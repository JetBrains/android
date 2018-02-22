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

import com.android.tools.profiler.proto.EnergyProfiler;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Duration to encapsulate related sequence events. For example, a wake lock instance's acquire and release.
 */
public final class EventDuration implements Comparable<EventDuration> {

  // Non-empty event list that the events share the same id in time order.
  @NotNull private final ImmutableList<EnergyProfiler.EnergyEvent> myEventList;

  public EventDuration(@NotNull List<EnergyProfiler.EnergyEvent> eventList) {
    assert !eventList.isEmpty();
    myEventList = ImmutableList.copyOf(eventList);
  }

  @Override
  public int compareTo(@NotNull EventDuration another) {
    return (int) (getInitialTimestamp() - another.getInitialTimestamp());
  }

  public long getInitialTimestamp() {
    return myEventList.get(0).getTimestamp();
  }

  /**
   * Returns the duration name, which is tag for wake lock, etc.
   */
  @NotNull
  public String getName() {
    switch (myEventList.get(0).getMetadataCase()) {
      case WAKE_LOCK_ACQUIRED:
        // TODO(b/73852076): Handle if the first event item is a released wakelock
        return myEventList.get(0).getWakeLockAcquired().getTag();
      case ALARM_SET:
        return "alarm" + TimeUnit.NANOSECONDS.toMillis(getInitialTimestamp());
      default:
        return "unspecified";
    }
  }

  @NotNull
  public String getKind() {
    switch (myEventList.get(0).getMetadataCase()) {
      case WAKE_LOCK_ACQUIRED:
        // fall through
      case WAKE_LOCK_RELEASED:
        return "wakelock";
      case ALARM_SET:
        // fall through
      case ALARM_CANCELLED:
        return "alarm";
      default:
        return "unspecified";
    }
  }

  @NotNull
  public ImmutableList<EnergyProfiler.EnergyEvent> getEventList() {
    return myEventList;
  }

  /**
   * Events are grouped by its unique ID which indicates those events sharing a duration. Assuming the only events without an ID are
   * energy sample data and put them in one list. For example, events are grouped to 3 durations {[1000ms, metadata], [[1200ms, metadata],
   * [1300ms, metadata]], [[1400ms, metadata], [1600ms, metadata]]}. First duration includes single event at 1000ms, second duration has
   * two events at 1200ms and 1300ms, third duration includes two events at 1400ms and 1600ms.
   */
  @NotNull
  public static List<EventDuration> groupById(List<EnergyProfiler.EnergyEvent> events) {
    Map<Integer, List<EnergyProfiler.EnergyEvent>> durationMap = new LinkedHashMap<>();
    for (EnergyProfiler.EnergyEvent event : events) {
      if (durationMap.containsKey(event.getEventId())) {
        durationMap.get(event.getEventId()).add(event);
      }
      else {
        List<EnergyProfiler.EnergyEvent> list = new ArrayList<>();
        list.add(event);
        durationMap.put(event.getEventId(), list);
      }
    }
    return durationMap.values().stream().map(list -> new EventDuration(list)).collect(Collectors.toList());
  }
}
