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
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.CpuTable;
import com.android.tools.datastore.poller.CpuDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuService extends CpuServiceGrpc.CpuServiceImplBase implements ServicePassThrough {
  private final Map<Integer, PollRunner> myRunners = new HashMap<>();
  private final Consumer<Runnable> myFetchExecutor;

  @NotNull
  private final CpuTable myCpuTable;
  @NotNull
  private final DataStoreService myService;

  @SuppressWarnings("unchecked")
  private ResponseData<CpuDataResponse> myLastCpuResponse = ResponseData.createEmpty();
  @SuppressWarnings("unchecked")
  private ResponseData<GetThreadsResponse> myLastThreadsResponse = ResponseData.createEmpty();
  @SuppressWarnings("unchecked")
  private ResponseData<GetTraceInfoResponse> myLastTraceInfoResponse = ResponseData.createEmpty();

  public CpuService(@NotNull DataStoreService dataStoreService,
                    Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
    myCpuTable = new CpuTable();
  }

  @Override
  public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> observer) {
    if (!myLastCpuResponse.matches(request.getProcessId(), request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      CpuDataResponse.Builder response = CpuDataResponse.newBuilder();
      List<CpuProfilerData> cpuData = myCpuTable.getCpuDataByRequest(request);
      for (CpuProfilerData data : cpuData) {
        response.addData(data);
      }
      myLastCpuResponse = new ResponseData<>(request.getProcessId(),
                                             request.getSession(),
                                             request.getStartTimestamp(),
                                             request.getEndTimestamp(),
                                             response.build());
    }
    observer.onNext(myLastCpuResponse.getResponse());
    observer.onCompleted();
  }

  @Override
  public void getThreads(GetThreadsRequest request, StreamObserver<GetThreadsResponse> observer) {
    if (!myLastThreadsResponse.matches(
      request.getProcessId(), request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      GetThreadsResponse.Builder response = GetThreadsResponse.newBuilder();
      // TODO: make it consistent with perfd and return the activities and the snapshot separately
      response.addAllThreads(myCpuTable.getThreadsDataByRequest(request));
      myLastThreadsResponse = new ResponseData<>(request.getProcessId(),
                                                 request.getSession(),
                                                 request.getStartTimestamp(),
                                                 request.getEndTimestamp(),
                                                 response.build());
    }

    observer.onNext(myLastThreadsResponse.getResponse());
    observer.onCompleted();
  }

  @Override
  public void getTraceInfo(GetTraceInfoRequest request, StreamObserver<GetTraceInfoResponse> responseObserver) {
    if (!myLastTraceInfoResponse.matches(
      request.getProcessId(), request.getSession(), request.getFromTimestamp(), request.getToTimestamp())) {
      GetTraceInfoResponse.Builder response = GetTraceInfoResponse.newBuilder();
      List<TraceInfo> responses = myCpuTable.getTraceInfo(request);
      response.addAllTraceInfo(responses);
      myLastTraceInfoResponse = new ResponseData<>(request.getProcessId(),
                                                   request.getSession(),
                                                   request.getFromTimestamp(),
                                                   request.getToTimestamp(),
                                                   response.build());
    }

    responseObserver.onNext(myLastTraceInfoResponse.getResponse());
    responseObserver.onCompleted();
  }

  @Override
  public void saveTraceInfo(SaveTraceInfoRequest request, StreamObserver<EmptyCpuReply> responseObserver) {
    myCpuTable.insertTraceInfo(request.getProcessId(), request.getTraceInfo(), request.getSession());
    responseObserver.onNext(EmptyCpuReply.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> observer) {
    // Start monitoring request needs to happen before we begin the poller to inform the device that we are going to be requesting
    // data for a specific process id.
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.startMonitoringApp(request));
      observer.onCompleted();
      int processId = request.getProcessId();
      myRunners
        .put(processId,
             new CpuDataPoller(processId, request.getSession(), myCpuTable,
                               myService.getCpuClient(DeviceId.fromSession(request.getSession()))));
      myFetchExecutor.accept(myRunners.get(processId));
    }
    else {
      observer.onNext(CpuStartResponse.getDefaultInstance());
      observer.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> observer) {
    int processId = request.getProcessId();
    PollRunner runner = myRunners.remove(processId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    CpuServiceGrpc.CpuServiceBlockingStub service = myService.getCpuClient(DeviceId.fromSession(request.getSession()));
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
    // TODO: start time shouldn't be keep in a variable here, but passed through request/response instead.
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(DeviceId.fromSession(request.getSession()));
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
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(DeviceId.fromSession(request.getSession()));
    CpuProfilingAppStopResponse response = CpuProfilingAppStopResponse.getDefaultInstance();
    if (client != null) {
      response = client.stopProfilingApp(request);
      myCpuTable.insertTrace(
        request.getProcessId(), response.getTraceId(), request.getSession(), request.getProfilerType(), response.getTrace());
    }
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void checkAppProfilingState(ProfilingStateRequest request,
                                     StreamObserver<ProfilingStateResponse> observer) {
    CpuServiceGrpc.CpuServiceBlockingStub client = myService.getCpuClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.checkAppProfilingState(request));
    }
    else {
      observer.onNext(ProfilingStateResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void getTrace(GetTraceRequest request, StreamObserver<GetTraceResponse> observer) {
    CpuTable.TraceData data = myCpuTable.getTraceData(request.getProcessId(), request.getTraceId(), request.getSession());
    GetTraceResponse.Builder builder = GetTraceResponse.newBuilder();
    if (data == null) {
      builder.setStatus(GetTraceResponse.Status.FAILURE);
    }
    else {
      builder.setStatus(GetTraceResponse.Status.SUCCESS).setData(data.getTraceBytes()).setProfilerType(data.getProfilerType());
    }

    observer.onNext(builder.build());
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

  /**
   * Stores a response of a determined type to avoid making unnecessary queries to the database.
   *
   * Often, there is no need for querying the database to get the response corresponding to the request made.
   * For example, in the threads monitor each thread has a ThreadStateDataSeries that will call
   * {@link #getThreads(GetThreadsRequest, StreamObserver)} passing a request with the same arguments (start/end timestamp,
   * pid, and session). In these cases, we can query the database once and return a cached result for the subsequent calls.
   *
   * @param <T> type of the response stored
   */
  private static class ResponseData<T> {
    private int myProcessId;
    private Common.Session mySession;
    private long myStart;
    private long myEnd;
    private T myResponse;

    private ResponseData(int processId, Common.Session session, long startTimestamp, long endTimestamp, T response) {
      myProcessId = processId;
      mySession = session;
      myStart = startTimestamp;
      myEnd = endTimestamp;
      myResponse = response;
    }

    public boolean matches(int processId, Common.Session session, long startTimestamp, long endTimestamp) {
      boolean isSessionEquals = (mySession == null && session == null) || (mySession != null && mySession.equals(session));
      return isSessionEquals && myProcessId == processId && myStart == startTimestamp && myEnd == endTimestamp;
    }

    public T getResponse() {
      return myResponse;
    }

    @SuppressWarnings("unchecked")
    public static ResponseData createEmpty() {
      return new ResponseData(0, null, 0, 0, null);
    }
  }
}
