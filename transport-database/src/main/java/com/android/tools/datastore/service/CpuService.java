/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.CpuTable;
import com.android.tools.datastore.poller.CpuDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler.CpuCoreConfigRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuCoreConfigResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.EmptyCpuReply;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceResponse;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateRequest;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateResponse;
import com.android.tools.profiler.proto.CpuProfiler.SaveTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.StartupProfilingRequest;
import com.android.tools.profiler.proto.CpuProfiler.StartupProfilingResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuService extends CpuServiceGrpc.CpuServiceImplBase implements ServicePassThrough {
  private final Map<Long, PollRunner> myRunners = new HashMap<>();
  private final Consumer<Runnable> myFetchExecutor;

  @NotNull
  private final CpuTable myCpuTable;
  @NotNull
  private final DataStoreService myService;
  @NotNull
  private final LogService myLogService;

  @SuppressWarnings("unchecked")
  private ResponseData<CpuDataResponse> myLastCpuResponse = ResponseData.createEmpty();
  @SuppressWarnings("unchecked")
  private ResponseData<GetThreadsResponse> myLastThreadsResponse = ResponseData.createEmpty();
  @SuppressWarnings("unchecked")
  private ResponseData<GetTraceInfoResponse> myLastTraceInfoResponse = ResponseData.createEmpty();

  public CpuService(@NotNull DataStoreService dataStoreService,
                    Consumer<Runnable> fetchExecutor,
                    LogService logService) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
    myLogService = logService;
    myCpuTable = new CpuTable();
  }

  @Override
  public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> observer) {
    if (!myLastCpuResponse.matches(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      CpuDataResponse.Builder response = CpuDataResponse.newBuilder();
      List<Cpu.CpuUsageData> cpuData = myCpuTable.getCpuDataByRequest(request);
      for (Cpu.CpuUsageData data : cpuData) {
        response.addData(data);
      }
      myLastCpuResponse = new ResponseData<>(request.getSession(),
                                             request.getStartTimestamp(),
                                             request.getEndTimestamp(),
                                             response.build());
    }
    observer.onNext(myLastCpuResponse.getResponse());
    observer.onCompleted();
  }

  @Override
  public void getThreads(GetThreadsRequest request, StreamObserver<GetThreadsResponse> observer) {
    if (!myLastThreadsResponse.matches(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      GetThreadsResponse.Builder response = GetThreadsResponse.newBuilder();
      // TODO: make it consistent with perfd and return the activities and the snapshot separately
      response.addAllThreads(myCpuTable.getThreadsDataByRequest(request));
      myLastThreadsResponse = new ResponseData<>(request.getSession(),
                                                 request.getStartTimestamp(),
                                                 request.getEndTimestamp(),
                                                 response.build());
    }

    observer.onNext(myLastThreadsResponse.getResponse());
    observer.onCompleted();
  }

  @Override
  public void getTraceInfo(GetTraceInfoRequest request, StreamObserver<GetTraceInfoResponse> responseObserver) {
    if (!myLastTraceInfoResponse.matches(request.getSession(), request.getFromTimestamp(), request.getToTimestamp())) {
      GetTraceInfoResponse.Builder response = GetTraceInfoResponse.newBuilder();
      List<Cpu.CpuTraceInfo> responses = myCpuTable.getTraceInfo(request);
      response.addAllTraceInfo(responses);
      myLastTraceInfoResponse = new ResponseData<>(request.getSession(),
                                                   request.getFromTimestamp(),
                                                   request.getToTimestamp(),
                                                   response.build());
    }

    responseObserver.onNext(myLastTraceInfoResponse.getResponse());
    responseObserver.onCompleted();
  }

  @Override
  public void saveTraceInfo(SaveTraceInfoRequest request, StreamObserver<EmptyCpuReply> responseObserver) {
    myCpuTable.insertTraceInfo(request.getSession(), request.getTraceInfo());
    if (!request.getPreprocessedTrace().isEmpty()) {
      myCpuTable.insertTrace(request.getSession(), request.getTraceInfo().getTraceId(), request.getTraceInfo().getTraceType(),
                             request.getTraceInfo().getTraceMode(), request.getPreprocessedTrace());
    }
    responseObserver.onNext(EmptyCpuReply.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> observer) {
    // Start monitoring request needs to happen before we begin the poller to inform the device that we are going to be requesting
    // data for a specific process id.
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getSession().getStreamId());
    if (client != null) {
      observer.onNext(client.startMonitoringApp(request));
      observer.onCompleted();
      long sessionId = request.getSession().getSessionId();
      myRunners
        .put(sessionId, new CpuDataPoller(request.getSession(), myCpuTable, client, myLogService));
      myFetchExecutor.accept(myRunners.get(sessionId));
    }
    else {
      observer.onNext(CpuStartResponse.getDefaultInstance());
      observer.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> observer) {
    long sessionId = request.getSession().getSessionId();
    PollRunner runner = myRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    CpuServiceGrpc.CpuServiceBlockingStub service = myService.getCpuClient(request.getSession().getStreamId());
    if (service == null) {
      observer.onNext(CpuStopResponse.getDefaultInstance());
    }
    else {
      observer.onNext(service.stopMonitoringApp(request));
    }
    observer.onCompleted();
  }

  @Override
  public void startProfilingApp(CpuProfilingAppStartRequest request,
                                StreamObserver<CpuProfilingAppStartResponse> observer) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getSession().getStreamId());
    if (client != null) {
      observer.onNext(client.startProfilingApp(request));
    }
    else {
      observer.onNext(CpuProfilingAppStartResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfilingAppStopRequest request,
                               StreamObserver<CpuProfilingAppStopResponse> observer) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getSession().getStreamId());
    CpuProfilingAppStopResponse response = CpuProfilingAppStopResponse.getDefaultInstance();
    if (client != null) {
      response = client.stopProfilingApp(request);
      // Only add successfully captured traces to the database
      if (response.getStatus() == CpuProfilingAppStopResponse.Status.SUCCESS) {
        myCpuTable.insertTrace(
          request.getSession(), response.getTraceId(), request.getTraceType(), request.getTraceMode(), response.getTrace());
      }
    }
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void checkAppProfilingState(ProfilingStateRequest request,
                                     StreamObserver<ProfilingStateResponse> observer) {
    ProfilingStateResponse response = myCpuTable.getProfilingStateData(request.getSession());
    if (response != null) {
      observer.onNext(response);
    }
    else {
      // When Profiler opens CpuProfilerStage directly (e.g when startup profiling was started),
      // we're expecting to hit this, because we haven't inserted any data in the DB yet.
      CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getSession().getStreamId());
      if (client != null) {
        observer.onNext(client.checkAppProfilingState(request));
      }
      else {
        observer.onNext(ProfilingStateResponse.getDefaultInstance());
      }
    }
    observer.onCompleted();
  }

  @Override
  public void startStartupProfiling(StartupProfilingRequest request,
                                    StreamObserver<StartupProfilingResponse> observer) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getDeviceId());
    if (client != null) {
      observer.onNext(client.startStartupProfiling(request));
    }
    else {
      observer.onNext(StartupProfilingResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void getTrace(GetTraceRequest request, StreamObserver<GetTraceResponse> observer) {
    CpuTable.TraceData data = myCpuTable.getTraceData(request.getSession(), request.getTraceId());
    GetTraceResponse.Builder builder = GetTraceResponse.newBuilder();
    if (data == null) {
      builder.setStatus(GetTraceResponse.Status.FAILURE);
    }
    else {
      builder.setStatus(GetTraceResponse.Status.SUCCESS)
        .setData(data.getTraceBytes())
        .setTraceType(data.getTraceType())
        .setTraceMode(data.getTraceMode());
    }

    observer.onNext(builder.build());
    observer.onCompleted();
  }

  @Override
  public void getCpuCoreConfig(CpuCoreConfigRequest request, StreamObserver<CpuCoreConfigResponse> observer) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(request.getDeviceId());
    if (client != null) {
      observer.onNext(client.getCpuCoreConfig(request));
    }
    observer.onCompleted();
  }

  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
    myCpuTable.initialize(connection);
  }
}
