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
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * This is a thread safe class to poll events from a grpc service. This class cannot be restarted once
 * stop is called it is guaranteed that run will not be executing.
 */
public class UnifiedEventsDataPoller implements Runnable {
  private final long myStreamId;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myEventPollingService;
  @NotNull private final CountDownLatch myRunningLatch;
  @NotNull private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  @NotNull private final AtomicBoolean myIsStopCalled = new AtomicBoolean(false);

  public UnifiedEventsDataPoller(long streamId,
                                 @NotNull UnifiedEventsTable unifiedEventsTable,
                                 @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub pollingService) {
    myEventPollingService = pollingService;
    myStreamId = streamId;
    myTable = unifiedEventsTable;
    myRunningLatch = new CountDownLatch(1);
  }

  public void stop() {
    try {
      // Request the running thread to stop.
      myIsStopCalled.set(true);
      // Block stop method until the run function has completed.
      if (myIsRunning.get()) {
        myRunningLatch.await();
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  @Override
  public void run() throws StatusRuntimeException {
    myIsRunning.set(true);
    // The iterator returned will block on next calls, only returning when data is received or the server disconnects.
    Iterator<Common.Event> events = myEventPollingService.getEvents(Profiler.GetEventsRequest.getDefaultInstance());
    while (!myIsStopCalled.get() && events.hasNext()) {
      Common.Event event = events.next();
      if (event != null) {
        myTable.insertUnifiedEvent(myStreamId, event);
      }
    }
    // Signal end of run.
    myRunningLatch.countDown();
  }
}
