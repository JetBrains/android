/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Repeated get events from datastore and categorize by event unique ID, then send events to all listeners.
 */
public class EnergyEventsFetcher implements Updatable {

  private static final long FETCH_FREQUENCY = TimeUnit.MILLISECONDS.toNanos(250);

  // myAspectObserver cannot be local to prevent early GC
  @SuppressWarnings("FieldCanBeLocal") private final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myClient;
  @NotNull Common.Session mySession;
  @NotNull private final List<Listener> myListeners = new ArrayList<>();

  @NotNull private final Range myRange;

  @Nullable private List<EventDuration> myDurationList;

  /**
   * Time accumulated since the last poll.
   */
  private long myAccumNs;

  public EnergyEventsFetcher(@NotNull EnergyServiceGrpc.EnergyServiceBlockingStub client,
                             @NotNull Common.Session session, @NotNull Range range) {
    myClient = client;
    mySession = session;
    myRange = range;
    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::pollImmediately);
    pollImmediately();
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
    if (myDurationList != null) {
      fireListeners(myDurationList);
    }
  }

  @Override
  public void update(long elapsedNs) {
    myAccumNs += elapsedNs;
    // If data list is not set yet, we always want to fetch regardless of accumulated time
    if (myAccumNs < FETCH_FREQUENCY && myDurationList != null) {
      return;
    }

    myAccumNs = 0;
    if (myDurationList == null) {
      myDurationList = new ArrayList<>();

      if (!myRange.isEmpty()) {
        EnergyProfiler.EnergyRequest request = EnergyProfiler.EnergyRequest.newBuilder()
          .setSession(mySession)
          .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin()))
          .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax()))
          .build();
        List<EnergyEvent> eventList = myClient.getEvents(request).getEventList();
        myDurationList = EventDuration.groupById(eventList);
      }

      fireListeners(myDurationList);
    }
  }

  private void pollImmediately() {
    myDurationList = null;
    update(0);
  }

  private void fireListeners(@NotNull List<EventDuration> list) {
    for (Listener l : myListeners) {
      l.onUpdated(list);
    }
  }

  public interface Listener {
    void onUpdated(@NotNull List<EventDuration> durationList);
  }
}
