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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A class which handles fetching a list of {@link EnergyDuration} instances within a
 * specified range. When the range changes, the list will automatically be updated, and this class
 * will notify any listeners.
 */
public class EnergyEventsFetcher {

  // myAspectObserver cannot be local to prevent early GC
  @SuppressWarnings("FieldCanBeLocal") private final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myClient;
  @NotNull Common.Session mySession;
  @NotNull private final List<Listener> myListeners = new ArrayList<>();

  @NotNull private final Range myRange;

  @Nullable private List<EnergyDuration> myDurationList;

  public EnergyEventsFetcher(@NotNull EnergyServiceGrpc.EnergyServiceBlockingStub client,
                             @NotNull Common.Session session, @NotNull Range range) {
    myClient = client;
    mySession = session;
    myRange = range;
    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::handleRangeUpdated);
    handleRangeUpdated();
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
    if (myDurationList != null) {
      fireListeners(myDurationList);
    }
  }

  private void handleRangeUpdated() {
    List<EnergyDuration> durationList = new ArrayList<>();

    if (!myRange.isEmpty()) {
      EnergyProfiler.EnergyRequest request = EnergyProfiler.EnergyRequest.newBuilder()
        .setSession(mySession)
        .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin()))
        .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax()))
        .build();
      List<Common.Event> eventList = myClient.getEvents(request).getEventsList();
      List<EnergyDuration> partialDurations = EnergyDuration.groupById(eventList);
      for (EnergyDuration partialDuration : partialDurations) {
        EnergyProfiler.EnergyEventGroupRequest eventGroupRequest = EnergyProfiler.EnergyEventGroupRequest.newBuilder()
          .setSession(mySession)
          .setEventId(partialDuration.getEventList().get(0).getGroupId())
          .build();
        durationList.add(new EnergyDuration(myClient.getEventGroup(eventGroupRequest).getEventsList()));
      }
    }

    if (myDurationList == null || !myDurationList.equals(durationList)) {
      myDurationList = durationList;
      fireListeners(myDurationList);
    }
  }

  private void fireListeners(@NotNull List<EnergyDuration> list) {
    for (Listener l : myListeners) {
      l.onUpdated(list);
    }
  }

  public interface Listener {
    void onUpdated(@NotNull List<EnergyDuration> durationList);
  }
}
