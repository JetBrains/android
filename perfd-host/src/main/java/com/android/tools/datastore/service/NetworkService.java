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

  private NetworkTable myNetworkTable = new NetworkTable();
  private Consumer<Runnable> myFetchExecutor;
  private Map<Integer, PollRunner> myRunners = new HashMap<>();
  private DataStoreService myService;

  public NetworkService(DataStoreService service, Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = service;
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
    NetworkServiceGrpc.NetworkServiceBlockingStub client = myService.getNetworkClient(request.getSession());
    if (client != null) {
      responseObserver.onNext(client.startMonitoringApp(request));
      responseObserver.onCompleted();
      int processId = request.getProcessId();
      myRunners.put(processId, new NetworkDataPoller(processId, request.getSession(), myNetworkTable, client));
      myFetchExecutor.accept(myRunners.get(processId));
    } else {
      responseObserver.onNext(NetworkProfiler.NetworkStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(NetworkProfiler.NetworkStopRequest request,
                                StreamObserver<NetworkProfiler.NetworkStopResponse> responseObserver) {
    int processId = request.getProcessId();
    PollRunner runner = myRunners.remove(processId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    NetworkServiceGrpc.NetworkServiceBlockingStub service = myService.getNetworkClient(request.getSession());
    if (service == null) {
      responseObserver.onNext(NetworkProfiler.NetworkStopResponse.getDefaultInstance());
    } else {
      responseObserver.onNext(service.stopMonitoringApp(request));
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
    NetworkProfiler.HttpDetailsResponse storedResponse = myNetworkTable.getHttpDetailsResponseById(request.getConnId(), request.getSession(), request.getType());
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