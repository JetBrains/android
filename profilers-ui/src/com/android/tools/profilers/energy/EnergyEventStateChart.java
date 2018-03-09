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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import static com.android.tools.profilers.ProfilerColors.*;

public final class EnergyEventStateChart {
  /**
   * Class responsible for rendering one or more sequential network requests, with each request appearing as a horizontal
   * bar where each stage of its lifetime (sending, receiving, etc.) is highlighted with unique colors.
   */
  private static final EnumColors<EnergyDuration.Kind> DURATION_STATE_ENUM_COLORS = new EnumColors.Builder<EnergyDuration.Kind>(1)
    .add(EnergyDuration.Kind.ALARM, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.JOB, ENERGY_BACKGROUND)
    .add(EnergyDuration.Kind.WAKE_LOCK, ENERGY_WAKE_LOCK)
    .add(EnergyDuration.Kind.UNKNOWN, TRANSPARENT_COLOR)
    .build();
  /**
   * In energy events table, the timeline state chart color looks transparent, we highlight the start of each event, which works by taking
   * the base {@link DURATION_STATE_NUM_COLORS} color and applying some transparency to it.
   */
  private static final float COLOR_ALPHA = 0.25f;

  @NotNull
  public static StateChart<EnergyEvent> create(@NotNull EnergyDuration duration, @NotNull Range range) {
    DefaultDataSeries<EnergyEvent> series = new DefaultDataSeries<>();
    duration.getEventList().forEach(evt -> series.add(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()), evt));

    StateChartModel<EnergyEvent> model = new StateChartModel<>();
    model.addSeries(new RangedSeries<>(range, series));

    Color highlightColor = DURATION_STATE_ENUM_COLORS.getColor(duration.getKind());
    Color stateChartColor = AdtUiUtils.overlayColor(DEFAULT_BACKGROUND.getRGB(), highlightColor.getRGB(), COLOR_ALPHA);
    StateChart<EnergyEvent> stateChart = new StateChart<>(model, evt -> !evt.getIsTerminal() ? stateChartColor : TRANSPARENT_COLOR);
    stateChart.setBarHighlightColor(highlightColor);
    return stateChart;
  }

  @NotNull
  public static StateChart<EnergyEvent> create(@NotNull StateChartModel<EnergyEvent> model) {
    StateChart<EnergyEvent> chart = new StateChart<>(model, evt -> !evt.getIsTerminal()
                                                                   ? DURATION_STATE_ENUM_COLORS.getColor(EnergyDuration.Kind.from(evt))
                                                                   : TRANSPARENT_COLOR);
    chart.detach(); // No mouse handling, because we don't want the event bars to resize on mouse-over
    return chart;
  }
}
