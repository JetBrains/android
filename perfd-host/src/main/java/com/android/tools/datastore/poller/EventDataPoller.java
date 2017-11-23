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

import com.android.tools.datastore.database.EventsTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler.*;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.StatusRuntimeException;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventDataPoller extends PollRunner {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private int myProcessId = -1;
  private final Common.Session mySession;
  private final EventsTable myEventsTable;
  private final EventServiceGrpc.EventServiceBlockingStub myEventPollingService;

  public EventDataPoller(int processId,
                         Common.Session session,
                         EventsTable eventTable,
                         EventServiceGrpc.EventServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myEventsTable = eventTable;
    myEventPollingService = pollingService;
    mySession = session;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    if (myProcessId == -1) {
      return;
    }

    EventDataRequest.Builder dataRequestBuilder = EventDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    // Query for and cache activity data that has changed since our last polling.
    ActivityDataResponse activityResponse = myEventPollingService.getActivityData(dataRequestBuilder.build());
    for (ActivityData data : activityResponse.getDataList()) {
      long id = data.getHash();
      ActivityData cached_data = myEventsTable.findActivityDataOrNull(data.getProcessId(), id, mySession);
      if (cached_data != null) {
        ActivityData.Builder builder = cached_data.toBuilder();
        // Perfd may return states that we already have cached. This checks for that and only adds unique ones.
        for (ActivityStateData state : data.getStateChangesList()) {
          if (!cached_data.getStateChangesList().contains(state)) {
            builder.addStateChanges(state);
          }
          if (state.getTimestamp() > myDataRequestStartTimestampNs) {
            myDataRequestStartTimestampNs = state.getTimestamp();
          }
        }
        myEventsTable.insertOrReplace(id, mySession, builder.build());
      }
      else {
        myEventsTable.insertOrReplace(id, mySession, data);
        for (ActivityStateData state : data.getStateChangesList()) {
          if (state.getTimestamp() > myDataRequestStartTimestampNs) {
            myDataRequestStartTimestampNs = state.getTimestamp();
          }
        }
      }
    }

    // Poll for system event data. If we have a duplicate event then we replace it with the incomming one.
    // we replace the event as the event information may have changed, eg now it has an uptime where previously it didn't
    SystemDataResponse systemResponse = myEventPollingService.getSystemData(dataRequestBuilder.build());
    for (SystemData data : systemResponse.getDataList()) {
      long id = data.getEventId();
      myEventsTable.insertOrReplace(id, mySession, data);
    }
  }
}
