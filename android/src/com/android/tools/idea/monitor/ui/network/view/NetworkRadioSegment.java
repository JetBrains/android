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
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.LegendRenderData;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.DataStoreSeries;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * This class represents ui for the idle, low and high power states of the device's wireless radios and type of network (e.g WIFI, MOBILE)
 * See: <a href="https://developer.android.com/training/efficient-downloads/efficient-network-access.html">Efficient Network Access</a>
 */
public class NetworkRadioSegment extends BaseSegment {

  public enum RadioState {
    NONE,
    ACTIVE,
    IDLE,
    SLEEPING,
  }

  public enum NetworkType {
    NONE,
    WIFI,
    MOBILE
  }

  private static final String SEGMENT_NAME = "Radio";

  private LegendComponent mLegendComponent;

  private StateChart<RadioState> mRadioChart;

  private StateChart<NetworkType> mNetworkTypeChart;

  private SeriesDataStore mDataStore;

  public NetworkRadioSegment(@NotNull Range timeCurrentRangeUs,
                             @NotNull SeriesDataStore dataStore,
                             @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dispatcher);
    mDataStore = dataStore;
  }

  // TODO change it to use the proper colors
  private static EnumMap<RadioState, Color> getRadioStateColor() {
    EnumMap<RadioState, Color> colors = new EnumMap<>(RadioState.class);
    colors.put(RadioState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    colors.put(RadioState.ACTIVE, JBColor.BLUE.darker());
    colors.put(RadioState.SLEEPING, JBColor.BLUE);
    colors.put(RadioState.IDLE, JBColor.BLUE.brighter());
    return colors;
  }

  private static EnumMap<RadioState, String> getRadioStateLabel() {
    EnumMap<RadioState, String> labels = new EnumMap<>(RadioState.class);
    labels.put(RadioState.NONE, "Radio None");
    labels.put(RadioState.ACTIVE, "Radio Full");
    labels.put(RadioState.SLEEPING, "Radio Low");
    labels.put(RadioState.IDLE, "Radio Idle");
    return labels;
  }

  private static EnumMap<NetworkType, Color> getNetworkTypeColor() {
    EnumMap<NetworkType, Color> colors = new EnumMap<>(NetworkType.class);
    colors.put(NetworkType.NONE, JBColor.BLACK);
    colors.put(NetworkType.MOBILE, JBColor.BLACK);
    colors.put(NetworkType.WIFI, JBColor.BLACK);
    return colors;
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {

    EnumMap<RadioState, Color> colorsMap = getRadioStateColor();
    EnumMap<RadioState, String> labelsMap = getRadioStateLabel();
    mRadioChart = new StateChart(colorsMap);
    mRadioChart.addSeries(new RangedSeries<>(myTimeCurrentRangeUs, new DataStoreSeries<>(mDataStore, SeriesDataType.NETWORK_RADIO)));
    mNetworkTypeChart = new StateChart<>(getNetworkTypeColor());
    mNetworkTypeChart.setRenderMode(StateChart.RenderMode.TEXT);
    mNetworkTypeChart.addSeries(new RangedSeries<>(myTimeCurrentRangeUs, new DataStoreSeries<>(mDataStore, SeriesDataType.NETWORK_TYPE)));

    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    for (RadioState state : RadioState.values()) {
      LegendRenderData renderData = new LegendRenderData(LegendRenderData.IconType.LINE, colorsMap.get(state), labelsMap.get(state));
      legendRenderDataList.add(renderData);
    }

    mLegendComponent = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, 100);
    mLegendComponent.setLegendData(legendRenderDataList);
    animatables.add(mNetworkTypeChart);
    animatables.add(mRadioChart);
    animatables.add(mLegendComponent);
  }

  @Override
  protected void setTopCenterContent(@NotNull JPanel panel) {
    panel.add(mLegendComponent, BorderLayout.EAST);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    //TODO Resize this to match mocks.
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 0;

    gbc.gridy = 0;
    panel.add(mNetworkTypeChart, gbc);
    gbc.gridy = 1;
    panel.add(mRadioChart, gbc);
  }
}
