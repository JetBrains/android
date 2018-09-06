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

import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

public class UnifiedEventsDataPoller extends PollRunner {

  private long myLastPollTimestamp;
  @NotNull private final int myStreamId;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myEventPollingService;

  public UnifiedEventsDataPoller(int streamId,
                         @NotNull UnifiedEventsTable unifiedEventsTable,
                         @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myEventPollingService = pollingService;
    myStreamId = streamId;
    myTable = unifiedEventsTable;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    Profiler.GetEventsRequest request = Profiler.GetEventsRequest.newBuilder()
                                                                 .setFromTimestamp(myLastPollTimestamp)
                                                                 .setToTimestamp(Long.MAX_VALUE).build();
    Profiler.GetEventsResponse response = myEventPollingService.getEvents(request);
    myTable.insertUnifiedEvents(myStreamId, response.getEventsList());
    // If we got back any events, update our last polled event time to be the timestamp of the last event.
    // This assumes the timestamps we get back are in chronological order. If not that is okay as,
    // any duplicated data retrieved will update existing results.
    if (response.getEventsCount() > 0) {
      myLastPollTimestamp = response.getEventsList().get(response.getEventsCount() - 1).getTimestamp();
    }
  }
}
