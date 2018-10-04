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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.StatusRuntimeException;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;

public class UnifiedEventsDataPoller implements Runnable {

  private final long myStreamId;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myEventPollingService;
  private boolean myIsRunning;

  public UnifiedEventsDataPoller(long streamId,
                                 @NotNull UnifiedEventsTable unifiedEventsTable,
                                 @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub pollingService) {
    myEventPollingService = pollingService;
    myStreamId = streamId;
    myTable = unifiedEventsTable;
    // Default to true, if stop is called before the thread is run we will stop the thread immediately.
    myIsRunning = true;
  }

  public void stop() {
    myIsRunning = false;
  }

  @Override
  public void run() throws StatusRuntimeException {
    // The iterator returned will block on next calls, only returning when data is received or the server disconnects.
    Iterator<Profiler.Event> events = myEventPollingService.getEvents(Profiler.GetEventsRequest.getDefaultInstance());
    while (myIsRunning && events.hasNext()) {
      Profiler.Event event = events.next();
      if (event != null) {
        myTable.insertUnifiedEvent(myStreamId, event);
      }
    }
    myIsRunning = false;
  }
}
