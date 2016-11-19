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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class NetworkProfilerStageView extends StageView {
  private static final BaseAxisFormatter TRAFFIC_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 2, 5);
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 1, "");

  private final NetworkProfilerStage myStage;
  private final ConnectionDetailsView myConnectionDetails;
  private final NetworkRequestsView myRequestsView;
  private final Splitter mySplitter;

  public NetworkProfilerStageView(NetworkProfilerStage stage) {
    super(stage);
    myStage = stage;

    myConnectionDetails = new ConnectionDetailsView();
    myRequestsView = new NetworkRequestsView(this, stage.getRequestsModel(), stage::setConnection);

    NetworkMonitor monitor = new NetworkMonitor(stage.getStudioProfilers());
    stage.aspect.addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(NetworkProfilerAspect.REQUEST_DETAILS, this::updateRequestDetailsView)
      .onChange(NetworkProfilerAspect.REQUESTS, this::updateRequestsView);

    AxisComponent.Builder leftAxisBuilder =
      new AxisComponent.Builder(new Range(0, 4), TRAFFIC_AXIS_FORMATTER, AxisComponent.AxisOrientation.LEFT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(ProfilerLayout.MARKER_LENGTH, ProfilerLayout.MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, ProfilerLayout.Y_AXIS_TOP_MARGIN);
    AxisComponent leftAxis = leftAxisBuilder.build();

    AxisComponent.Builder rightAxisBuilder =
      new AxisComponent.Builder(new Range(0, 5), CONNECTIONS_AXIS_FORMATTER, AxisComponent.AxisOrientation.RIGHT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(ProfilerLayout.MARKER_LENGTH, ProfilerLayout.MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, ProfilerLayout.Y_AXIS_TOP_MARGIN);
    AxisComponent rightAxis = rightAxisBuilder.build();

    LineChart lineChart = new LineChart();
    LineConfig receivedConfig = new LineConfig(ProfilerColors.NETWORK_RECEIVING_COLOR);
    lineChart.addLine(new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(),
                                                 monitor.getTimeline().getViewRange(),
                                                 leftAxis.getRange(),
                                                 monitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED)),
                      receivedConfig);
    LineConfig sentConfig = new LineConfig(ProfilerColors.NETWORK_SENDING_COLOR);
    lineChart.addLine(new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(),
                                                 monitor.getTimeline().getViewRange(),
                                                 leftAxis.getRange(),
                                                 monitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_SENT)),
                      sentConfig);
    LineConfig connectionConfig = new LineConfig(ProfilerColors.NETWORK_CONNECTIONS_COLOR).setStroke(LineConfig.DEFAULT_DASH_STROKE);
    lineChart.addLine(new RangedContinuousSeries("Connections",
                                                 monitor.getTimeline().getViewRange(),
                                                 rightAxis.getRange(),
                                                 monitor.getOpenConnectionsSeries()),
                      connectionConfig);

    JPanel lineChartWrapper = new JPanel(new BorderLayout());
    lineChartWrapper.setBorder(BorderFactory.createEmptyBorder(ProfilerLayout.Y_AXIS_TOP_MARGIN, 0, 0, 0));
    lineChartWrapper.add(lineChart);

    JPanel lineChartAndAxis = new JPanel(new BorderLayout());
    lineChartAndAxis.add(leftAxis, BorderLayout.WEST);
    lineChartAndAxis.add(rightAxis, BorderLayout.EAST);
    lineChartAndAxis.add(lineChartWrapper, BorderLayout.CENTER);

    Splitter l2l3splitter = new Splitter(true);
    l2l3splitter.setFirstComponent(lineChartAndAxis);
    l2l3splitter.setSecondComponent(new JBScrollPane(myRequestsView.getComponent()));

    mySplitter = new Splitter(false);
    mySplitter.setFirstComponent(l2l3splitter);
    mySplitter.setSecondComponent(myConnectionDetails);

    getComponent().add(mySplitter, BorderLayout.CENTER);

    Choreographer choreographer = getChoreographer();
    choreographer.register(lineChart);
    choreographer.register(leftAxis);
    choreographer.register(rightAxis);

    updateRequestsView();
    updateRequestDetailsView();
  }

  private void updateRequestDetailsView() {
    myConnectionDetails.update(myStage.getConnection());
    myConnectionDetails.setVisible(myStage.isConnectionDataEnabled());
    myConnectionDetails.revalidate();
  }

  private void updateRequestsView() {
  }

  @NotNull
  @Override
  public JComponent getToolbar() {
    // TODO Replace with real network profiler toolbar elements. The following buttons are debug only.
    JPanel toolbar = new JPanel();
    JButton button = new JButton("<-");
    button.addActionListener(action -> {
      StudioProfilers profilers = getStage().getStudioProfilers();
      StudioMonitorStage monitor = new StudioMonitorStage(profilers);
      profilers.setStage(monitor);
    });
    toolbar.add(button);
    button = new JButton("Open details pane");
    button.addActionListener(action -> myStage.setEnableConnectionData(!myStage.isConnectionDataEnabled()));
    toolbar.add(button);

    return toolbar;
  }
}
