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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.NetworkTable;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.RunnableFuture;

// TODO: Implement a storage container that can read/write data to disk
public class NetworkDataPoller extends NetworkServiceGrpc.NetworkServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {
  // Intentionally accessing this field out of sync block because it's OK for it to be o
  // off by a frame; we'll pick up all data eventually
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private long myHttpRangeRequestStartTimeNs = Long.MIN_VALUE;
  private NetworkServiceGrpc.NetworkServiceBlockingStub myPollingService;
  private int myProcessId = -1;
  private NetworkTable myNetworkTable = new NetworkTable();

  public NetworkDataPoller() {
  }

  @Override
  public RunnableFuture<Void> getRunner() {
    return new PollRunner(this, PollRunner.POLLING_DELAY_NS);
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
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
    myProcessId = request.getProcessId();
    responseObserver.onNext(myPollingService.startMonitoringApp(request));
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(NetworkProfiler.NetworkStopRequest request,
                                StreamObserver<NetworkProfiler.NetworkStopResponse> responseObserver) {
    myProcessId = -1;
    responseObserver.onNext(myPollingService.stopMonitoringApp(request));
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
  public ServerServiceDefinition bindService() {
    return super.bindService();
  }

  @Override
  public void poll() {
    if (myProcessId == -1) {
      return;
    }
    NetworkProfiler.NetworkDataRequest.Builder dataRequestBuilder = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.NetworkDataResponse response = myPollingService.getData(dataRequestBuilder.build());

    for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
      myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
      myNetworkTable.insert(data.getBasicInfo().getProcessId(), data);
      pollHttpRange();
    }
  }

  private void pollHttpRange() {
    NetworkProfiler.HttpRangeRequest.Builder requestBuilder = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myHttpRangeRequestStartTimeNs)
      .setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.HttpRangeResponse response = myPollingService.getHttpRange(requestBuilder.build());

    for (NetworkProfiler.HttpConnectionData data : response.getDataList()) {
      myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, data.getStartTimestamp() + 1);
      myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, data.getEndTimestamp() + 1);
      NetworkProfiler.HttpDetailsResponse initialData = myNetworkTable.getHttpDetailsResponseById(data.getConnId(),
                                                                                                  NetworkProfiler.HttpDetailsRequest.Type.REQUEST);

      NetworkProfiler.HttpDetailsResponse request = initialData;
      NetworkProfiler.HttpDetailsResponse responseData = null;
      NetworkProfiler.HttpDetailsResponse body = null;
      if (initialData == null) {
        request = pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
      }
      if (data.getEndTimestamp() != 0) {
        responseData = pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.RESPONSE);
        body = pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY);
      }
      myNetworkTable.insertOrReplace(myProcessId, request, responseData, body, data);
    }
  }

  private NetworkProfiler.HttpDetailsResponse pollHttpDetails(long id, NetworkProfiler.HttpDetailsRequest.Type type) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(id)
      .setType(type)
      .build();
    NetworkProfiler.HttpDetailsResponse response = myPollingService.getHttpDetails(request);
    return response;
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myNetworkTable;
  }
}