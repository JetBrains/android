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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A data series to count how many energy events are merged into one.
 * <p>
 * For example, events like so:
 * <p>
 * <pre>
 *
 *    [========) <- terminal timestamp not included
 *        [===========)
 *                          [=====)
 *                               [==)
 *                                      [========)
 * </pre>
 * <p>
 * would be counted as
 * <p>
 * <pre>
 *   011112222211111110000001111121100001111111110
 * </pre>
 */
public final class EnergyEventsCountDataSeries implements DataSeries<Long> {

  @NotNull private final RangedSeries<Common.Event> myDelegateSeries;

  private final List<EnergyDuration.Kind> myKindsFilter;

  /**
   * @param delegateSeries A source series whose events will be read from and then counted
   * @param kindsFilter    A list of one or more event kinds to merge into a single bar
   */
  public EnergyEventsCountDataSeries(@NotNull RangedSeries<Common.Event> delegateSeries,
                                     @NotNull EnergyDuration.Kind... kindsFilter) {
    myDelegateSeries = delegateSeries;
    myKindsFilter = Arrays.asList(kindsFilter);
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range xRange) {
    List<SeriesData<Common.Event>> sourceData = myDelegateSeries.getSeries();
    long position = (long)xRange.getMax();
    Set<Long> activeEventGroups = new HashSet<>();

    for (SeriesData<Common.Event> eventData : sourceData) {
      if (!myKindsFilter.contains(EnergyDuration.Kind.from(eventData.value.getEnergyEvent()))) {
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
    return Arrays.asList(new SeriesData(position, Long.valueOf(activeEventGroups.size())));
  }

  public List<EnergyDuration.Kind> getKindsFilter() {
    return myKindsFilter;
  }
}
