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
import com.android.tools.datastore.database.EventsTable;
import com.android.tools.datastore.poller.EventDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventService extends EventServiceGrpc.EventServiceImplBase implements ServicePassThrough {
  /**
   * This number controls the minimum number of states we return before the requested timestamp range.
   * eg. Requested Range = |-------------|
   * States =  ^  ^  ^   ^    ^   ^    ^   ^
   * Result =     ^  ^   ^    ^   ^.
   * With a prefix count of 3 we return the 3 states before our minimum range to ensure we return
   * enough information back to the caller for them to understand the current state of the activity.
   * The reason we are fixed at 3 is because the UI cares about when an activity is paused. There
   * are at most 2 other states after paused that each event gets. The states are either SAVED/STOPPED and DESTROYED/REMOVED.
   */
  private static final int INCLUDED_STATE_CHANGES_BEFORE_START_COUNT = 3;
  private final EventsTable myEventsTable;
  private final Map<Long, PollRunner> myRunners = new HashMap<>();
  private final Consumer<Runnable> myFetchExecutor;
  private final DataStoreService myService;

  public EventService(@NotNull DataStoreService dataStoreService,
                      Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
    myEventsTable = new EventsTable();
  }

  /**
   * This function responds with a stream of activities and the state changes for the activities within a given range.
   * Note if the caller request activities for range X to Y. The response will return all activities in range X to Y in addition
   * to at most {@link INCLUDED_STATE_CHANGES_BEFORE_START_COUNT} states before X.
   *
   * @param request
   * @param responseObserver
   */
  @Override
  public void getActivityData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
    EventProfiler.ActivityDataResponse.Builder response = EventProfiler.ActivityDataResponse.newBuilder();
    Common.Session session = request.getSession();
    List<EventProfiler.ActivityData> activities = myEventsTable.getActivityDataBySession(session);
    for (EventProfiler.ActivityData data : activities) {
      // We always return information about an activity to the caller. This is so the caller can choose to act on this
      // information or drop it.
      EventProfiler.ActivityData.Builder builder = EventProfiler.ActivityData.newBuilder();
      builder.setName(data.getName());
      builder.setHash(data.getHash());
      builder.setActivityContextHash(data.getActivityContextHash());

      // Find the first index greater than our initial request range.
      int firstIndexGreaterThanStart = 0;
      while (firstIndexGreaterThanStart < data.getStateChangesCount() &&
             data.getStateChanges(firstIndexGreaterThanStart).getTimestamp() < request.getStartTimestamp()) {
        firstIndexGreaterThanStart++;
      }
      firstIndexGreaterThanStart = Math.max(0, firstIndexGreaterThanStart - INCLUDED_STATE_CHANGES_BEFORE_START_COUNT);
      // Starting at our new index add all states until we encounter one that is greater than our end timestamp.
      for (int i = firstIndexGreaterThanStart; i < data.getStateChangesCount(); i++) {
        EventProfiler.ActivityStateData state = data.getStateChanges(i);
        if (state.getTimestamp() > request.getEndTimestamp()) {
          break;
        }
        builder.addStateChanges(state);
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
    for (EventProfiler.SystemData data : systemData) {
      response.addData(data);
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventProfiler.EventStartRequest request, StreamObserver<EventProfiler.EventStartResponse> observer) {
    EventServiceGrpc.EventServiceBlockingStub client = myService.getEventClient(request.getSession().getStreamId());
    if (client != null) {
      observer.onNext(client.startMonitoringApp(request));
      observer.onCompleted();
      Common.Session session = request.getSession();
      myRunners.put(session.getSessionId(), new EventDataPoller(session, myEventsTable, client));
      myFetchExecutor.accept(myRunners.get(session.getSessionId()));
    }
    else {
      observer.onNext(EventProfiler.EventStartResponse.getDefaultInstance());
      observer.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(EventProfiler.EventStopRequest request, StreamObserver<EventProfiler.EventStopResponse> observer) {
    long sessionId = request.getSession().getSessionId();
    PollRunner runner = myRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }

    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    EventServiceGrpc.EventServiceBlockingStub client = myService.getEventClient(request.getSession().getStreamId());
    if (client == null) {
      observer.onNext(EventProfiler.EventStopResponse.getDefaultInstance());
    }
    else {
      observer.onNext(client.stopMonitoringApp(request));
    }
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
    myEventsTable.initialize(connection);
  }
}
