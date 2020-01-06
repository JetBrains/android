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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Energy;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulate a sequence of related energy events into a single class.
 *
 * This can be useful for reading through a stream of intermingled energy events (multiple wakelock
 * events, alarm events, etc.) and separating them into their own individual sequences. Use
 * {@link #groupById(List)} to accomplish this.
 */
public final class EnergyDuration implements Comparable<EnergyDuration> {

  public enum Kind {
    UNKNOWN ("N/A"),
    WAKE_LOCK ("Wake Lock"),
    ALARM("Alarm"),
    JOB("Job"),
    LOCATION("Location");

    @NotNull
    public static Kind from(@NotNull Energy.EnergyEventData event) {
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
  @NotNull private final ImmutableList<Common.Event> myEventList;

  public EnergyDuration(@NotNull List<Common.Event> eventList) {
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
        if (getEventList().get(0).getEnergyEvent().hasWakeLockAcquired()) {
          namePart = getWakeLockLevelName(getEventList().get(0).getEnergyEvent().getWakeLockAcquired().getLevel());
        }
        break;
      case ALARM:
        if (getEventList().get(0).getEnergyEvent().hasAlarmSet()) {
          namePart = getAlarmTypeName(getEventList().get(0).getEnergyEvent().getAlarmSet().getType());
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
   *   <ul>Others returns "N/A".</ul>
   * </li>
   */
  @NotNull
  public String getDescription() {
    switch (getKind()) {
      case WAKE_LOCK:
        if (getEventList().get(0).getEnergyEvent().hasWakeLockAcquired()) {
          return getEventList().get(0).getEnergyEvent().getWakeLockAcquired().getTag();
        }
        break;
      case ALARM:
        if (getEventList().get(0).getEnergyEvent().hasAlarmSet()) {
          Energy.AlarmSet alarmSet = getEventList().get(0).getEnergyEvent().getAlarmSet();
          return alarmSet.hasOperation() ? alarmSet.getOperation().getCreatorPackage()
                                         : alarmSet.hasListener() ? alarmSet.getListener().getTag() : "";
        }
        break;
      case JOB:
        if (getEventList().get(0).getEnergyEvent().hasJobScheduled()) {
          Energy.JobInfo job = getEventList().get(0).getEnergyEvent().getJobScheduled().getJob();
          return String.format("%d:%s", job.getJobId(), job.getServiceName());
        }
        break;
      default:
        break;
    }
    return "N/A";
  }

  @NotNull
  public Kind getKind() {
    return Kind.from(myEventList.get(0).getEnergyEvent());
  }

  @NotNull
  public ImmutableList<Common.Event> getEventList() {
    return myEventList;
  }

  /**
   * Events are grouped by its unique ID which indicates those events sharing a duration. Assuming the only events without an ID are
   * energy sample data and put them in one list. For example, events are grouped to 3 durations {[1000ms, metadata], [[1200ms, metadata],
   * [1300ms, metadata]], [[1400ms, metadata], [1600ms, metadata]]}. First duration includes single event at 1000ms, second duration has
   * two events at 1200ms and 1300ms, third duration includes two events at 1400ms and 1600ms.
   */
  @NotNull
  public static List<EnergyDuration> groupById(@NotNull List<Common.Event> events) {
    Map<Long, List<Common.Event>> durationMap = new LinkedHashMap<>();
    for (Common.Event event : events) {
      if (durationMap.containsKey(event.getGroupId())) {
        durationMap.get(event.getGroupId()).add(event);
      }
      else {
        List<Common.Event> list = new ArrayList<>();
        list.add(event);
        durationMap.put(event.getGroupId(), list);
      }
    }
    return ContainerUtil.map(durationMap.values(), list -> new EnergyDuration(list));
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(EnergyDuration.class);
  }

  @NotNull
  public static String getWakeLockLevelName(@NotNull Energy.WakeLockAcquired.Level level) {
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
        return "N/A";
    }
  }

  @NotNull
  public static String getAlarmTypeName(@NotNull Energy.AlarmSet.Type type) {
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
        return "N/A";
    }
  }

  @NotNull
  public static String getMetadataName(Energy.EnergyEventData.MetadataCase metadataCase) {
    switch (metadataCase) {
      case WAKE_LOCK_ACQUIRED:
        return "Acquired";
      case WAKE_LOCK_RELEASED:
        return "Released";
      case ALARM_SET:
        return "Set";
      case ALARM_FIRED:
        return "Triggered";
      case ALARM_CANCELLED:
        return "Cancelled";
      case JOB_SCHEDULED:
        return "Scheduled";
      case JOB_STARTED:
        return "Started";
      case JOB_STOPPED:
        return "Stopped";
      case JOB_FINISHED:
        return "Finished";
      case LOCATION_UPDATE_REQUESTED:
        return "Requested";
      case LOCATION_CHANGED:
        return "Location Updated";
      case LOCATION_UPDATE_REMOVED:
        return "Request Removed";
      default:
        return "";
    }
  }

  /**
   * The raw callstack data looks like "package.Class.method(File: Line number)", so we truncate the data before the line metadata
   * in the first line, for example, "com.AlarmManager.method(Class line: 50)" results in "com.AlarmManager.method".
   *
   * @return the first non-empty callstack from the events list, if absent returns empty string.
   */
  @NotNull
  public String getCalledBy() {
    if (myCalledBy == null) {
      myCalledBy = getEventList().stream()
        .filter(e -> !e.getEnergyEvent().getCallstack().isEmpty())
        .map(e -> e.getEnergyEvent().getCallstack().split("\\(", 2))
        .filter(splitResult -> splitResult.length > 0)
        .map(splitResult -> splitResult[0])
        .findFirst()
        .orElse("");
    }
    return myCalledBy;
  }

  /**
   * Caches the called-by string to save finding it in the event list.
   */
  private String myCalledBy = null;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EnergyDuration duration = (EnergyDuration)o;
    return myEventList.equals(duration.myEventList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myEventList);
  }
}
