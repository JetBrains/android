/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.statechart.DefaultStateChartReducer;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartConfig;
import com.android.tools.adtui.model.legend.FixedLegend;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ProfilerColors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;

import static com.android.tools.profilers.ProfilerLayout.*;
import static com.android.tools.profilers.ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS;
import static com.android.tools.profilers.network.NetworkRadioDataSeries.RadioState;

public class NetworkRadioView {
  private static final String LABEL = "RADIO";
  private static final double STATE_CHART_HEIGHT_RATIO = .6;
  private static final double STATE_CHART_OVER_HEIGHT_RATIO = 1;
  private static final float STATE_CHART_OFFSET = .1f;

  private static final EnumMap<RadioState, Color> RADIO_STATE_COLOR = new EnumMap<>(RadioState.class);

  static {
    RADIO_STATE_COLOR.put(RadioState.NONE, ProfilerColors.DEFAULT_BACKGROUND);
    RADIO_STATE_COLOR.put(RadioState.WIFI, ProfilerColors.NETWORK_RADIO_WIFI);
    RADIO_STATE_COLOR.put(RadioState.HIGH, ProfilerColors.NETWORK_RADIO_HIGH);
    RADIO_STATE_COLOR.put(RadioState.LOW, ProfilerColors.NETWORK_RADIO_LOW);
  }

  @NotNull private final StateChart<RadioState> myRadioChart;

  @NotNull private final JComponent myComponent;

  public NetworkRadioView(@NotNull NetworkProfilerStageView stageView) {
    StateChartConfig config =
      new StateChartConfig(new DefaultStateChartReducer(), STATE_CHART_HEIGHT_RATIO, STATE_CHART_OVER_HEIGHT_RATIO, STATE_CHART_OFFSET);
    myRadioChart = new StateChart<>(stageView.getStage().getRadioState(), RADIO_STATE_COLOR, config);

    myComponent = new JPanel();
    myComponent.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myComponent.setBorder(MONITOR_BORDER);

    populateUI(myComponent);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  private void populateUI(@NotNull JComponent panel) {
    JLabel label = new JLabel(LABEL);
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    LegendComponentModel legendModel = new LegendComponentModel(LEGEND_UPDATE_FREQUENCY_MS);
    Legend wifiLegend = new FixedLegend(RadioState.WIFI.toString());
    Legend highLegend = new FixedLegend(RadioState.HIGH.toString());
    Legend lowLegend = new FixedLegend(RadioState.LOW.toString());
    legendModel.add(wifiLegend);
    legendModel.add(highLegend);
    legendModel.add(lowLegend);

    LegendComponent legend =
      new LegendComponent.Builder(legendModel).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(wifiLegend, new LegendConfig(LegendConfig.IconType.LINE, RADIO_STATE_COLOR.get(RadioState.WIFI)));
    legend.configure(highLegend, new LegendConfig(LegendConfig.IconType.LINE, RADIO_STATE_COLOR.get(RadioState.HIGH)));
    legend.configure(lowLegend, new LegendConfig(LegendConfig.IconType.LINE, RADIO_STATE_COLOR.get(RadioState.LOW)));
    legendModel.update(1);

    JPanel topPane = new JPanel(new BorderLayout());
    topPane.setOpaque(false);
    topPane.add(label, BorderLayout.WEST);
    topPane.add(legend, BorderLayout.EAST);

    panel.setLayout(new TabularLayout("*", "Fit,12px,8px"));
    panel.add(topPane, new TabularLayout.Constraint(0, 0));
    panel.add(myRadioChart, new TabularLayout.Constraint(1, 0));
  }
}
