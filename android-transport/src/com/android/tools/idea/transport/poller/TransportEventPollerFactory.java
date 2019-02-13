/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.poller;

import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Creates and keeps track of TransportEventPollers to tick them on the same thread
 */
public class TransportEventPollerFactory {
  @NotNull private final ScheduledExecutorService myExecutorService;
  @NotNull private final Map<TransportEventPoller, ScheduledFuture> myScheduledFutures;

  private TransportEventPollerFactory() {
    myExecutorService = Executors.newScheduledThreadPool(1);
    myScheduledFutures = new HashMap<>();
  }

  public TransportEventPoller createPoller(@NotNull TransportServiceGrpc.TransportServiceBlockingStub transportClient,
                                           long pollPeriodNs) {
    TransportEventPoller poller = new TransportEventPoller(transportClient);
    ScheduledFuture<?> scheduledFuture = myExecutorService.scheduleAtFixedRate(() -> poller.poll(),
                                                                               0, pollPeriodNs, TimeUnit.NANOSECONDS);
    myScheduledFutures.put(poller, scheduledFuture);
    return poller;
  }

  public void stopPoller(@NotNull TransportEventPoller poller) {
    ScheduledFuture<?> scheduledFuture = myScheduledFutures.remove(poller);
    scheduledFuture.cancel(false);
  }

  /**
   * Singleton helpers
   */
  private static class SingletonCreator {
    private static final TransportEventPollerFactory instance = new TransportEventPollerFactory();
  }

  public static TransportEventPollerFactory getInstance() {
    return SingletonCreator.instance;
  }
}
