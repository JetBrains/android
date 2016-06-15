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
package com.android.tools.idea.monitor.ui.network.view;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class RadioSegment extends BaseSegment {

  public enum RadioState {
    NONE,
    FULL,
    LOW,
    IDLE
  }

  private static final String SEGMENT_NAME = "Radio";

  private LegendComponent mLegendComponent;

  private StateChart<RadioState> mStateChart;

  public RadioSegment(@NotNull Range sharedRange,
                      @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, sharedRange, dispatcher);
  }

  // TODO change it to use the proper colors
  private static EnumMap<RadioState, Color> getRadioStateColor() {
    EnumMap<RadioState, Color> colors = new EnumMap<>(RadioState.class);
    colors.put(RadioState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    colors.put(RadioState.FULL, JBColor.BLUE.darker());
    colors.put(RadioState.LOW, JBColor.BLUE);
    colors.put(RadioState.IDLE, JBColor.BLUE.brighter());
    return colors;
  }

  private static EnumMap<RadioState, String> getRadioStateLabel() {
    EnumMap<RadioState, String> labels = new EnumMap<>(RadioState.class);
    labels.put(RadioState.NONE, "Radio None");
    labels.put(RadioState.FULL, "Radio Full");
    labels.put(RadioState.LOW, "Radio Low");
    labels.put(RadioState.IDLE, "Radio Idle");
    return labels;
  }


  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {

    EnumMap<RadioState, Color> colorsMap = getRadioStateColor();
    EnumMap<RadioState, String> labelsMap = getRadioStateLabel();

    mStateChart = new StateChart(colorsMap);
    mStateChart.setHeightGap(0.5f);
    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    for (RadioState state : RadioState.values()) {
      LegendRenderData renderData = new LegendRenderData(LegendRenderData.IconType.LINE, colorsMap.get(state), labelsMap.get(state));
      legendRenderDataList.add(renderData);
    }

    mLegendComponent = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, 100);
    mLegendComponent.setLegendData(legendRenderDataList);
    animatables.add(mStateChart);
    animatables.add(mLegendComponent);
  }

  @Override
  protected void setTopCenterContent(@NotNull JPanel panel) {
    panel.add(mLegendComponent, BorderLayout.EAST);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    //TODO Resize this to match mocks.
    panel.add(mStateChart, BorderLayout.CENTER);
  }

  public void addRadioStateSeries(RangedSeries<RadioState> series) {
    mStateChart.addSeries(series);
  }
}
