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
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy version of {@link EnergyEventsCountDataSeries}.
 */
public final class LegacyEnergyEventsCountDataSeries implements DataSeries<Long> {

  @NotNull private final RangedSeries<Common.Event> myDelegateSeries;

  @NotNull private final Predicate<EnergyDuration.Kind> myKindsFilter;

  /**
   * @param delegateSeries A source series whose events will be read from and then counted
   * @param kindFilter     Only count event for which the kind predicate evaluates to true
   */
  public LegacyEnergyEventsCountDataSeries(@NotNull RangedSeries<Common.Event> delegateSeries,
                                           @NotNull Predicate<EnergyDuration.Kind> kindFilter) {
    myDelegateSeries = delegateSeries;
    myKindsFilter = kindFilter;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range xRange) {
    List<SeriesData<Common.Event>> sourceData = myDelegateSeries.getSeries();
    long position = (long)xRange.getMax();
    Set<Long> activeEventGroups = new HashSet<>();

    for (SeriesData<Common.Event> eventData : sourceData) {
      if (!myKindsFilter.test(EnergyDuration.Kind.from(eventData.value.getEnergyEvent()))) {
        continue;
      }
      if (eventData.x > position) {
        break;
      }
      if (!eventData.value.getIsEnded()) {
        activeEventGroups.add(eventData.value.getGroupId());
      }
      else {
        activeEventGroups.remove(eventData.value.getGroupId());
      }
    }
    return Collections.singletonList(new SeriesData<>(position, Long.valueOf(activeEventGroups.size())));
  }
}
