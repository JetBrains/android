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
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RunnableFuture;

public class NetworkDataPoller extends NetworkServiceGrpc.NetworkServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  // Intentionally accessing this field out of sync block because it's OK for it to be o
  // off by a frame; we'll pick up all data eventually
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private NetworkServiceGrpc.NetworkServiceBlockingStub myPollingService;
  //TODO Pull this into a storage container that can read/write this to disk
  private final List<NetworkProfiler.NetworkProfilerData> myData = new ArrayList<>();
  private int myProcessId = -1;

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

    //TODO: Optimize so we do not need to loop all the data every request, ideally binary search to start time and loop till end.
    synchronized (myData) {
      if (myData.size() == 0) {
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
        return;
      }

      long startTime = request.getStartTimestamp();
      long endTime = request.getEndTimestamp();

      for (NetworkProfiler.NetworkProfilerData data : myData) {
        long current = data.getBasicInfo().getEndTimestamp();
        if (current > startTime && current <= endTime) {
          if ((request.getType() == NetworkProfiler.NetworkDataRequest.Type.ALL) ||
              (request.getType() == NetworkProfiler.NetworkDataRequest.Type.SPEED &&
               data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.SPEED_DATA) ||
              (request.getType() == NetworkProfiler.NetworkDataRequest.Type.CONNECTIONS &&
               data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.CONNECTION_DATA) ||
              (request.getType() == NetworkProfiler.NetworkDataRequest.Type.CONNECTIVITY &&
               data.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.CONNECTIVITY_DATA)) {
            response.addData(data);
          }
        }
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void startMonitoringApp(NetworkProfiler.NetworkStartRequest request,
                                 StreamObserver<NetworkProfiler.NetworkStartResponse> responseObserver) {
    synchronized (myData) {
      myData.clear();
    }

    myProcessId = request.getAppId();
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
    responseObserver.onNext(myPollingService.getHttpRange(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpDetails(NetworkProfiler.HttpDetailsRequest request,
                             StreamObserver<NetworkProfiler.HttpDetailsResponse> responseObserver) {
    responseObserver.onNext(myPollingService.getHttpDetails(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getPayload(NetworkProfiler.NetworkPayloadRequest request,
                         StreamObserver<NetworkProfiler.NetworkPayloadResponse> responseObserver) {
    responseObserver.onNext(myPollingService.getPayload(request));
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
      .setAppId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.NetworkDataResponse response = myPollingService.getData(dataRequestBuilder.build());

    synchronized (myData) {
      for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
        myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
        myData.add(data);
      }
    }
  }
}
