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
import com.android.tools.datastore.database.EventsTable;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.RunnableFuture;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventDataPoller extends EventServiceGrpc.EventServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private EventServiceGrpc.EventServiceBlockingStub myEventPollingService;
  private int myProcessId = -1;

  private EventsTable myEventsTable = new EventsTable();
  private Object myActivityLock = new Object();
  private Object mySystemDataLock = new Object();

  public EventDataPoller() {
  }

  @Override
  public void poll() throws StatusRuntimeException {
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);

    // Query for and cache activity data that has changed since our last polling.
    EventProfiler.ActivityDataResponse activityResponse = myEventPollingService.getActivityData(dataRequestBuilder.build());
    synchronized (myActivityLock) {
      for (EventProfiler.ActivityData data : activityResponse.getDataList()) {
        long id = data.getHash();
        EventProfiler.ActivityData cached_data = myEventsTable.findActivityDataOrNull(data.getProcessId(), id);
        if (cached_data != null) {
          EventProfiler.ActivityData.Builder builder = cached_data.toBuilder();
          // Perfd may return states that we already have cached. This checks for that and only adds unique ones.
          for (EventProfiler.ActivityStateData state : data.getStateChangesList()) {
            if (!cached_data.getStateChangesList().contains(state)) {
              builder.addStateChanges(state);
            }
            if (state.getTimestamp() > myDataRequestStartTimestampNs) {
              myDataRequestStartTimestampNs = state.getTimestamp();
            }
          }
          myEventsTable.insertOrReplace(id, builder.build());
        }
        else {
          myEventsTable.insertOrReplace(id, data);
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
        myEventsTable.insertOrReplace(id, data);
      }
    }
  }

  @Override
  public void getActivityData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
    EventProfiler.ActivityDataResponse.Builder response = EventProfiler.ActivityDataResponse.newBuilder();
    synchronized (myActivityLock) {
      List<EventProfiler.ActivityData> activites = myEventsTable.getActivityDataByApp(request.getProcessId());
      for (EventProfiler.ActivityData data : activites) {
        // We always return information about an activity to the caller. This is so the caller can choose to act on this
        // information or drop it.
        EventProfiler.ActivityData.Builder builder = EventProfiler.ActivityData.newBuilder();
        builder.setName(data.getName());
        builder.setProcessId(data.getProcessId());
        builder.setHash(data.getHash());

        // Loop through each state change event an activity has gone through and add
        // 1) the first state change before the current start time.
        // 2) add all the state changes in the current time range.
        // 3) add the latest state change assuming the first two criteria are not met.
        for (int i = 0; i < data.getStateChangesCount(); i++) {
          EventProfiler.ActivityStateData state = data.getStateChanges(i);
          if (state.getTimestamp() > request.getStartTimestamp() && state.getTimestamp() <= request.getEndTimestamp()) {
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
    List<EventProfiler.SystemData> systemData = myEventsTable.getSystemDataByRequest(request);
    for(EventProfiler.SystemData data : systemData) {
      response.addData(data);
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventProfiler.EventStartRequest request, StreamObserver<EventProfiler.EventStartResponse> observer) {
    myProcessId = request.getProcessId();
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

  @Override
  public DatastoreTable getDatastoreTable() {
    return myEventsTable;
  }
}
