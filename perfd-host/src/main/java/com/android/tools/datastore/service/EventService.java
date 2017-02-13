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

import com.android.tools.datastore.DataStoreDatabase;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.EventsTable;
import com.android.tools.datastore.poller.EventDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventService extends EventServiceGrpc.EventServiceImplBase implements ServicePassThrough {

  private EventsTable myEventsTable = new EventsTable();
  private Map<Integer, PollRunner> myRunners = new HashMap<>();
  private Consumer<Runnable> myFetchExecutor;
  private DataStoreService myService;
  public EventService(@NotNull DataStoreService dataStoreService, Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
  }

  @Override
  public void getActivityData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
    EventProfiler.ActivityDataResponse.Builder response = EventProfiler.ActivityDataResponse.newBuilder();
    Common.Session session = request.getSession();
    List<EventProfiler.ActivityData> activites = myEventsTable.getActivityDataByApp(request.getProcessId(), session);
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
    observer.onNext(myService.getEventClient(request.getSession()).startMonitoringApp(request));
    observer.onCompleted();
    int processId = request.getProcessId();
    Common.Session session = request.getSession();
    myRunners.put(processId, new EventDataPoller(processId, session, myEventsTable, myService.getEventClient(session)));
    myFetchExecutor.accept(myRunners.get(processId));
  }

  @Override
  public void stopMonitoringApp(EventProfiler.EventStopRequest request, StreamObserver<EventProfiler.EventStopResponse> observer) {
    int processId = request.getProcessId();
    myRunners.remove(processId).stop();

    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    EventServiceGrpc.EventServiceBlockingStub service = myService.getEventClient(request.getSession());
    if (service == null) {
      observer.onNext(EventProfiler.EventStopResponse.getDefaultInstance());
    } else {
      observer.onNext(service.stopMonitoringApp(request));
    }
    observer.onCompleted();
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myEventsTable;
  }
}
