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
import com.android.tools.profilers.*;
import com.android.tools.profilers.common.ProfilerButton;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.*;

public class NetworkProfilerStageView extends StageView<NetworkProfilerStage> {

  private final ConnectionsView myConnectionsView;
  private final ConnectionDetailsView myConnectionDetails;
  private final JBScrollPane myConnectionsScroller;

  public NetworkProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull NetworkProfilerStage stage) {
    super(profilersView, stage);

    getStage().getAspect().addDependency(this)
      .onChange(NetworkProfilerAspect.ACTIVE_CONNECTION, this::updateConnectionDetailsView);

    myConnectionDetails = new ConnectionDetailsView(this);
    myConnectionDetails.setMinimumSize(new Dimension(JBUI.scale(450), (int)myConnectionDetails.getMinimumSize().getHeight()));
    myConnectionsView = new ConnectionsView(this, stage::setSelectedConnection);
    myConnectionsScroller = new JBScrollPane(myConnectionsView.getComponent());
    myConnectionsScroller.setVisible(false);

    Splitter leftSplitter = new Splitter(true);
    leftSplitter.setFirstComponent(buildMonitorUi());
    leftSplitter.setSecondComponent(myConnectionsScroller);

    Splitter splitter = new Splitter(false, 0.6f);
    splitter.setFirstComponent(leftSplitter);
    splitter.setSecondComponent(myConnectionDetails);
    splitter.setHonorComponentsMinimumSize(true);

    getComponent().add(splitter, BorderLayout.CENTER);

    updateConnectionDetailsView();
  }

  @TestOnly
  public ConnectionsView getConnectionsView() {
    return myConnectionsView;
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(4, 0));

    AxisComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    panel.add(new NetworkRadioView(this).getComponent(), new TabularLayout.Constraint(1, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    DetailedNetworkUsage usage = getStage().getDetailedNetworkUsage();
    final LineChart lineChart = new LineChart(usage);
    LineConfig receivedConfig = new LineConfig(ProfilerColors.NETWORK_RECEIVING_COLOR).setLegendIconType(LegendConfig.IconType.LINE);
    lineChart.configure(usage.getRxSeries(), receivedConfig);
    LineConfig sentConfig = new LineConfig(ProfilerColors.NETWORK_SENDING_COLOR).setLegendIconType(LegendConfig.IconType.LINE);
    lineChart.configure(usage.getTxSeries(), sentConfig);
    LineConfig connectionConfig = new LineConfig(ProfilerColors.NETWORK_CONNECTIONS_COLOR)
      .setLegendIconType(LegendConfig.IconType.DASHED_LINE).setStroke(LineConfig.DEFAULT_DASH_STROKE);
    lineChart.configure(usage.getConnectionSeries(), connectionConfig);

    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getTrafficAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getConnectionsAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    NetworkProfilerStage.NetworkStageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent(legends);
    legend.configure(legends.getRxLegend(), new LegendConfig(lineChart.getLineConfig(usage.getRxSeries())));
    legend.configure(legends.getTxLegend(), new LegendConfig(lineChart.getLineConfig(usage.getTxSeries())));
    legend.configure(legends.getConnectionLegend(), new LegendConfig(lineChart.getLineConfig(usage.getConnectionSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel());
    getStage().getSelectionModel().addChangeListener(this::onSelectionChanged);

    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(2, "*"); // Give as much space as possible to the main monitor panel
    panel.add(monitorPanel, new TabularLayout.Constraint(2, 0));

    return panel;
  }

  private void updateConnectionDetailsView() {
    myConnectionDetails.update(getStage().getSelectedConnection());
  }

  private void onSelectionChanged(ChangeEvent event) {
    // TODO This should be moved to the model, not here.
    StudioProfilers profilers = getStage().getStudioProfilers();
    myConnectionsScroller.setVisible(!profilers.getTimeline().getSelectionRange().isEmpty());
    profilers.modeChanged();
  }

  @NotNull
  @Override
  public JComponent getToolbar() {
    // TODO Replace with real network profiler toolbar elements. The following buttons are debug only.
    JPanel toolbar = new JPanel(new BorderLayout());
    JButton button = new ProfilerButton(ProfilerIcons.BACK_ARROW);
    button.addActionListener(action -> getStage().getStudioProfilers().setMonitoringStage());
    toolbar.add(button, BorderLayout.WEST);
    return toolbar;
  }
}
