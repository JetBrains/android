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
import com.android.tools.profilers.network.httpdata.HttpData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.profilers.ProfilerColors.*;
import static com.android.tools.profilers.ProfilerColors.NETWORK_WAITING_COLOR;
import static com.android.tools.profilers.ProfilerColors.TRANSPARENT_COLOR;

/**
 * Class responsible for rendering one or more sequential network requests, with each request appearing as a horizontal
 * bar where each stage of its lifetime (sending, receiving, etc.) is highlighted with unique colors.
 */
class ConnectionsStateChart {
  @NotNull private final EnumColors<NetworkState> myColors = new EnumColors.Builder<NetworkState>(2)
    .add(NetworkState.SENDING, NETWORK_SENDING_COLOR, NETWORK_SENDING_COLOR)
    .add(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR, NETWORK_RECEIVING_SELECTED_COLOR)
    .add(NetworkState.WAITING, NETWORK_WAITING_COLOR, NETWORK_WAITING_COLOR)
    .add(NetworkState.NONE, TRANSPARENT_COLOR, TRANSPARENT_COLOR)
    .build();

  @NotNull private final StateChart<NetworkState> myChart;

  ConnectionsStateChart(@NotNull List<HttpData> dataList, @NotNull Range range) {
    myChart = createChart(dataList, range);
  }

  ConnectionsStateChart(@NotNull HttpData data, @NotNull Range range) {
    this(Collections.singletonList(data), range);
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
  private StateChart<NetworkState> createChart(@NotNull Collection<HttpData> dataList, @NotNull Range range) {
    DefaultDataSeries<NetworkState> series = new DefaultDataSeries<>();
    series.add(0, NetworkState.NONE);
    for (HttpData data : dataList) {
      if (data.getEndTimeUs() == 0) {
        continue;
      }

      series.add(data.getStartTimeUs(), NetworkState.SENDING);
      if (data.getDownloadingTimeUs() > 0) {
        series.add(data.getDownloadingTimeUs(), NetworkState.RECEIVING);
      }
      series.add(data.getEndTimeUs(), NetworkState.NONE);
    }
    StateChartModel<NetworkState> stateModel = new StateChartModel<>();
    StateChart<NetworkState> chart = new StateChart<>(stateModel, myColors);
    stateModel.addSeries(new RangedSeries<>(range, series));
    return chart;
  }
}
