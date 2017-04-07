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
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.RunnableFuture;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventDataPoller extends EventServiceGrpc.EventServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private EventServiceGrpc.EventServiceBlockingStub myEventPollingService;
  private int myProcessId = -1;

  //TODO: Pull into a storage class that can manage caching data to disk.
  private Map<Long, EventProfiler.ActivityData> myActivityDataMap = new HashMap<>();
  private Map<Long, EventProfiler.SystemData> mySystemMap = new HashMap<>();
  private Object myActivityLock = new Object();
  private Object mySystemDataLock = new Object();

  public EventDataPoller() {

  }

  @Override
  public void poll() throws StatusRuntimeException {
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);

    // Query for and cache activity data that has changed since our last polling.
    EventProfiler.ActivityDataResponse activityResponse = myEventPollingService.getActivityData(dataRequestBuilder.build());
    synchronized (myActivityLock) {
      for (EventProfiler.ActivityData data : activityResponse.getDataList()) {
        long id = data.getHash();
        if (myActivityDataMap.containsKey(id)) {
          EventProfiler.ActivityData cached_data = myActivityDataMap.get(id);
          EventProfiler.ActivityData.Builder builder = myActivityDataMap.get(id).toBuilder();

          // Perfd may return states that we already have cached. This checks for that and only adds unique ones.
          for (EventProfiler.ActivityStateData state : data.getStateChangesList()) {
            if (!cached_data.getStateChangesList().contains(state)) {
              builder.addStateChanges(state);
            }
            if (state.getTimestamp() > myDataRequestStartTimestampNs) {
              myDataRequestStartTimestampNs = state.getTimestamp();
            }
          }
          myActivityDataMap.replace(id, builder.build());
        }
        else {
          myActivityDataMap.put(id, data);
          for (EventProfiler.ActivityStateData state : data.getStateChangesList()) {
            if (state.getTimestamp() > myDataRequestStartTimestampNs) {
              myDataRequestStartTimestampNs = state.getTimestamp();
            }
          }
        }
      }
    }

    // Poll for system event data. If we have a duplicate event then we replace it with the incomming one.
    // we replace the event as the event information may have changed, eg now it has an uptime where previously it didn't
    EventProfiler.SystemDataResponse systemResponse = myEventPollingService.getSystemData(dataRequestBuilder.build());
    synchronized (mySystemDataLock) {
      for (EventProfiler.SystemData data : systemResponse.getDataList()) {
        long id = data.getEventId();
        mySystemMap.put(id, data);
      }
    }
  }

  @Override
  public void getActivityData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
    EventProfiler.ActivityDataResponse.Builder response = EventProfiler.ActivityDataResponse.newBuilder();
    synchronized (myActivityLock) {
      for (EventProfiler.ActivityData data : myActivityDataMap.values()) {
        if (data.getAppId() != request.getAppId()) {
          continue;
        }
        // We always return information about an activity to the caller. This is so the caller can choose to act on this
        // information or drop it.
        EventProfiler.ActivityData.Builder builder = EventProfiler.ActivityData.newBuilder();
        builder.setName(data.getName());
        builder.setAppId(data.getAppId());
        builder.setHash(data.getHash());

        // Loop through each state change event an activity has gone through and add
        // 1) the first state change before the current start time.
        // 2) add all the state changes in the current time range.
        // 3) add the latest state change assuming the first two criteria are not met.
        for (int i = 0; i < data.getStateChangesCount(); i++) {
          EventProfiler.ActivityStateData state = data.getStateChanges(i);
          if (state.getTimestamp() > request.getStartTimestamp() && state.getTimestamp() < request.getEndTimestamp()) {
            if (builder.getStateChangesCount() == 0 && i > 0) {
              builder.addStateChanges(data.getStateChanges(i - 1));
            }
            builder.addStateChanges(state);
          }
          else if (state.getTimestamp() > request.getEndTimestamp()) {
            builder.addStateChanges(state);
            break;
          }
        }
        if (builder.getStateChangesCount() == 0) {
          builder.addStateChanges(data.getStateChanges(data.getStateChangesCount() - 1));
        }
        response.addData(builder);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getSystemData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.SystemDataResponse> responseObserver) {
    EventProfiler.SystemDataResponse.Builder response = EventProfiler.SystemDataResponse.newBuilder();
    synchronized (mySystemDataLock) {
      for (EventProfiler.SystemData data : mySystemMap.values()) {
        if (request.getAppId() != data.getAppId()) {
          continue;
        }
        if ((data.getStartTimestamp() < request.getEndTimestamp()) &&
            data.getEndTimestamp() >= request.getStartTimestamp() || data.getEndTimestamp() == 0) {
          response.addData(data);
        }
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventProfiler.EventStartRequest request, StreamObserver<EventProfiler.EventStartResponse> observer) {
    myProcessId = request.getAppId();
    observer.onNext(myEventPollingService.startMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopMonitoringApp(EventProfiler.EventStopRequest request, StreamObserver<EventProfiler.EventStopResponse> observer) {
    myProcessId = -1;
    observer.onNext(myEventPollingService.stopMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myEventPollingService = EventServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public RunnableFuture<Void> getRunner() {
    return new PollRunner(this, PollRunner.POLLING_DELAY_NS);
  }
}
