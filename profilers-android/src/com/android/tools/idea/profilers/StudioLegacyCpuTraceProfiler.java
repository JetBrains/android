/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.profilers;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuTraceMode;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.idea.protobuf.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Since the 'am' (activity manager) command line in older releases of Android has limited functionalities for
 * CPU method-level tracing, we therefore need to employ JDWP (DDMS).
 * <p>
 * In this class, 'profiling' means method-level tracing using either instrumentation or profiling.
 * Within this class 'profiling' is not a general concept as in 'CPU profiler'.
 */
public class StudioLegacyCpuTraceProfiler implements LegacyCpuTraceProfiler {

  private static Logger getLogger() {
    return Logger.getInstance(StudioLegacyCpuTraceProfiler.class);
  }

  @NotNull private IDevice myDevice;
  @NotNull private final Object myLegacyProfilingLock = new Object();
  // Using a stub instead of the chanel to allow for easier testing.
  @NotNull private CpuServiceGrpc.CpuServiceBlockingStub myServiceStub;
  @NotNull private TransportServiceGrpc.TransportServiceBlockingStub myTransportServiceStub;
  /**
   * Map from process id to the record of the profiling.
   * Existence in the map means there is an active ongoing profiling for that given app.
   */
  @GuardedBy("myLegacyProfilingLock")
  @NotNull private final Map<Integer, LegacyCpuTraceRecord> myLegacyProfilingRecord = new HashMap<>();

  @NotNull private final Map<Integer, List<Cpu.CpuTraceInfo.Builder>> myTraceInfoMap = new HashMap<>();

  public StudioLegacyCpuTraceProfiler(@NotNull IDevice device,
                                      @NotNull CpuServiceGrpc.CpuServiceBlockingStub cpuStub,
                                      @NotNull TransportServiceGrpc.TransportServiceBlockingStub transportStub,
                                      @NotNull Map<String, ByteString> proxyBytesCache) {
    myDevice = device;
    myServiceStub = cpuStub;
    myTransportServiceStub = transportStub;
    LegacyCpuProfilingHandler profilingHandler = new LegacyCpuProfilingHandler(myLegacyProfilingRecord, proxyBytesCache);
    ClientData.setMethodProfilingHandler(profilingHandler);
  }

  @Override
  public CpuProfilingAppStartResponse startProfilingApp(CpuProfilingAppStartRequest request) {
    Cpu.CpuTraceConfiguration.UserOptions userOptions = request.getConfiguration().getUserOptions();
    // Daemon will handle all things related to ATrace. We don't keep a record here.
    if (userOptions.getTraceType() == CpuTraceType.ATRACE) {
      return myServiceStub.startProfilingApp(request);
    }

    assert userOptions.getTraceType() == CpuTraceType.ART;

    int pid = request.getSession().getPid();
    CpuProfilingAppStartResponse.Builder responseBuilder = CpuProfilingAppStartResponse.newBuilder();
    String appPkgName = myDevice.getClientName(pid);
    Client client = appPkgName != null ? myDevice.getClient(appPkgName) : null;
    if (client == null) {
      Cpu.TraceStartStatus status = Cpu.TraceStartStatus.newBuilder()
        .setStatus(Cpu.TraceStartStatus.Status.FAILURE)
        .setErrorMessage("App is not running")
        .build();
      return responseBuilder.setStatus(status).build();
    }

    synchronized (myLegacyProfilingLock) {
      LegacyCpuTraceRecord record = myLegacyProfilingRecord.get(pid);
      if (record != null && client.getClientData().getMethodProfilingStatus() != ClientData.MethodProfilingStatus.OFF) {
        Cpu.TraceStartStatus status = Cpu.TraceStartStatus.newBuilder()
          .setStatus(Cpu.TraceStartStatus.Status.FAILURE)
          .setErrorMessage("Start request ignored. The app has an on-going profiling session.")
          .build();
        return responseBuilder.setStatus(status).build();
      }

      // com.android.ddmlib.HandleProfiling.sendSPSS(..) has buffer size as a parameter, but we cannot call it
      // because the class is not public. To set buffer size, we modify DdmPreferences which will be read by
      // client.startSamplingProfiler(..) and client.startMethodTracer().
      DdmPreferences.setProfilerBufferSizeMb(userOptions.getBufferSizeInMb());
      long requestTimeNs = myTransportServiceStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance()).getTimestampNs();
      record = new LegacyCpuTraceRecord();
      myLegacyProfilingRecord.put(pid, record);
      try {
        if (userOptions.getTraceMode() == CpuTraceMode.SAMPLED) {
          client.startSamplingProfiler(userOptions.getSamplingIntervalUs(), TimeUnit.MICROSECONDS);
        }
        else {
          client.startMethodTracer();
        }

        // startSamplingProfiler() and startMethodTracer() calls above always return immediately.
        // In case there is an error, ClientData.IMethodProfilingHandler.onEndFailure(..) will be called and the
        // responseBuilder has been populated there. Because IMethodProfilingHandler has no callback at success,
        // we limit the waiting to 0.1 second which is usually sufficient for the error handling to complete.
        record.getStartLatch().await(100, TimeUnit.MILLISECONDS);
        // The start latch is used to determine whether onEndFailure is called for start or stop tracing,
        // so we countDown here to indicate that we are done processing the start callback.
        record.getStartLatch().countDown();
        if (record.isStartFailed()) {
          Cpu.TraceStartStatus status = Cpu.TraceStartStatus.newBuilder()
            .setStatus(Cpu.TraceStartStatus.Status.FAILURE)
            .setErrorMessage("Failed to start profiling: " + record.getStartFailureMessage())
            .build();
          responseBuilder.setStatus(status).build();
          myLegacyProfilingRecord.remove(pid);
        }
        else {
          Cpu.TraceStartStatus status = Cpu.TraceStartStatus.newBuilder()
            .setStatus(Cpu.TraceStartStatus.Status.SUCCESS)
            .build();
          responseBuilder.setStatus(status);

          // Create a corresponding CpuTraceInfo for the trace start event.
          Cpu.CpuTraceInfo.Builder infoBuilder = Cpu.CpuTraceInfo.newBuilder()
            .setTraceId(requestTimeNs)
            .setConfiguration(request.getConfiguration())
            .setFromTimestamp(requestTimeNs)
            .setStartStatus(status)
            .setToTimestamp(-1);
          record.setTraceInfo(infoBuilder);

          List<Cpu.CpuTraceInfo.Builder> builders = myTraceInfoMap.computeIfAbsent(pid, ArrayList::new);
          builders.add(infoBuilder);
        }
      }
      catch (IOException | InterruptedException e) {
        Cpu.TraceStartStatus status = Cpu.TraceStartStatus.newBuilder()
          .setStatus(Cpu.TraceStartStatus.Status.FAILURE)
          .setErrorMessage("Failed: " + e)
          .build();
        responseBuilder.setStatus(status);
        getLogger().error("Exception while CpuServiceProxy startProfilingAppDdms: " + e);
      }
    }
    return responseBuilder.build();
  }

  @Override
  public CpuProfilingAppStopResponse stopProfilingApp(CpuProfilingAppStopRequest request) {
    if (request.getTraceType() == CpuTraceType.ATRACE) {
      // Daemon will handle all things related to ATrace. We don't keep a record here.
      return myServiceStub.stopProfilingApp(request);
    }

    assert request.getTraceType() == CpuTraceType.ART;

    int pid = request.getSession().getPid();
    CpuProfilingAppStopResponse.Builder responseBuilder = CpuProfilingAppStopResponse.newBuilder();
    String appPkgName = myDevice.getClientName(pid);
    Client client = appPkgName != null ? myDevice.getClient(appPkgName) : null;
    synchronized (myLegacyProfilingLock) {
      if (client == null) {
        myLegacyProfilingRecord.remove(pid);   // Remove the entry if there exists one.
        Cpu.TraceStopStatus status = Cpu.TraceStopStatus.newBuilder()
          .setStatus(Cpu.TraceStopStatus.Status.APP_PROCESS_DIED)
          .setErrorMessage("App is not running.")
          .build();
        responseBuilder.setStatus(status).build();
      }
      else {
        LegacyCpuTraceRecord record = myLegacyProfilingRecord.get(pid);
        if (LegacyCpuTraceRecord.Companion.isMethodProfilingStatusOff(record, client)) {
          Cpu.TraceStopStatus status = Cpu.TraceStopStatus.newBuilder()
            .setStatus(Cpu.TraceStopStatus.Status.NO_ONGOING_PROFILING)
            .setErrorMessage("The app is not being profiled.")
            .build();
          responseBuilder.setStatus(status);
        }
        else {
          assert record.getTraceInfo() != null;
          Cpu.CpuTraceConfiguration.UserOptions userOptions = record.getTraceInfo().getConfiguration().getUserOptions();
          try {
            if (userOptions.getTraceMode() == CpuTraceMode.SAMPLED) {
              client.stopSamplingProfiler();
            }
            else {
              client.stopMethodTracer();
            }
            record.getStopLatch().await();
            responseBuilder.setStatus(record.getTraceInfo().getStopStatus());
            responseBuilder.setTraceId(record.getTraceInfo().getTraceId());
          }
          catch (IOException | InterruptedException e) {
            Cpu.TraceStopStatus status = Cpu.TraceStopStatus.newBuilder()
              .setStatus(Cpu.TraceStopStatus.Status.STOP_COMMAND_FAILED)
              .setErrorMessage("Failed: " + e)
              .build();
            responseBuilder.setStatus(status);
            getLogger().error("Exception while CpuServiceProxy stopProfilingApp: " + e);
          }
        }
        myLegacyProfilingRecord.remove(pid);
      }

      // Update the ongoing trace info sample if there is one.
      List<Cpu.CpuTraceInfo.Builder> builders = myTraceInfoMap.get(pid);
      if (builders != null && !builders.isEmpty()) {
        Cpu.CpuTraceInfo.Builder builder = builders.get(builders.size() - 1);
        if (builder.getToTimestamp() == -1) {
          Transport.TimeResponse timeResponse = myTransportServiceStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance());
          builder.setToTimestamp(timeResponse.getTimestampNs());
        }
      }
    }
    return responseBuilder.build();
  }

  @Override
  public List<Cpu.CpuTraceInfo> getTraceInfo(CpuProfiler.GetTraceInfoRequest request) {
    // Query the daemon for any ATrace data.
    List<Cpu.CpuTraceInfo> matchedInfoList = new ArrayList<>(myServiceStub.getTraceInfo(request).getTraceInfoList());
    synchronized (myLegacyProfilingLock) {
      if (myTraceInfoMap.containsKey(request.getSession().getPid())) {
        for (Cpu.CpuTraceInfo.Builder builder : myTraceInfoMap.get(request.getSession().getPid())) {
          if (builder.getFromTimestamp() <= request.getToTimestamp() &&
              (builder.getToTimestamp() > request.getFromTimestamp() || builder.getToTimestamp() == -1)) {
            matchedInfoList.add(builder.build());
          }
        }
      }
    }

    return matchedInfoList;
  }
}