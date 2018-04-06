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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.EnergyProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class EnergyEventsTableTooltipInfoModel extends AspectModel<EnergyEventsTableTooltipInfoModel.Aspect> {

  public enum Aspect {
    EVENT,
  }

  @NotNull final private Range myGlobalRange;
  @NotNull final private TimeAxisFormatter myFormatter = new TimeAxisFormatter(1, 4, 1);
  final private long systemTimeDelta;

  @Nullable private EnergyDuration myDuration;
  @Nullable private EnergyProfiler.EnergyEvent myCurrentSelectedEvent;

  public EnergyEventsTableTooltipInfoModel(@NotNull Range globalRange) {
    myGlobalRange = globalRange;
    systemTimeDelta = TimeUnit.MICROSECONDS.toMillis((long)myGlobalRange.getMax()) - System.currentTimeMillis();
  }

  public void update(@NotNull EnergyDuration duration, @NotNull Range range) {
    // Reset duration if it does not contains range
    EnergyProfiler.EnergyEvent firstEvent = duration.getEventList().get(0);
    EnergyProfiler.EnergyEvent lastEvent = duration.getEventList().get(duration.getEventList().size() - 1);
    if (range.getMax() < TimeUnit.NANOSECONDS.toMicros(firstEvent.getTimestamp()) ||
        (lastEvent.getIsTerminal() && range.getMin() > TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp()))) {
      duration = null;
    }

    // Find the event inside range.
    EnergyProfiler.EnergyEvent newSelectedEvent = null;
    if (duration != null) {
      for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
        long minTimestamp = TimeUnit.MICROSECONDS.toNanos((long)range.getMin());
        long maxTimestamp = TimeUnit.MICROSECONDS.toNanos((long)range.getMax());
        if (event.getTimestamp() >= minTimestamp && event.getTimestamp() <= maxTimestamp) {
          newSelectedEvent = event;
          break;
        }
      }
    }

    // Update myDuration and myCurrentSelectedEvent.
    if (duration != myDuration || newSelectedEvent != myCurrentSelectedEvent) {
      myDuration = duration;
      myCurrentSelectedEvent = newSelectedEvent;
      changed(Aspect.EVENT);
    }
  }

  @Nullable
  public EnergyDuration getDuration() {
    return myDuration;
  }

  @Nullable
  public EnergyProfiler.EnergyEvent getCurrentSelectedEvent() {
    return myCurrentSelectedEvent;
  }

  @Nullable
  public String getStatusString() {
    if (myCurrentSelectedEvent == null) {
      return "Active";
    }
    switch (myCurrentSelectedEvent.getMetadataCase()) {
      case WAKE_LOCK_ACQUIRED:
        return "Acquired";
      case WAKE_LOCK_RELEASED:
        return "Released";
      case JOB_SCHEDULED:
        return "Scheduled";
      case JOB_STARTED:
        return "Started";
      case JOB_STOPPED:
        return "Stopped";
      case JOB_FINISHED:
        return "Finished";
      default:
        return null;
    }
  }

  public String getDateFormattedString(long timestampMs) {
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    return timeFormat.format(new Date(timestampMs - systemTimeDelta));
  }

  public String getFormattedString(double timestampUs) {
    return myFormatter.getClockFormattedString((long)(timestampUs - myGlobalRange.getMin()));
  }

  public String getFormattedDuration(long timestampUs) {
    return myFormatter.getFormattedDuration(timestampUs);
  }

  public String getRangeString() {
    if (myDuration == null) {
      return null;
    }
    EnergyProfiler.EnergyEvent firstEvent = myDuration.getEventList().get(0);
    EnergyProfiler.EnergyEvent lastEvent = myDuration.getEventList().get(myDuration.getEventList().size() - 1);
    String startTime = getFormattedString(TimeUnit.NANOSECONDS.toMicros(firstEvent.getTimestamp()));
    String endTime = lastEvent.getIsTerminal() ? getFormattedString(TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp())) : "unknown";
    return startTime + " - " + endTime;
  }

  public String getDurationString() {
    if (myDuration == null) {
      return null;
    }
    EnergyProfiler.EnergyEvent firstEvent = myDuration.getEventList().get(0);
    EnergyProfiler.EnergyEvent lastEvent = myDuration.getEventList().get(myDuration.getEventList().size() - 1);
    if (!lastEvent.getIsTerminal()) {
      return null;
    }
    return getFormattedDuration(TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp() - firstEvent.getTimestamp()));
  }
}
