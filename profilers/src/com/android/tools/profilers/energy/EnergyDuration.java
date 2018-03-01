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
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encapsulate a sequence of related energy events into a single class.
 *
 * This can be useful for reading through a stream of intermingled energy events (multiple wakelock
 * events, alarm events, etc.) and separating them into their own individual sequences. Use
 * {@link #groupById(List)} to accomplish this.
 */
public final class EnergyDuration implements Comparable<EnergyDuration> {

  public enum Kind {
    UNKNOWN,
    WAKE_LOCK,
    ALARM,
    JOB
  }

  // Non-empty event list that the events share the same id in time order.
  @NotNull private final ImmutableList<EnergyProfiler.EnergyEvent> myEventList;

  public EnergyDuration(@NotNull List<EnergyProfiler.EnergyEvent> eventList) {
    assert !eventList.isEmpty();
    myEventList = ImmutableList.copyOf(eventList);
  }

  @Override
  public int compareTo(@NotNull EnergyDuration another) {
    return (int) (getInitialTimestamp() - another.getInitialTimestamp());
  }

  public long getInitialTimestamp() {
    return myEventList.get(0).getTimestamp();
  }

  public long getFinalTimestamp() {
    if (myEventList.size() <= 1) {
      return Long.MAX_VALUE;
    }

    EnergyProfiler.EnergyEvent lastEvent = myEventList.get(myEventList.size() - 1);
    return lastEvent.getIsTerminal() ? lastEvent.getTimestamp() : Long.MAX_VALUE;
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
        if (myEventList.get(0).getAlarmSet().hasListener()) {
          return myEventList.get(0).getAlarmSet().getListener().getTag();
        }
        break;
      case ALARM_CANCELLED:
        if (myEventList.get(0).getAlarmCancelled().hasListener()) {
          return myEventList.get(0).getAlarmCancelled().getListener().getTag();
        }
        break;
      case JOB_SCHEDULED:
        return String.valueOf(myEventList.get(0).getJobScheduled().getJob().getJobId());
      case JOB_STARTED:
        return String.valueOf(myEventList.get(0).getJobStarted().getParams().getJobId());
      case JOB_STOPPED:
        return String.valueOf(myEventList.get(0).getJobStopped().getParams().getJobId());
      case JOB_FINISHED:
        return String.valueOf(myEventList.get(0).getJobFinished().getParams().getJobId());
      default:
        getLogger().warn("First event in duration is " + myEventList.get(0).getMetadataCase().name());
        break;
    }
    return "unspecified";
  }

  @NotNull
  public Kind getKind() {
    switch (myEventList.get(0).getMetadataCase()) {
      case WAKE_LOCK_ACQUIRED:
      case WAKE_LOCK_RELEASED:
        return Kind.WAKE_LOCK;

      case ALARM_SET:
      case ALARM_CANCELLED:
        return Kind.ALARM;

      case JOB_SCHEDULED:
      case JOB_STARTED:
      case JOB_STOPPED:
      case JOB_FINISHED:
        return Kind.JOB;

      default:
        getLogger().warn("Unsupported Kind for " + myEventList.get(0).getMetadataCase().name());
        return Kind.UNKNOWN;
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
  public static List<EnergyDuration> groupById(@NotNull List<EnergyProfiler.EnergyEvent> events) {
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
    return durationMap.values().stream().map(list -> new EnergyDuration(list)).collect(Collectors.toList());
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(EnergyDuration.class);
  }
}
