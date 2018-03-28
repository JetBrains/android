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
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import static com.android.tools.profilers.ProfilerColors.*;

public final class EnergyEventStateChart {
  /**
   * Class responsible for rendering one or more sequential network requests, with each request appearing as a horizontal
   * bar where each stage of its lifetime (sending, receiving, etc.) is highlighted with unique colors.
   */
  static final EnumColors<EnergyDuration.Kind> DURATION_STATE_ENUM_COLORS = new EnumColors.Builder<EnergyDuration.Kind>(1)
    .add(EnergyDuration.Kind.ALARM, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.JOB, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.WAKE_LOCK, ENERGY_WAKE_LOCK)
    .add(EnergyDuration.Kind.LOCATION, ENERGY_LOCATION)
    .add(EnergyDuration.Kind.UNKNOWN, TRANSPARENT_COLOR)
    .build();

  @NotNull
  public static StateChart<EnergyEvent> create(@NotNull EnergyDuration duration, @NotNull Range range) {
    DefaultDataSeries<EnergyEvent> series = new DefaultDataSeries<>();
    duration.getEventList().forEach(evt -> series.add(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()), evt));

    StateChartModel<EnergyEvent> model = new StateChartModel<>();
    model.addSeries(new RangedSeries<>(range, series));

    return create(model);
  }

  @NotNull
  public static StateChart<EnergyEvent> create(@NotNull StateChartModel<EnergyEvent> model) {
    return new StateChart<>(model, evt -> !evt.getIsTerminal()
                                          ? DURATION_STATE_ENUM_COLORS.getColor(EnergyDuration.Kind.from(evt))
                                          : TRANSPARENT_COLOR);
  }
}
