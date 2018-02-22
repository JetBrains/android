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

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profilers.ProfilerColors;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * State chart of a series energy event, i.e. duration.
 */
public final class DurationStateChart extends StateChart<MetadataCase> {

  private static final EnumColors<MetadataCase> DURATION_STATE_ENUM_COLORS = new EnumColors.Builder<MetadataCase>(1)
    .add(MetadataCase.WAKE_LOCK_ACQUIRED, ProfilerColors.ENERGY_WAKE_LOCK)
    .add(MetadataCase.WAKE_LOCK_RELEASED, ProfilerColors.TRANSPARENT_COLOR)
    .add(MetadataCase.ALARM_SET, ProfilerColors.ENERGY_ALARM)
    .add(MetadataCase.ALARM_CANCELLED, ProfilerColors.TRANSPARENT_COLOR)
    .add(MetadataCase.METADATA_NOT_SET, ProfilerColors.TRANSPARENT_COLOR)
    .build();

  public DurationStateChart(@NotNull EventDuration data, @NotNull Range range) {
    super(createChartModel(data, range), DURATION_STATE_ENUM_COLORS::getColor);
  }

  @NotNull
  private static StateChartModel<MetadataCase> createChartModel(@NotNull EventDuration data, @NotNull Range range) {
    DefaultDataSeries<MetadataCase> series = new DefaultDataSeries<>();
    series.add(0, MetadataCase.METADATA_NOT_SET);
    for (EnergyProfiler.EnergyEvent event : data.getEventList()) {
      series.add(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()), event.getMetadataCase());
    }

    StateChartModel<MetadataCase> stateModel = new StateChartModel<>();
    stateModel.addSeries(new RangedSeries<>(range, series));
    return stateModel;
  }
}
