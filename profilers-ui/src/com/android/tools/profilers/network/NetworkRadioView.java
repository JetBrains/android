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
import com.android.tools.adtui.LegendRenderData;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.network.NetworkRadioDataSeries.RadioState;

public class NetworkRadioView {
  private static final String LABEL = "Radio";
  private static final int MINIMUM_HEIGHT = JBUI.scale(45);

  private static final EnumMap<RadioState, Color> RADIO_STATE_COLOR = new EnumMap<>(RadioState.class);

  static {
    RADIO_STATE_COLOR.put(RadioState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    RADIO_STATE_COLOR.put(RadioState.WIFI, ProfilerColors.NETWORK_RADIO_WIFI);
    RADIO_STATE_COLOR.put(RadioState.HIGH, ProfilerColors.NETWORK_RADIO_HIGH);
    RADIO_STATE_COLOR.put(RadioState.LOW, ProfilerColors.NETWORK_RADIO_LOW);
    RADIO_STATE_COLOR.put(RadioState.IDLE, ProfilerColors.NETWORK_RADIO_IDLE);
  }

  @NotNull private final StateChart<RadioState> myRadioChart;

  @NotNull private final JComponent myComponent;

  public NetworkRadioView(@NotNull NetworkProfilerStageView stageView) {
    myRadioChart = new StateChart<>(RADIO_STATE_COLOR);
    myRadioChart.addSeries(new RangedSeries<>(stageView.getTimeline().getViewRange(), stageView.getStage().getRadioDataSeries()));
    myRadioChart.setHeightGap(0.4f);

    myComponent = new JPanel();
    myComponent.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myComponent.setMinimumSize(new Dimension(0, MINIMUM_HEIGHT));
    myComponent.setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    myComponent.setBorder(MONITOR_BORDER);

    populateUI(myComponent);

    stageView.getChoreographer().register(myRadioChart);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  private void populateUI(@NotNull JComponent panel) {
    JLabel label = new JLabel(LABEL);
    label.setVerticalAlignment(SwingConstants.TOP);

    LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, Integer.MAX_VALUE);
    legend.setLegendData(getLegendData());
    // As legend doesn't change over time, it doesn't need to be in {@code Choreographer}}, so draw it by animating once.
    legend.update(1);

    JPanel topPane = new JPanel(new BorderLayout());
    topPane.setOpaque(false);
    topPane.add(label, BorderLayout.WEST);
    topPane.add(legend, BorderLayout.EAST);

    panel.setLayout(new VerticalFlowLayout(true, true));
    panel.add(topPane);
    panel.add(myRadioChart);
  }

  @NotNull
  private static List<LegendRenderData> getLegendData() {
    List<LegendRenderData> legendData = new ArrayList<>();
    for (RadioState state : RadioState.values()) {
      if (state == RadioState.NONE) {
        continue;
      }
      else if (state == RadioState.LOW) {
        // TODO: perfd currently doesn't return this state. Investigate getting it to work or
        // consider removing this state entirely.
        continue;
      }
      LegendRenderData renderData = new LegendRenderData(LegendRenderData.IconType.LINE, RADIO_STATE_COLOR.get(state), state.toString());
      legendData.add(renderData);
    }
    return legendData;
  }
}
