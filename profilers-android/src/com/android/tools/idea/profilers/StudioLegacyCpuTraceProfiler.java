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

import com.android.annotations.Nullable;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuTraceMode;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateRequest;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
  /**
   * Map from process id to the record of the profiling.
   * Existence in the map means there is an active ongoing profiling for that given app.
   */
  @NotNull private final Map<Integer, LegacyProfilingRecord> myLegacyProfilingRecord = new HashMap<>();

  public StudioLegacyCpuTraceProfiler(@NotNull IDevice device, @NotNull CpuServiceGrpc.CpuServiceBlockingStub cpuStub) {
    myDevice = device;
    myServiceStub = cpuStub;
    // Sets a global handler which contains callbacks related to method-level profiling using DDMS.
    LegacyProfilingHandler profilingHandler = LegacyProfilingHandler.getInstance();
    ClientData.setMethodProfilingHandler(profilingHandler);
    // Add profiling records for this device to the profiling handler
    profilingHandler.addProfilingRecords(myDevice, myLegacyProfilingRecord);
  }

  @Override
  public CpuProfilingAppStartResponse startProfilingApp(CpuProfilingAppStartRequest request) {
    int pid = request.getSession().getPid();
    CpuProfilingAppStartResponse.Builder responseBuilder = CpuProfilingAppStartResponse.newBuilder();
    String appPkgName = myDevice.getClientName(pid);
    Client client = appPkgName != null ? myDevice.getClient(appPkgName) : null;
    if (client == null) {
      return responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE)
        .setErrorMessage("App is not running.").build();
    }

    synchronized (myLegacyProfilingLock) {
      LegacyProfilingRecord record = myLegacyProfilingRecord.get(pid);
      if (record != null && client.getClientData().getMethodProfilingStatus() != ClientData.MethodProfilingStatus.OFF) {
        return responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE)
          .setErrorMessage("Start request ignored. The app has an on-going profiling session.").build();
      }

      Cpu.CpuTraceConfiguration.UserOptions userOptions = request.getConfiguration().getUserOptions();
      // com.android.ddmlib.HandleProfiling.sendSPSS(..) has buffer size as a parameter, but we cannot call it
      // because the class is not public. To set buffer size, we modify DdmPreferences which will be read by
      // client.startSamplingProfiler(..) and client.startMethodTracer().
      DdmPreferences.setProfilerBufferSizeMb(userOptions.getBufferSizeInMb());
      long nowNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
      record = new LegacyProfilingRecord(request, nowNs, responseBuilder);
      myLegacyProfilingRecord.put(pid, record);
      try {
        if (userOptions.getTraceType() == CpuTraceType.ATRACE) {
          responseBuilder.mergeFrom(myServiceStub.startProfilingApp(request));
        }
        else if (userOptions.getTraceMode() == CpuTraceMode.SAMPLED) {
          assert userOptions.getTraceType() == CpuTraceType.ART;
          client.startSamplingProfiler(userOptions.getSamplingIntervalUs(), TimeUnit.MICROSECONDS);
        }
        else {
          assert userOptions.getTraceType() == CpuTraceType.ART;
          client.startMethodTracer();
        }
        // startSamplingProfiler() and startMethodTracer() calls above always return immediately.
        // In case there is an error, ClientData.IMethodProfilingHandler.onEndFailure(..) will be called and the
        // responseBuilder has been populated there. Because IMethodProfilingHandler has no callback at success,
        // we limit the waiting to 0.1 second which is usually sufficient for the error handling to complete.
        record.myStartLatch.await(100, TimeUnit.MILLISECONDS);
        if (record.myStartFailed) {
          myLegacyProfilingRecord.remove(pid);
        }
        else {
          responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
        }
      }
      catch (IOException | InterruptedException e) {
        responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE);
        responseBuilder.setErrorMessage("Failed: " + e);
        getLogger().error("Exception while CpuServiceProxy startProfilingAppDdms: " + e);
      }
    }
    return responseBuilder.build();
  }

  @Override
  public CpuProfilingAppStopResponse stopProfilingApp(CpuProfilingAppStopRequest request) {
    int pid = request.getSession().getPid();
    CpuProfilingAppStopResponse.Builder responseBuilder = CpuProfilingAppStopResponse.newBuilder();
    String appPkgName = myDevice.getClientName(pid);
    Client client = appPkgName != null ? myDevice.getClient(appPkgName) : null;

    synchronized (myLegacyProfilingLock) {
      if (client == null) {
        myLegacyProfilingRecord.remove(pid);   // Remove the entry if there exists one.
        return responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.APP_PROCESS_DIED)
          .setErrorMessage("App is not running.").build();
      }

      LegacyProfilingRecord record = myLegacyProfilingRecord.get(pid);
      if (isMethodProfilingStatusOff(record, client)) {
        return responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.NO_ONGOING_PROFILING)
          .setErrorMessage("The app is not being profiled.").build();
      }

      record.setStopResponseBuilder(responseBuilder);
      try {
        Cpu.CpuTraceConfiguration.UserOptions userOptions = record.myStartRequest.getConfiguration().getUserOptions();
        if (userOptions.getTraceType() == CpuTraceType.ATRACE) {
          CpuProfilingAppStopResponse response = myServiceStub.stopProfilingApp(request);
          responseBuilder.mergeFrom(response);
          record.myStopLatch.countDown();
        }
        else if (userOptions.getTraceMode() == CpuTraceMode.SAMPLED) {
          assert userOptions.getTraceType() == CpuTraceType.ART;
          client.stopSamplingProfiler();
        }
        else {
          assert userOptions.getTraceType() == CpuTraceType.ART;
          client.stopMethodTracer();
        }
        record.myStopLatch.await();
      }
      catch (IOException | InterruptedException e) {
        responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.STOP_COMMAND_FAILED);
        responseBuilder.setErrorMessage("Failed: " + e);
        getLogger().error("Exception while CpuServiceProxy stopProfilingApp: " + e);
      }
      myLegacyProfilingRecord.remove(pid);
    }
    return responseBuilder.build();
  }

  @Override
  public ProfilingStateResponse checkAppProfilingState(ProfilingStateRequest request) {
    int pid = request.getSession().getPid();
    long nowNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    ProfilingStateResponse.Builder responseBuilder = ProfilingStateResponse.newBuilder().setCheckTimestamp(nowNs);
    String appPkgName = myDevice.getClientName(pid);
    Client client = appPkgName != null ? myDevice.getClient(appPkgName) : null;
    if (client == null) {
      return responseBuilder.setBeingProfiled(false).build();
    }

    synchronized (myLegacyProfilingLock) {
      LegacyProfilingRecord record = myLegacyProfilingRecord.get(pid);
      if (isMethodProfilingStatusOff(record, client)) {
        return responseBuilder.setBeingProfiled(false).build();
      }
      else {
        return responseBuilder
          .setBeingProfiled(true)
          .setConfiguration(record.myStartRequest.getConfiguration())
          .setStartTimestamp(record.myStartRequestTimestamp)
          .build();
      }
    }
  }

  /**
   * This method returns true if the method profiling status is off for art traces only. For all other trace types (mainly systrace) we
   * return false because method profiling is not an available feature.
   */
  private boolean isMethodProfilingStatusOff(LegacyProfilingRecord record, Client client) {
    return record == null || (client.getClientData().getMethodProfilingStatus() == ClientData.MethodProfilingStatus.OFF &&
                              record.myStartRequest.getConfiguration().getUserOptions().getTraceType() == CpuTraceType.ART);
  }

  private static class LegacyProfilingHandler implements ClientData.IMethodProfilingHandler {
    @NotNull private final Map<IDevice, Map<Integer, LegacyProfilingRecord>> myProfilingRecords;

    private static LegacyProfilingHandler ourInstance;

    private LegacyProfilingHandler() {
      myProfilingRecords = new HashMap<>();
    }

    public static synchronized LegacyProfilingHandler getInstance() {
      if (ourInstance == null) {
        ourInstance = new LegacyProfilingHandler();
      }
      return ourInstance;
    }

    @Override
    public void onSuccess(String remoteFilePath, Client client) {
      LegacyProfilingRecord record = myProfilingRecords.get(client.getDevice()).get(client.getClientData().getPid());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        assert stopResponseBuilder != null;
        // Devices older than API 10 don't return profile results via JDWP. Instead they save the results on the
        // sdcard. We don't support this.
        stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.CANNOT_COPY_FILE);
        stopResponseBuilder.setErrorMessage(
          "Method profiling: Older devices (API level < 10) are not supported. Please use DDMS.");
        record.myStopLatch.countDown();
      }
    }

    @Override
    public void onSuccess(byte[] data, Client client) {
      LegacyProfilingRecord record = myProfilingRecords.get(client.getDevice()).get(client.getClientData().getPid());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        assert stopResponseBuilder != null;
        stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
        stopResponseBuilder.setTrace(ByteString.copyFrom(data));
        stopResponseBuilder.setTraceId(System.nanoTime());
        record.myStopLatch.countDown();
      }
    }

    /**
     * The interface says this callback is "called when method tracing failed to start". It may be true
     * for API < 10 (not having FEATURE_PROFILING_STREAMING), but there appears no executing path trigger it
     * for API >= 10.
     *
     * @param client  the client that was profiled.
     * @param message an optional (<code>null</code> ok) error message to be displayed.
     */
    @Override
    public void onStartFailure(Client client, String message) {
    }

    /**
     * The interface says "Called when method tracing failed to end on the VM side", but in reality
     * it is called when either start or end fails. Therefore, we rely on whether a field
     * (record.getStopResponseBuilder()) is set to distinguish it is a start or stop failure,
     *
     * @param client  the client that was profiled.
     * @param message an optional (<code>null</code> ok) error message to be displayed.
     */
    @Override
    public void onEndFailure(Client client, String message) {
      LegacyProfilingRecord record = myProfilingRecords.get(client.getDevice()).get(client.getClientData().getPid());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        if (stopResponseBuilder != null) {
          stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.STOP_COMMAND_FAILED);
          stopResponseBuilder.setErrorMessage("Failed to stop profiling: " + message);
          record.myStopLatch.countDown();
        }
        else {
          record.myStartFailed = true;
          record.myStartResponseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE);
          record.myStartResponseBuilder.setErrorMessage("Failed to start profiling: " + message);
          record.myStartLatch.countDown();
        }
      }
    }

    public void addProfilingRecords(@NotNull IDevice device, @NotNull Map<Integer, LegacyProfilingRecord> record) {
      myProfilingRecords.put(device, record);
    }
  }

  /**
   * Metadata of an ongoing profiling session of an app.
   */
  private static class LegacyProfilingRecord {
    @NotNull final CpuProfilingAppStartRequest myStartRequest;
    long myStartRequestTimestamp;
    @NotNull CpuProfilingAppStartResponse.Builder myStartResponseBuilder;
    /**
     * The latch that the profiler waits on when sending a start profiling request.
     * If the start fails, LegacyProfilingHandler.onEndFailure(..) would be triggered which
     * counts down the latch. There is no known way to count down if the start succeeds.
     */
    @NotNull CountDownLatch myStartLatch = new CountDownLatch(1);
    /**
     * The latch that the profiler waits on when sending a stop profiling request.
     * If the stop succeeds, LegacyProfilingHandler.onSuccess(..) would be triggered which
     * counts down the latch. If the stop fails, LegacyProfilingHandler.onEndFailure(..)
     * would be triggered which counts down the latch.
     */
    @NotNull CountDownLatch myStopLatch = new CountDownLatch(1);

    boolean myStartFailed = false;
    /**
     * The builder of stop response, Set only after the stop profiling API is called.
     */
    @Nullable CpuProfilingAppStopResponse.Builder myStopResponseBuilder;

    public LegacyProfilingRecord(@NotNull CpuProfilingAppStartRequest request,
                                 long timestamp,
                                 @NotNull CpuProfilingAppStartResponse.Builder startResponseBuilder) {
      myStartRequest = request;
      myStartRequestTimestamp = timestamp;
      myStartResponseBuilder = startResponseBuilder;
    }

    public void setStopResponseBuilder(@NotNull CpuProfilingAppStopResponse.Builder builder) {
      myStopResponseBuilder = builder;
    }

    @Nullable
    public CpuProfilingAppStopResponse.Builder getStopResponseBuilder() {
      return myStopResponseBuilder;
    }
  }
}
