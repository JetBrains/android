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
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.StudioProfilers;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public class DetailedEnergyEventsCount {

  @NotNull private final RangedContinuousSeries myLocationCountSeries;
  @NotNull private final RangedContinuousSeries myWakeLockCountSeries;
  @NotNull private final RangedContinuousSeries myAlarmAndJobCountSeries;

  public DetailedEnergyEventsCount(@NotNull StudioProfilers profilers) {
    Range countRange = new Range(0, EnergyMonitor.MAX_EXPECTED_USAGE);
    myLocationCountSeries = new RangedContinuousSeries("Location", profilers.getTimeline().getViewRange(), countRange,
                                                       createEventsCountSeries(profilers, kind -> kind == EnergyDuration.Kind.LOCATION));
    myWakeLockCountSeries = new RangedContinuousSeries("Wake Locks", profilers.getTimeline().getViewRange(), countRange,
                                                       createEventsCountSeries(profilers, kind -> kind == EnergyDuration.Kind.WAKE_LOCK));
    myAlarmAndJobCountSeries = new RangedContinuousSeries("Alarms & Jobs", profilers.getTimeline().getViewRange(), countRange,
                                                          createEventsCountSeries(profilers, kind -> kind == EnergyDuration.Kind.ALARM ||
                                                                                                     kind == EnergyDuration.Kind.JOB));
  }

  @NotNull
  public RangedContinuousSeries getLocationCountSeries() {
    return myLocationCountSeries;
  }

  @NotNull
  public RangedContinuousSeries getWakeLockCountSeries() {
    return myWakeLockCountSeries;
  }

  @NotNull
  public RangedContinuousSeries getAlarmAndJobCountSeries() {
    return myAlarmAndJobCountSeries;
  }

  @NotNull
  private static DataSeries<Long> createEventsCountSeries(StudioProfilers profilers, Predicate<EnergyDuration.Kind> kindFilter) {
    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      return new EnergyEventsCountDataSeries(
        profilers.getClient().getTransportClient(),
        profilers.getSession().getStreamId(),
        profilers.getSession().getPid(),
        kindFilter);
    }
    return new LegacyEnergyEventsCountDataSeries(
      new RangedSeries<>(profilers.getTimeline().getDataRange(),
                         new LegacyEnergyEventsDataSeries(profilers.getClient(), profilers.getSession())),
      kindFilter);
  }
}
