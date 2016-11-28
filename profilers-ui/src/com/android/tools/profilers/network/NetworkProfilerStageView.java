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

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static com.android.tools.profilers.ProfilerLayout.*;

public class NetworkProfilerStageView extends StageView<NetworkProfilerStage> {
  private static final BaseAxisFormatter TRAFFIC_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 5, 5);
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 1, "");

  private final ConnectionDetailsView myConnectionDetails;

  public NetworkProfilerStageView(NetworkProfilerStage stage) {
    super(stage);
    getStage().aspect.addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(NetworkProfilerAspect.CONNECTIONS, this::updateConnectionsView)
      .onChange(NetworkProfilerAspect.ACTIVE_CONNECTION, this::updateConnectionDetailsView);

    myConnectionDetails = new ConnectionDetailsView();
    ConnectionsView connectionsView = new ConnectionsView(this, stage::setConnection);

    Splitter leftSplitter = new Splitter(true);
    leftSplitter.setFirstComponent(buildMonitorUi());
    leftSplitter.setSecondComponent(new JBScrollPane(connectionsView.getComponent()));

    Splitter splitter = new Splitter(false, 0.6f);
    splitter.setFirstComponent(leftSplitter);
    splitter.setSecondComponent(myConnectionDetails);

    getComponent().add(splitter, BorderLayout.CENTER);

    updateConnectionDetailsView();
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();
    Range dataRange = getTimeline().getDataRange();

    EventMonitor events = new EventMonitor(profilers);
    NetworkMonitor monitor = new NetworkMonitor(getStage().getStudioProfilers());

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    setupPanAndZoomListeners(panel);

    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(timeline);
    getChoreographer().register(sb);
    panel.add(sb, new TabularLayout.Constraint(4, 0));

    AxisComponent timeAxis = buildTimeAxis(profilers);
    getChoreographer().register(timeAxis);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(events);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    panel.add(new NetworkRadioView(this).getComponent(), new TabularLayout.Constraint(1, 0));

    JPanel monitorPanel = new JBPanel(new GridBagLayout());
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(monitor.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    Range leftYRange = new Range(0, 4);
    Range rightYRange = new Range(0, 5);

    RangedContinuousSeries receivedSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(),
                                                                 viewRange,
                                                                 leftYRange,
                                                                 monitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED));
    RangedContinuousSeries sentSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(),
                                                                 viewRange,
                                                                 leftYRange,
                                                                 monitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_SENT));
    RangedContinuousSeries connectionSeries = new RangedContinuousSeries("Connections",
                                                                  viewRange,
                                                                  rightYRange,
                                                                  monitor.getOpenConnectionsSeries());

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    LineConfig receivedConfig = new LineConfig(ProfilerColors.NETWORK_RECEIVING_COLOR);
    lineChart.addLine(receivedSeries, receivedConfig);
    LineConfig sentConfig = new LineConfig(ProfilerColors.NETWORK_SENDING_COLOR);
    lineChart.addLine(sentSeries, sentConfig);
    LineConfig connectionConfig = new LineConfig(ProfilerColors.NETWORK_CONNECTIONS_COLOR).setStroke(LineConfig.DEFAULT_DASH_STROKE);
    lineChart.addLine(connectionSeries, connectionConfig);

    getChoreographer().register(lineChart);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent.Builder leftAxisBuilder =
      new AxisComponent.Builder(leftYRange, TRAFFIC_AXIS_FORMATTER, AxisComponent.AxisOrientation.RIGHT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent leftAxis = leftAxisBuilder.build();
    getChoreographer().register(leftAxis);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    AxisComponent.Builder rightAxisBuilder =
      new AxisComponent.Builder(rightYRange, CONNECTIONS_AXIS_FORMATTER, AxisComponent.AxisOrientation.LEFT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent rightAxis = rightAxisBuilder.build();
    getChoreographer().register(rightAxis);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    ArrayList<LegendRenderData> legendData = new ArrayList<>();
    legendData.add(lineChart.createLegendRenderData(receivedSeries, TRAFFIC_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(sentSeries, TRAFFIC_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(connectionSeries, CONNECTIONS_AXIS_FORMATTER, dataRange));
    legend.setLegendData(legendData);
    getChoreographer().register(legend);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    SelectionComponent selection = new SelectionComponent(timeline.getSelectionRange(), timeline.getViewRange());
    getChoreographer().register(selection);
    monitorPanel.add(selection, GBC_FULL);
    monitorPanel.add(legendPanel, GBC_FULL);
    monitorPanel.add(axisPanel, GBC_FULL);
    monitorPanel.add(lineChartPanel, GBC_FULL);

    layout.setRowSizing(2, "*"); // Give as much space as possible to the main monitor panel
    panel.add(monitorPanel, new TabularLayout.Constraint(2, 0));

    return panel;
  }

  private void updateConnectionsView() {
  }

  private void updateConnectionDetailsView() {
    myConnectionDetails.update(getStage().getConnection());
  }

  @NotNull
  @Override
  public JComponent getToolbar() {
    // TODO Replace with real network profiler toolbar elements. The following buttons are debug only.
    JPanel toolbar = new JPanel(new BorderLayout());
    JButton button = new JButton();
    button.setIcon(AllIcons.Actions.Back);
    button.addActionListener(action -> getStage().getStudioProfilers().setMonitoringStage());
    toolbar.add(button, BorderLayout.WEST);
    return toolbar;
  }
}
