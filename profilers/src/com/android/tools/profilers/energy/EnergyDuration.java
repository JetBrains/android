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

import java.util.*;
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
    UNKNOWN ("n/a"),
    WAKE_LOCK ("Wake Lock"),
    ALARM("Alarm"),
    JOB("Job"),
    LOCATION("Location");

    @NotNull
    public static Kind from(@NotNull EnergyProfiler.EnergyEvent event) {
      switch (event.getMetadataCase()) {
        case WAKE_LOCK_ACQUIRED:
        case WAKE_LOCK_RELEASED:
          return WAKE_LOCK;

        case ALARM_SET:
        case ALARM_FIRED:
        case ALARM_CANCELLED:
          return ALARM;

        case JOB_SCHEDULED:
        case JOB_STARTED:
        case JOB_STOPPED:
        case JOB_FINISHED:
          return JOB;

        case LOCATION_UPDATE_REQUESTED:
        case LOCATION_UPDATE_REMOVED:
        case LOCATION_CHANGED:
          return LOCATION;

        default:
          getLogger().warn("Unsupported Kind for " + event.getMetadataCase().name());
          return UNKNOWN;
      }
    }

    @NotNull private final String myDisplayName;

    Kind(@NotNull String displayName) {
      myDisplayName = displayName;
    }

    @NotNull
    public String getDisplayName() {
      return myDisplayName;
    }
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

  /**
   * Returns the duration event critical data as a header string.
   *
   * <li>
   *   <ul>WAKE_LOCK returns the wake lock level, for example "Wake lock: PARTIAL".</ul>
   *   <ul>ALARM returns the alarm type, for example "Alarm: RTC_WAKEUP".</ul>
   *   <ul>LOCATION returns "Location: Request".</ul>
   *   <ul>Others returns the duration kind, for example, "Job".</ul>
   * </li>
   */
  @NotNull
  public String getName() {
    Kind kind = getKind();
    String namePart = "";
    switch (kind) {
      case WAKE_LOCK:
        if (getEventList().get(0).hasWakeLockAcquired()) {
          namePart = getWakeLockLevelName(getEventList().get(0).getWakeLockAcquired().getLevel());
        }
        break;
      case ALARM:
        if (getEventList().get(0).hasAlarmSet()) {
          namePart = getAlarmTypeName(getEventList().get(0).getAlarmSet().getType());
        }
        break;
      case LOCATION:
        namePart = "Request";
        break;
      default:
        break;
    }
    return !namePart.isEmpty() ? String.format("%s: %s", kind.getDisplayName(), namePart) : kind.getDisplayName();
  }

  /**
   * Returns the duration description string.
   *
   * <li>
   *   <ul>WAKE_LOCK returns its tag, that could be empty string.</ul>
   *   <ul>ALARM returns either the creator package from PendingIntent, or the listener tag.</ul>
   *   <ul>JOB returns the job id and job service name.</ul>
   *   <ul>Others returns "n/a".</ul>
   * </li>
   */
  @NotNull
  public String getDescription() {
    switch (getKind()) {
      case WAKE_LOCK:
        if (getEventList().get(0).hasWakeLockAcquired()) {
          return getEventList().get(0).getWakeLockAcquired().getTag();
        }
        break;
      case ALARM:
        if (getEventList().get(0).hasAlarmSet()) {
          EnergyProfiler.AlarmSet alarmSet = getEventList().get(0).getAlarmSet();
          return alarmSet.hasOperation() ? alarmSet.getOperation().getCreatorPackage()
                                         : alarmSet.hasListener() ? alarmSet.getListener().getTag() : "";
        }
        break;
      case JOB:
        if (getEventList().get(0).hasJobScheduled()) {
          EnergyProfiler.JobInfo job = getEventList().get(0).getJobScheduled().getJob();
          return String.format("%d:%s", job.getJobId(), job.getServiceName());
        }
        break;
      default:
        break;
    }
    return "n/a";
  }

  @NotNull Kind getKind() {
    return Kind.from(myEventList.get(0));
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

  @NotNull
  public static String getWakeLockLevelName(@NotNull EnergyProfiler.WakeLockAcquired.Level level) {
    switch (level) {
      case FULL_WAKE_LOCK:
        return "Full";
      case PARTIAL_WAKE_LOCK:
        return "Partial";
      case SCREEN_DIM_WAKE_LOCK:
        return "Screen Dim";
      case SCREEN_BRIGHT_WAKE_LOCK:
        return "Screen Bright";
      case PROXIMITY_SCREEN_OFF_WAKE_LOCK:
        return "Proximity Screen Off";
      default:
        return "n/a";
    }
  }

  @NotNull
  public static String getAlarmTypeName(@NotNull EnergyProfiler.AlarmSet.Type type) {
    switch (type) {
      case RTC:
        return "RTC";
      case RTC_WAKEUP:
        return "RTC Wakeup";
      case ELAPSED_REALTIME:
        return "Elapsed Realtime";
      case ELAPSED_REALTIME_WAKEUP:
        return "Elapsed Realtime Wakeup";
      default:
        return "n/a";
    }
  }
}
