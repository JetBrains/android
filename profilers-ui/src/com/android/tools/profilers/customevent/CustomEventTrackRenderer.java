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

import static com.android.tools.profilers.ProfilerLayout.CUSTOM_EVENT_VISUALIZATION_TRACK_HEIGHT;
import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBDimension;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for custom user events.
 */
public class CustomEventTrackRenderer implements TrackRenderer<CustomEventTrackModel> {

  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<CustomEventTrackModel, ?> trackModel) {
    JPanel container = new JPanel();
    // Line up Custom Event graph with the start Interaction track group so that timelines are synced
    container.setLayout(new TabularLayout("150px,*", "*"));
    container.setFocusable(true);

    // LineChart Panel
    LineChartModel lineChartModel = trackModel.getDataModel().getLineChartModel();
    List<RangedContinuousSeries> rangedContinuousSeriesList = lineChartModel.getSeries();
    // Confirm a series has been added to the line chart model, every event track will have exactly 1 series.
    assert rangedContinuousSeriesList.size() == 1;
    RangedContinuousSeries rangedContinuousSeries = rangedContinuousSeriesList.get(0);

    JPanel lineChartPanel = new JPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    LineChart lineChart = new LineChart(lineChartModel);

    LineConfig config =
      new LineConfig(ProfilerColors.USER_COUNTER_EVENT_USAGE).setLegendIconType(LegendConfig.IconType.LINE).setFilled(true);
    lineChart.configure(rangedContinuousSeries, config);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChart.setFillEndGap(true);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    // Track heights are fixed, so must manually set track height here.
    lineChartPanel
      .setPreferredSize(JBDimension.create(lineChartPanel.getPreferredSize()).withHeight(CUSTOM_EVENT_VISUALIZATION_TRACK_HEIGHT));

    // Axis Panel
    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent leftAxis =
      new AxisComponent(trackModel.getDataModel().getAxisComponentModel(), AxisComponent.AxisOrientation.RIGHT, true);
    leftAxis.setShowAxisLine(false);
    leftAxis.setOnlyShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    // Legend Panel
    JLabel titleLabel = new JLabel(" " + trackModel.getDataModel().getName());
    CustomEventTrackModel.Legends legends = trackModel.getDataModel().getLegends();
    LegendComponent legend = new CustomEventTrackLegendComponent(legends);
    legend.configure(legends.getTrackLegend(),new LegendConfig(LegendConfig.IconType.NONE, ProfilerColors.USER_COUNTER_EVENT_NONE));
    final JPanel legendPanel = new JBPanel(new TabularLayout("*,*", "*,*"));
    legendPanel.setOpaque(false);
    legendPanel.add(titleLabel, new TabularLayout.Constraint(0, 0, 1, 2));
    legendPanel.add(legend, new TabularLayout.Constraint(1, 0));

    container.add(legendPanel, new TabularLayout.Constraint(0, 0));
    container.add(leftAxis, new TabularLayout.Constraint(0, 1));
    container.add(lineChartPanel, new TabularLayout.Constraint(0, 1));
    // Add a line to separate each track at the bottom.
    container.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.COMBOBOX_BORDER));
    container.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    return container;
  }
}
