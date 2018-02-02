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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.instructions.UrlInstruction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SelectionListener;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.network.details.ConnectionDetailsView;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.android.tools.adtui.common.AdtUiUtils.*;
import static com.android.tools.profilers.ProfilerLayout.*;

public class NetworkProfilerStageView extends StageView<NetworkProfilerStage> {

  private static final String CARD_CONNECTIONS = "Connections";
  private static final String CARD_INFO = "Info";

  private final ConnectionsView myConnectionsView;
  private final ThreadsView myThreadsView;
  private final ConnectionDetailsView myConnectionDetails;
  private final JPanel myConnectionsPanel;

  public NetworkProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull NetworkProfilerStage stage) {
    super(profilersView, stage);

    getStage().getAspect().addDependency(this)
      .onChange(NetworkProfilerAspect.SELECTED_CONNECTION, this::updateConnectionDetailsView);

    getTooltipBinder().bind(NetworkRadioTooltip.class, NetworkRadioTooltipView::new);
    getTooltipBinder().bind(NetworkTrafficTooltip.class, NetworkTrafficTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);

    myConnectionDetails = new ConnectionDetailsView(this);
    myConnectionDetails.setMinimumSize(new Dimension(JBUI.scale(450), (int)myConnectionDetails.getMinimumSize().getHeight()));
    myConnectionsView = new ConnectionsView(this);
    myThreadsView = new ThreadsView(this);

    JBSplitter leftSplitter = new JBSplitter(true);
    leftSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    leftSplitter.setFirstComponent(buildMonitorUi());

    myConnectionsPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));
    JPanel connectionsPanel = new JPanel(new CardLayout());
    if (stage.getStudioProfilers().getIdeServices().getFeatureConfig().isNetworkThreadViewEnabled()) {
      JTabbedPane connectionsTab = new CommonTabbedPane();
      JScrollPane connectionScrollPane = new JBScrollPane(myConnectionsView.getComponent());
      connectionScrollPane.setBorder(DEFAULT_TOP_BORDER);
      JScrollPane threadsViewScrollPane = new JBScrollPane(myThreadsView.getComponent());
      threadsViewScrollPane.setBorder(DEFAULT_TOP_BORDER);
      connectionsTab.addTab("Connection View", connectionScrollPane);
      connectionsTab.addTab("Thread View", threadsViewScrollPane);
      // The toolbar overlays the tab panel so we have to make sure we repaint the parent panel when switching tabs.
      connectionsTab.addChangeListener((evt) -> {
        myConnectionsPanel.repaint();
        int selectedIndex = connectionsTab.getSelectedIndex();
        if (selectedIndex == 0) {
          getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectNetworkConnectionsView();
        } else if (selectedIndex == 1) {
          getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectNetworkThreadsView();
        } else {
          throw new IllegalStateException("Missing tracking for tab " + connectionsTab.getTitleAt(selectedIndex));
        }
      });
      connectionsPanel.add(connectionsTab, CARD_CONNECTIONS);
    }
    else {
      JScrollPane connectionScrollPane = new JBScrollPane(myConnectionsView.getComponent());
      connectionScrollPane.setBorder(DEFAULT_TOP_BORDER);
      connectionsPanel.add(connectionScrollPane, CARD_CONNECTIONS);
    }

    JPanel infoPanel = new JPanel(new BorderLayout());
    InstructionsPanel infoMessage = new InstructionsPanel.Builder(
      new TextInstruction(INFO_MESSAGE_HEADER_FONT, "Network profiling data unavailable"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(INFO_MESSAGE_DESCRIPTION_FONT, "There is no information for the network traffic you've selected."),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new UrlInstruction(INFO_MESSAGE_DESCRIPTION_FONT, "Learn More",
                         "https://developer.android.com/r/studio-ui/network-profiler-troubleshoot-connections.html"))
      .setColors(JBColor.foreground(), null)
      .build();
    infoPanel.add(infoMessage, BorderLayout.CENTER);
    infoPanel.setName(CARD_INFO);
    connectionsPanel.add(infoPanel, CARD_INFO);

    JPanel toolbar = new JPanel(createToolbarLayout());
    toolbar.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    toolbar.add(getSelectionTimeLabel());

    myConnectionsPanel.add(toolbar, new TabularLayout.Constraint(0, 1));
    myConnectionsPanel.add(connectionsPanel, new TabularLayout.Constraint(0, 0, 2, 2));
    myConnectionsPanel.setVisible(false);
    leftSplitter.setSecondComponent(myConnectionsPanel);

    getTimeline().getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      CardLayout cardLayout = (CardLayout)connectionsPanel.getLayout();
      cardLayout.show(connectionsPanel, selectionHasTrafficUsageWithNoConnection() ? CARD_INFO : CARD_CONNECTIONS);
    });

    JBSplitter splitter = new JBSplitter(false, 0.6f);
    splitter.setFirstComponent(leftSplitter);
    splitter.setSecondComponent(myConnectionDetails);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);

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
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(4, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    NetworkRadioView radioView = new NetworkRadioView(this);
    JComponent radioComponent = radioView.getComponent();
    panel.add(radioComponent, new TabularLayout.Constraint(1, 0));

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
      .setLegendIconType(LegendConfig.IconType.DASHED_LINE).setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE);
    lineChart.configure(usage.getConnectionSeries(), connectionConfig);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);

    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getTrafficAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getConnectionsAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setHideTickAtMin(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    NetworkProfilerStage.NetworkStageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(legends.getRxLegend(), new LegendConfig(lineChart.getLineConfig(usage.getRxSeries())));
    legend.configure(legends.getTxLegend(), new LegendConfig(lineChart.getLineConfig(usage.getTxSeries())));
    legend.configure(legends.getConnectionLegend(), new LegendConfig(lineChart.getLineConfig(usage.getConnectionSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel(), timeline.getViewRange());
    selection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    getStage().getSelectionModel().addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        myConnectionsPanel.setVisible(true);
      }

      @Override
      public void selectionCleared() {
        myConnectionsPanel.setVisible(false);
        myConnectionDetails.setHttpData(null);
      }
    });

    radioComponent.addMouseListener(
      new ProfilerTooltipMouseAdapter(getStage(), () -> new NetworkRadioTooltip(getStage())
      ));

    selection.addMouseListener(new ProfilerTooltipMouseAdapter(getStage(), () -> new NetworkTrafficTooltip(getStage())));

    RangeTooltipComponent tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                                              timeline.getDataRange(),
                                                              getTooltipPanel(),
                                                              ProfilerLayeredPane.class);
    eventsView.registerTooltip(tooltip, getStage());

    tooltip.registerListenersOn(selection);
    tooltip.registerListenersOn(radioComponent);

    if (!getStage().hasUserUsedNetworkSelection()) {
      installProfilingInstructions(monitorPanel);
    }
    monitorPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(2, "*"); // Give as much space as possible to the main monitor panel
    panel.add(monitorPanel, new TabularLayout.Constraint(2, 0));

    return panel;
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    InstructionsPanel panel =
      new InstructionsPanel.Builder(new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "Select a range to inspect network traffic"))
        .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  private void updateConnectionDetailsView() {
    myConnectionDetails.setHttpData(getStage().getSelectedConnection());
  }

  @NotNull
  @Override
  public JComponent getToolbar() {
    return new JPanel(new BorderLayout());
  }

  private boolean selectionHasTrafficUsageWithNoConnection() {
    Range range = getTimeline().getSelectionRange();
    boolean hasNoConnection = !range.isEmpty() && getStage().getConnectionsModel().getData(range).isEmpty();
    if (hasNoConnection) {
      DetailedNetworkUsage detailedNetworkUsage = getStage().getDetailedNetworkUsage();
      return hasTrafficUsage(detailedNetworkUsage.getRxSeries(), range) ||
             hasTrafficUsage(detailedNetworkUsage.getTxSeries(), range);
    }
    else {
      return false;
    }
  }

  private static boolean hasTrafficUsage(RangedContinuousSeries series, Range range) {
    List<SeriesData<Long>> list = series.getDataSeries().getDataForXRange(range);
    if (list.stream().anyMatch(data -> data.x >= range.getMin() && data.x <= range.getMax() && data.value > 0)) {
      return true;
    }

    // If there is no positive value at a time t within given range, check if there is index i that
    // list.get(i).x < range.getMin <= range.getMax < list.get(i + 1).x; and values at i and i+1 are positive.
    Function<Long, Integer> getInsertPoint = time -> {
      int index = Collections.binarySearch(list, new SeriesData<>(time, 0L), (o1, o2) -> Long.compare(o1.x, o2.x));
      return index < 0 ? -(index + 1) : index;
    };
    int minIndex = getInsertPoint.apply((long)range.getMin());
    int maxIndex = getInsertPoint.apply((long)range.getMax());
    if (minIndex == maxIndex) {
      return minIndex > 0 && list.get(minIndex - 1).value > 0 && minIndex < list.size() && list.get(minIndex).value > 0;
    }
    return false;
  }
}
