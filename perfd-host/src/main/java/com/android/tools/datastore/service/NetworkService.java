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

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.NetworkTable;
import com.android.tools.datastore.poller.NetworkDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// TODO: Implement a storage container that can read/write data to disk
public class NetworkService extends NetworkServiceGrpc.NetworkServiceImplBase implements ServicePassThrough {
  private NetworkServiceGrpc.NetworkServiceBlockingStub myPollingService;
  private NetworkTable myNetworkTable = new NetworkTable();
  Consumer<Runnable> myFetchExecutor;
  private Map<Integer, PollRunner> myRunners = new HashMap<>();
  public NetworkService(Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = NetworkServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public void getData(NetworkProfiler.NetworkDataRequest request, StreamObserver<NetworkProfiler.NetworkDataResponse> responseObserver) {
    NetworkProfiler.NetworkDataResponse.Builder response = NetworkProfiler.NetworkDataResponse.newBuilder();
    List<NetworkProfiler.NetworkProfilerData> datas = myNetworkTable.getNetworkDataByRequest(request);
    response.addAllData(datas);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(NetworkProfiler.NetworkStartRequest request,
                                 StreamObserver<NetworkProfiler.NetworkStartResponse> responseObserver) {
    responseObserver.onNext(myPollingService.startMonitoringApp(request));
    responseObserver.onCompleted();
    int processId = request.getProcessId();
    myRunners.put(processId, new NetworkDataPoller(processId, myNetworkTable, myPollingService));
    myFetchExecutor.accept(myRunners.get(processId));
  }

  @Override
  public void stopMonitoringApp(NetworkProfiler.NetworkStopRequest request,
                                StreamObserver<NetworkProfiler.NetworkStopResponse> responseObserver) {
    int processId = request.getProcessId();
    myRunners.remove(processId).stop();
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    if (!((ManagedChannel)myPollingService.getChannel()).isShutdown()) {
      responseObserver.onNext(myPollingService.stopMonitoringApp(request));
    } else {
      responseObserver.onNext(NetworkProfiler.NetworkStopResponse.getDefaultInstance());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpRange(NetworkProfiler.HttpRangeRequest request, StreamObserver<NetworkProfiler.HttpRangeResponse> responseObserver) {
    NetworkProfiler.HttpRangeResponse.Builder response = NetworkProfiler.HttpRangeResponse.newBuilder();
    List<NetworkProfiler.HttpConnectionData> datas = myNetworkTable.getNetworkConnectionDataByRequest(request);
    response.addAllData(datas);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpDetails(NetworkProfiler.HttpDetailsRequest request,
                             StreamObserver<NetworkProfiler.HttpDetailsResponse> responseObserver) {
    NetworkProfiler.HttpDetailsResponse storedResponse = myNetworkTable.getHttpDetailsResponseById(request.getConnId(), request.getType());
    NetworkProfiler.HttpDetailsResponse.Builder response = NetworkProfiler.HttpDetailsResponse.newBuilder();
    switch (request.getType()) {
      case REQUEST:
        response.setRequest(storedResponse.getRequest());
        break;
      case RESPONSE:
        response.setResponse(storedResponse.getResponse());
        break;
      case RESPONSE_BODY:
        response.setResponseBody(storedResponse.getResponseBody());
        break;
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myNetworkTable;
  }
}