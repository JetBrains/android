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
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profiler.proto.Common;
import com.google.common.annotations.VisibleForTesting;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyEventsTableTooltipInfoModel extends AspectModel<EnergyEventsTableTooltipInfoModel.Aspect> {

  public enum Aspect {
    EVENT,
  }

  @NotNull final private Range myGlobalRange;
  final private long mySystemTimeDelta;

  @Nullable private EnergyDuration myDuration;
  @Nullable private Common.Event myCurrentSelectedEvent;

  @VisibleForTesting
  EnergyEventsTableTooltipInfoModel(@NotNull Range globalRange, long systemTimeDelta) {
    myGlobalRange = globalRange;
    mySystemTimeDelta = systemTimeDelta;
  }

  public EnergyEventsTableTooltipInfoModel(@NotNull Range globalRange) {
    this(globalRange, TimeUnit.MICROSECONDS.toMillis((long)globalRange.getMax()) - System.currentTimeMillis());
  }

  public void update(@NotNull EnergyDuration duration, @NotNull Range range) {
    // Reset duration if it does not contains range
    Common.Event firstEvent = duration.getEventList().get(0);
    Common.Event lastEvent = duration.getEventList().get(duration.getEventList().size() - 1);
    if (range.getMax() < TimeUnit.NANOSECONDS.toMicros(firstEvent.getTimestamp()) ||
        (lastEvent.getIsEnded() && range.getMin() > TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp()))) {
      duration = null;
    }

    // Find the event inside range.
    Common.Event newSelectedEvent = null;
    if (duration != null) {
      for (Common.Event event : duration.getEventList()) {
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
  public Common.Event getCurrentSelectedEvent() {
    return myCurrentSelectedEvent;
  }

  @Nullable
  public String getStatusString() {
    return myCurrentSelectedEvent == null
           ? "Active"
           : EnergyDuration.getMetadataName(myCurrentSelectedEvent.getEnergyEvent().getMetadataCase());
  }

  public String getDateFormattedString(long timestampMs) {
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    return timeFormat.format(new Date(timestampMs - mySystemTimeDelta));
  }

  public String getSimplifiedClockFormattedString(long timestampUs) {
    return TimeFormatter.getSimplifiedClockString(timestampUs - (long)myGlobalRange.getMin());
  }

  public String getRangeString() {
    if (myDuration == null) {
      return null;
    }
    Common.Event firstEvent = myDuration.getEventList().get(0);
    Common.Event lastEvent = myDuration.getEventList().get(myDuration.getEventList().size() - 1);
    String startTime = getSimplifiedClockFormattedString(TimeUnit.NANOSECONDS.toMicros(firstEvent.getTimestamp()));
    String unknownString;
    switch (myDuration.getKind()) {
      case WAKE_LOCK:
        unknownString = "Unreleased";
        break;
      case JOB:
        unknownString = "Unfinished";
        break;
      default:
        unknownString = "Alive";
    }

    String endTime =
      lastEvent.getIsEnded()
      ? getSimplifiedClockFormattedString(TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp()))
      : unknownString;
    return startTime + " - " + endTime;
  }

  public String getDurationString() {
    if (myDuration == null) {
      return null;
    }
    Common.Event firstEvent = myDuration.getEventList().get(0);
    Common.Event lastEvent = myDuration.getEventList().get(myDuration.getEventList().size() - 1);
    if (!lastEvent.getIsEnded()) {
      return null;
    }
    return TimeFormatter.getSingleUnitDurationString(
      TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp() - firstEvent.getTimestamp()));
  }
}
