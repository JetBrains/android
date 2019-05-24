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

import static com.android.tools.profilers.ProfilerColors.ENERGY_BACKGROUND;
import static com.android.tools.profilers.ProfilerColors.ENERGY_LOCATION;
import static com.android.tools.profilers.ProfilerColors.ENERGY_WAKE_LOCK;
import static com.android.tools.profilers.ProfilerColors.TRANSPARENT_COLOR;

import com.android.tools.adtui.chart.statechart.DefaultStateChartReducer;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.chart.statechart.StateChartConfig;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profiler.proto.Common;
import java.awt.Color;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public final class EnergyEventStateChart {
  /**
   * Class responsible for rendering one or more sequential energy events, with each event appearing as a horizontal
   * bar in its unique color.
   */
  static final EnumColors<EnergyDuration.Kind> DURATION_STATE_ENUM_COLORS = new EnumColors.Builder<EnergyDuration.Kind>(1)
    .add(EnergyDuration.Kind.ALARM, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.JOB, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.WAKE_LOCK, ENERGY_WAKE_LOCK)
    .add(EnergyDuration.Kind.LOCATION, ENERGY_LOCATION)
    .add(EnergyDuration.Kind.UNKNOWN, TRANSPARENT_COLOR)
    .build();

  private static final StateChartColorProvider<Common.Event> DURATION_STATE_COLOR_PROVIDER = new StateChartColorProvider<Common.Event>() {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull Common.Event value) {
      if (value.getIsEnded()) {
        return TRANSPARENT_COLOR;
      }
      return DURATION_STATE_ENUM_COLORS.getColor(EnergyDuration.Kind.from(value.getEnergyEvent()));
    }
  };

  @NotNull
  public static StateChart<Common.Event> create(@NotNull EnergyDuration duration, @NotNull Range range) {
    DefaultDataSeries<Common.Event> series = new DefaultDataSeries<>();
    duration.getEventList().forEach(evt -> series.add(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()), evt));

    StateChartModel<Common.Event> model = new StateChartModel<>();
    model.addSeries(new RangedSeries<>(range, series));

    return create(model);
  }

  @NotNull
  public static StateChart<Common.Event> create(@NotNull StateChartModel<Common.Event> model) {
    return new StateChart<>(model, new StateChartConfig<>(new DefaultStateChartReducer<>(), 1, 1, 0.33f), DURATION_STATE_COLOR_PROVIDER);
  }
}
