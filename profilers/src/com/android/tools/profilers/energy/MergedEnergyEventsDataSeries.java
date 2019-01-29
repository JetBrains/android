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
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A data series where multiple, separate series are merged into one.
 *
 * For example, events like so:
 *
 * <pre>
 *    [========]
 *        [===========]
 *                          [=====]
 *                               [==]
 *                                      [========]
 * </pre>
 *
 * would be collapsed into:
 *
 * <pre>
 *    [===============]     [=======]   [========]
 * </pre>
 */
public final class MergedEnergyEventsDataSeries implements DataSeries<EnergyEvent> {

  @NotNull private final EnergyEventsDataSeries myDelegateSeries;
  private final List<EnergyDuration.Kind> myKindsFilter;

  /**
   * @param delegateSeries A source series whose events will be read from and then merged
   * @param kindsFilter A list of one or more event kinds to merge into a single bar
   */
  public MergedEnergyEventsDataSeries(@NotNull EnergyEventsDataSeries delegateSeries, @NotNull EnergyDuration.Kind... kindsFilter) {
    myDelegateSeries = delegateSeries;
    myKindsFilter = Arrays.asList(kindsFilter);
  }

  @Override
  public List<SeriesData<EnergyEvent>> getDataForXRange(Range xRange) {
    List<SeriesData<EnergyEvent>> sourceData = myDelegateSeries.getDataForXRange(xRange);
    List<SeriesData<EnergyEvent>> destData = new ArrayList<>();
    Set<Integer> activeEventGroups = new HashSet<>();

    for (SeriesData<EnergyEvent> eventData : sourceData) {
      if (!myKindsFilter.contains(EnergyDuration.Kind.from(eventData.value))) {
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
      if (!eventData.value.getIsTerminal()) {
        if (activeEventGroups.isEmpty()) {
          destData.add(eventData);
        }
        activeEventGroups.add(eventData.value.getEventId());
      }
      else {
        activeEventGroups.remove(eventData.value.getEventId());
        if (activeEventGroups.isEmpty()) {
          destData.add(eventData);
        }
      }
    }
    return destData;
  }
}
