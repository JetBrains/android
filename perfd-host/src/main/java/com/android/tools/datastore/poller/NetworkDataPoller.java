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
import net.jcip.annotations.GuardedBy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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

  private final Object myLock = new Object();
  @GuardedBy("myLock") private final List<NetworkProfiler.NetworkProfilerData> myData = new ArrayList<>();
  @GuardedBy("myLock") private final Map<Long, ConnectionData> myConnectionData = new LinkedHashMap<>();

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
    synchronized (myLock) {
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

    synchronized (myLock) {
      myData.clear();
      myConnectionData.clear();
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
    NetworkProfiler.HttpRangeResponse.Builder response = NetworkProfiler.HttpRangeResponse.newBuilder();
    long startTime = request.getStartTimestamp();
    long endTime = request.getEndTimestamp();

    synchronized (myLock) {
      // Because myConnectionRangeData.values is in sorted order (by start time), then,
      // based on the requested range, we can exclude older connections and stop if we get to newer connections.
      for (ConnectionData allData: myConnectionData.values()) {
        NetworkProfiler.HttpConnectionData data = allData.myCommonData;
        if (endTime < data.getStartTimestamp()) {
          break;
        }
        if (data.getEndTimestamp() != 0 && data.getEndTimestamp() < startTime) {
          continue;
        }
        response.addData(data);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getHttpDetails(NetworkProfiler.HttpDetailsRequest request,
                             StreamObserver<NetworkProfiler.HttpDetailsResponse> responseObserver) {
    NetworkProfiler.HttpDetailsResponse.Builder response = NetworkProfiler.HttpDetailsResponse.newBuilder();
    synchronized (myLock) {
      ConnectionData details = myConnectionData.get(request.getConnId());
      switch (request.getType()) {
        case REQUEST:
          response.setRequest(details.myRequest);
          break;
        case RESPONSE:
          response.setResponse(details.myResponse);
          break;
        case RESPONSE_BODY:
          response.setResponseBody(details.myResponseBody);
          break;
        default:
          assert false : "Unsupported request type " + request.getType();
      }
    }
    responseObserver.onNext(response.build());
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

    synchronized (myLock) {
      for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
        myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
        myData.add(data);
      }
      pollHttpRange();
    }
  }

  private void pollHttpRange() {
    NetworkProfiler.HttpRangeRequest.Builder requestBuilder = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(myHttpRangeRequestStartTimeNs)
      .setEndTimestamp(Long.MAX_VALUE);
    NetworkProfiler.HttpRangeResponse response = myPollingService.getHttpRange(requestBuilder.build());

    synchronized (myLock) {
      for (NetworkProfiler.HttpConnectionData data : response.getDataList()) {
        myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, data.getStartTimestamp() + 1);
        myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, data.getEndTimestamp() + 1);

        if (!myConnectionData.containsKey(data.getConnId())) {
          myConnectionData.put(data.getConnId(), new ConnectionData(data));
          pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
        } else {
          myConnectionData.get(data.getConnId()).myCommonData = data;
        }

        if (data.getEndTimestamp() != 0) {
          pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.RESPONSE);
          pollHttpDetails(data.getConnId(), NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY);
        }
      }
    }
  }

  private void pollHttpDetails(long connectionId, NetworkProfiler.HttpDetailsRequest.Type type) {
    NetworkProfiler.HttpDetailsRequest request = NetworkProfiler.HttpDetailsRequest.newBuilder()
      .setConnId(connectionId)
      .setType(type)
      .build();
    NetworkProfiler.HttpDetailsResponse response = myPollingService.getHttpDetails(request);

    synchronized (myLock) {
      ConnectionData data = myConnectionData.get(connectionId);
      switch (type) {
        case REQUEST:
          data.myRequest = response.getRequest();
          break;
        case RESPONSE:
          data.myResponse = response.getResponse();
          break;
        case RESPONSE_BODY:
          data.myResponseBody = response.getResponseBody();
          break;
        default:
          assert false : "Unsupported response type " + type;
      }
    }
  }

  private static final class ConnectionData {
    @NotNull private NetworkProfiler.HttpConnectionData myCommonData;
    private NetworkProfiler.HttpDetailsResponse.Body myResponseBody;
    private NetworkProfiler.HttpDetailsResponse.Request myRequest;
    private NetworkProfiler.HttpDetailsResponse.Response myResponse;

    private ConnectionData(@NotNull NetworkProfiler.HttpConnectionData commonData) {
      myCommonData = commonData;
    }
  }
}
