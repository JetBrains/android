/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.chart.statechart.DefaultStateChartReducer;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.chart.statechart.StateChartConfig;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.formatter.UserCounterAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.google.common.annotations.VisibleForTesting;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Class responsible for rendering the event count for Custom Event Visualization. The event count appears as a horizontal
 * bar in its unique color corresponding to how many events are happening relative to the total number of events a user is tracking.
 */
public final class UserCounterStateChartFactory {

  private static final StateChartColorProvider<Long> DURATION_STATE_COLOR_PROVIDER = new StateChartColorProvider<Long>() {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull Long value) {
      // Consistency across the legend and state chart colors for the value's conversion into none, light, med, or dark
      String weight = UserCounterAxisFormatter.DEFAULT.getFormattedString(0, value, false);

      switch (weight) {
        case "None":
          return ProfilerColors.USER_COUNTER_EVENT_NONE;
        case "Light":
          return ProfilerColors.USER_COUNTER_EVENT_LIGHT;
        case "Medium":
          return ProfilerColors.USER_COUNTER_EVENT_MED;
        default:
          return ProfilerColors.USER_COUNTER_EVENT_DARK;
      }
    }
  };

  @NotNull
  public static StateChart<Long> create(@NotNull StateChartModel<Long> model) {
    return new StateChart<>(model, DURATION_STATE_COLOR_PROVIDER, Object::toString,
                            new StateChartConfig<>(new DefaultStateChartReducer<>(), 0.33, 1, 0.33f));
  }

  @VisibleForTesting
  static StateChartColorProvider<Long> getDurationStateColorProvider() {
    return DURATION_STATE_COLOR_PROVIDER;
  }
}
