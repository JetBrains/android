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

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * This is a thread safe class to poll events from a grpc service. This class cannot be restarted once
 * stop is called it is guaranteed that run will not be executing.
 */
public class UnifiedEventsDataPoller implements Runnable, DataStoreTable.DataStoreTableErrorCallback {
  private final long myStreamId;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myEventPollingService;
  @NotNull private final DataStoreService myDataStoreService;
  @NotNull private final CountDownLatch myRunningLatch;
  @NotNull private final AtomicBoolean myIsRunning = new AtomicBoolean(false);

  public UnifiedEventsDataPoller(long streamId,
                                 @NotNull UnifiedEventsTable unifiedEventsTable,
                                 @NotNull TransportServiceGrpc.TransportServiceBlockingStub pollingService,
                                 @NotNull DataStoreService dataStoreService) {
    myEventPollingService = pollingService;
    myDataStoreService = dataStoreService;
    myStreamId = streamId;
    myTable = unifiedEventsTable;
    myRunningLatch = new CountDownLatch(1);
  }

  @Override
  public void onDataStoreError(Throwable t) {
    myDataStoreService.disconnect(myStreamId);
  }

  public void stop() {
    try {
      // Block stop method until the run function has completed.
      if (myIsRunning.get()) {
        myRunningLatch.await();
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  @Override
  public void run() {
    myIsRunning.set(true);
    try {
      // The iterator returned will block on next calls, only returning when data is received or the server disconnects.
      Iterator<Event> events = myEventPollingService.getEvents(GetEventsRequest.getDefaultInstance());
      while (events.hasNext()) {
        Event event = events.next();
        if (event != null) {
          myTable.insertUnifiedEvent(myStreamId, event);
        }
      }
    }
    catch (StatusRuntimeException exception) {
      // device disconnect logic handle via TransportDeviceManager
    }
    // Signal end of run.
    myRunningLatch.countDown();
  }
}
