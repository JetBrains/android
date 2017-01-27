/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.profilers.ProfilerColors.*;

/**
 * A Swing component which renders a single network request as a horizontal bar where each stage of its lifetime (sending, receiving, etc.)
 * is highlighted with unique colors.
 */
public class RequestTimeline {

  @NotNull
  private final StateChart<NetworkState> myChart;
  @NotNull
  private final EnumColors<NetworkState> myColors = new EnumColors.Builder<NetworkState>(2)
    .add(NetworkState.SENDING, NETWORK_SENDING_COLOR, NETWORK_SENDING_COLOR)
    .add(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR, NETWORK_RECEIVING_SELECTED_COLOR)
    .add(NetworkState.WAITING, NETWORK_WAITING_COLOR, NETWORK_WAITING_COLOR)
    .add(NetworkState.NONE, TRANSPARENT_COLOR, TRANSPARENT_COLOR)
    .build();

  public RequestTimeline(@NotNull HttpData httpData, @NotNull Range range) {
    myChart = createChart(httpData, range);
  }

  @NotNull
  public EnumColors<NetworkState> getColors() {
    return myColors;
  }

  public void setHeightGap(float gap) {
    myChart.setHeightGap(gap);
  }

  @NotNull
  public JComponent getComponent() {
    return myChart;
  }

  @NotNull
  private StateChart<NetworkState> createChart(@NotNull HttpData httpData, @NotNull Range range) {
    DefaultDataSeries<NetworkState> series = new DefaultDataSeries<>();
    series.add(0, NetworkState.NONE);
    series.add(httpData.getStartTimeUs(), NetworkState.SENDING);
    if (httpData.getDownloadingTimeUs() > 0) {
      series.add(httpData.getDownloadingTimeUs(), NetworkState.RECEIVING);
    }
    if (httpData.getEndTimeUs() > 0) {
      series.add(httpData.getEndTimeUs(), NetworkState.NONE);
    }

    StateChartModel<NetworkState> stateModel = new StateChartModel<>();
    StateChart<NetworkState> chart = new StateChart<>(stateModel, myColors);
    stateModel.addSeries(new RangedSeries<>(range, series));
    return chart;
  }
}
