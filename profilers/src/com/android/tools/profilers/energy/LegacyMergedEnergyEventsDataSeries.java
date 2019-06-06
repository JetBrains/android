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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy version of {@link MergedEnergyEventsDataSeries}.
 */
public final class LegacyMergedEnergyEventsDataSeries implements DataSeries<Common.Event> {

  @NotNull private final DataSeries<Common.Event> myDelegateSeries;
  private final List<EnergyDuration.Kind> myKindsFilter;

  /**
   * @param delegateSeries A source series whose events will be read from and then merged
   * @param kindsFilter    A list of one or more event kinds to merge into a single bar
   */
  public LegacyMergedEnergyEventsDataSeries(@NotNull DataSeries<Common.Event> delegateSeries, @NotNull EnergyDuration.Kind... kindsFilter) {
    myDelegateSeries = delegateSeries;
    myKindsFilter = Arrays.asList(kindsFilter);
  }

  @Override
  public List<SeriesData<Common.Event>> getDataForXRange(Range xRange) {
    List<SeriesData<Common.Event>> sourceData = myDelegateSeries.getDataForXRange(xRange);
    List<SeriesData<Common.Event>> destData = new ArrayList<>();
    Set<Long> activeEventGroups = new HashSet<>();

    for (SeriesData<Common.Event> eventData : sourceData) {
      if (!myKindsFilter.contains(EnergyDuration.Kind.from(eventData.value.getEnergyEvent()))) {
        continue;
      }

      // Here, we are going to combine separate event groups into one. We basically loop through
      // all events (which are in sorted order), and create new, fake event groups on the fly that
      // are a superset of those groups. We keep track of all active event groups (those that have
      // been started but not yet finished), so the superset group starts when we get our first
      // active event, and it ends when we get a terminal event while no other groups are active.
      //
      //   t0   t1   t2   t3   t4   t5
      //    [=========]                  <- Active t0 - t2
      //    |   [===============]        <- Active t1 - t4
      //    |             [=========]    <- Active t3 - t5
      //    |                       |
      //  start                    end
      if (!eventData.value.getIsEnded()) {
        if (activeEventGroups.isEmpty()) {
          destData.add(eventData);
        }
        activeEventGroups.add(eventData.value.getGroupId());
      }
      else {
        activeEventGroups.remove(eventData.value.getGroupId());
        if (activeEventGroups.isEmpty()) {
          destData.add(eventData);
        }
      }
    }
    return destData;
  }
}
