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
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.CpuTable;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.poller.CpuDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.*;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuService extends CpuServiceGrpc.CpuServiceImplBase implements ServicePassThrough {

  private Map<Integer, PollRunner> myRunners = new HashMap<>();
  private Consumer<Runnable> myFetchExecutor;
  private CpuTable myCpuTable = new CpuTable();
  private long myStartTraceTimestamp = -1;
  private DataStoreService myService;

  public CpuService(@NotNull DataStoreService dataStoreService, Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
  }

  @Override
  public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> observer) {
    CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
    List<CpuProfiler.CpuProfilerData> cpuData = myCpuTable.getCpuDataByRequest(request);
    for (CpuProfiler.CpuProfilerData data : cpuData) {
      response.addData(data);
    }
    observer.onNext(response.build());
    observer.onCompleted();
  }

  @Override
  public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> observer) {
    CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
    // TODO: make it consistent with perfd and return the activities and the snapshot separately
    response.addAllThreads(myCpuTable.getThreadsDataByRequest(request));
    observer.onNext(response.build());
    observer.onCompleted();
  }

  @Override
  public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
    CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
    List<CpuProfiler.TraceInfo> responses = myCpuTable.getTraceByRequest(request);
    response.addAllTraceInfo(responses);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> observer) {
    // Start monitoring request needs to happen before we begin the poller to inform the device that we are going to be requesting
    // data for a specific process id.
    observer.onNext(myService.getCpuClient(request.getSession()).startMonitoringApp(request));
    observer.onCompleted();
    int processId = request.getProcessId();
    myRunners.put(processId, new CpuDataPoller(processId, myCpuTable, myService.getCpuClient(request.getSession())));
    myFetchExecutor.accept(myRunners.get(processId));
  }

  @Override
  public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> observer) {
    int processId = request.getProcessId();
    myRunners.remove(processId).stop();
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    CpuServiceGrpc.CpuServiceBlockingStub service = myService.getCpuClient(request.getSession());
    if (service == null) {
      observer.onNext(CpuProfiler.CpuStopResponse.getDefaultInstance());
    } else {
      observer.onNext(service.stopMonitoringApp(request));
    }
    observer.onCompleted();
  }

  @Override
  public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request,
                                StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> observer) {
    // TODO: start time shouldn't be keep in a variable here, but passed through request/response instead.
    myStartTraceTimestamp = getCurrentDeviceTimeNs(request.getSession());
    observer.onNext(myService.getCpuClient(request.getSession()).startProfilingApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request,
                               StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> observer) {
    CpuProfiler.CpuProfilingAppStopResponse response = myService.getCpuClient(request.getSession()).stopProfilingApp(request);
    CpuProfiler.TraceInfo trace = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(response.getTraceId())
      .setFromTimestamp(myStartTraceTimestamp)
      .setToTimestamp(getCurrentDeviceTimeNs(request.getSession()))
      .build();
    myCpuTable.insertTrace(trace, response.getTrace());
    myStartTraceTimestamp = -1;
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getTrace(CpuProfiler.GetTraceRequest request, StreamObserver<CpuProfiler.GetTraceResponse> observer) {

    ByteString data = myCpuTable.getTraceData(request.getTraceId());
    CpuProfiler.GetTraceResponse.Builder builder = CpuProfiler.GetTraceResponse.newBuilder();
    if (data == null) {
      builder.setStatus(CpuProfiler.GetTraceResponse.Status.FAILURE);
    }
    else {
      builder.setStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS).setData(data);
    }

    observer.onNext(builder.build());
    observer.onCompleted();
  }

  private long getCurrentDeviceTimeNs(Common.Session session) {
    return myService.getProfilerClient(session).getCurrentTime(Profiler.TimeRequest.newBuilder().setSession(session).build()).getTimestampNs();
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myCpuTable;
  }
}
