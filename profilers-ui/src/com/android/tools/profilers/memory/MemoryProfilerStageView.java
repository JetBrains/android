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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.chart.linechart.EventConfig;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {
  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  public MemoryProfilerStageView(@NotNull MemoryProfilerStage stage) {
    super(stage);
    JComponent monitorUi = buildMonitorUi();
    getComponent().add(monitorUi, BorderLayout.CENTER);
  }

  @Override
  public JComponent getToolbar() {
    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);

    JButton backButton = new JButton("Go back");
    toolBar.add(backButton);
    backButton.addActionListener(action -> returnToStudioStage());

    JToggleButton recordAllocationButton = new JToggleButton("Record");
    recordAllocationButton.addActionListener(e -> getStage().setAllocationTracking(recordAllocationButton.isSelected()));
    toolBar.add(recordAllocationButton);

    JButton triggerHeapDumpButton = new JButton("Heap Dump");
    triggerHeapDumpButton.addActionListener(e -> getStage().requestHeapDump());
    toolBar.add(triggerHeapDumpButton);

    return toolBar;
  }

  private JComponent buildMonitorUi() {
    Splitter mainSplitter = new Splitter(false);
    Splitter chartClassesSplitter = new Splitter(true);

    JPanel classesPane = new JPanel();
    classesPane.setVisible(false);
    chartClassesSplitter.setFirstComponent(createLineChart());
    chartClassesSplitter.setSecondComponent(classesPane);

    Splitter instancesDetailsSplitter = new Splitter(true);
    JPanel instancesPane = new JPanel();
    JPanel detailsPane = new JPanel();
    instancesDetailsSplitter.setFirstComponent(instancesPane);
    instancesDetailsSplitter.setSecondComponent(detailsPane);
    instancesDetailsSplitter.setVisible(false);

    mainSplitter.setFirstComponent(chartClassesSplitter);
    mainSplitter.setSecondComponent(instancesDetailsSplitter);

    getComponent().add(mainSplitter, BorderLayout.CENTER);

    return mainSplitter;
  }

  @NotNull
  private JPanel createLineChart() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();

    JPanel panel = new JBPanel(new GridBagLayout());
    setupPanAndZoomListeners(panel);

    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.weighty = 0;

    ProfilerScrollbar sb = new ProfilerScrollbar(timeline);
    getChoreographer().register(sb);
    gbc.gridy = 3;
    panel.add(sb, gbc);

    AxisComponent timeAxis = buildTimeAxis(profilers);
    getChoreographer().register(timeAxis);
    gbc.gridy = 2;
    panel.add(timeAxis, gbc);

    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());
    gbc.gridy = 0;
    panel.add(eventsComponent, gbc);

    MemoryMonitor monitor = new MemoryMonitor(profilers);
    JPanel monitorPanel = new JBPanel(new GridBagLayout());
    final JLabel label = new JLabel(monitor.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    Range leftYRange = new Range();
    Range rightYRange = new Range();

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    lineChart.addLine(new RangedContinuousSeries("Java", viewRange, leftYRange, monitor.getJavaMemory()),
                      new LineConfig(ProfilerColors.MEMORY_JAVA).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Native", viewRange, leftYRange, monitor.getNativeMemory()),
                      new LineConfig(ProfilerColors.MEMORY_NATIVE).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Graphics", viewRange, leftYRange, monitor.getGraphicsMemory()),
                      new LineConfig(ProfilerColors.MEMORY_GRAPHCIS).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Stack", viewRange, leftYRange, monitor.getStackMemory()),
                      new LineConfig(ProfilerColors.MEMORY_STACK).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Code", viewRange, leftYRange, monitor.getCodeMemory()),
                      new LineConfig(ProfilerColors.MEMORY_CODE).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Others", viewRange, leftYRange, monitor.getOthersMemory()),
                      new LineConfig(ProfilerColors.MEMORY_OTHERS).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Total", viewRange, leftYRange, monitor.getTotalMemory()),
                      new LineConfig(ProfilerColors.MEMORY_TOTAL).setFilled(true));
    lineChart.addLine(new RangedContinuousSeries("Objects", viewRange, rightYRange, monitor.getObjectCount()),
                      new LineConfig(ProfilerColors.MEMORY_OBJECTS).setStroke(LineConfig.DEFAULT_DASH_STROKE));

    // TODO set proper colors.
    getStage().getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_DUMP, this::heapDumpSelected)
      .onChange(MemoryProfilerAspect.CURRENT_DIFF_HEAP_DUMP, this::heapDiffSelected);
    lineChart
      .addEvent(new RangedSeries<>(viewRange, getStage().getHeapDumpSampleDurations()), new EventConfig(Color.BLACK).setText("Heap Dump"));
    lineChart.addEvent(new RangedSeries<>(viewRange, getStage().getAllocationDumpSampleDurations()),
                       new EventConfig(Color.BLUE).setText("Alloocation Tracking"));
    getChoreographer().register(lineChart);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent.Builder leftBuilder = new AxisComponent.Builder(leftYRange, MEMORY_AXIS_FORMATTER,
                                                                  AxisComponent.AxisOrientation.RIGHT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .clampToMajorTicks(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent leftAxis = leftBuilder.build();
    getChoreographer().register(leftAxis);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    AxisComponent.Builder rightBuilder = new AxisComponent.Builder(rightYRange, OBJECT_COUNT_AXIS_FORMATTER,
                                                                   AxisComponent.AxisOrientation.LEFT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .clampToMajorTicks(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent rightAxis = rightBuilder.build();
    getChoreographer().register(rightAxis);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    legend.setLegendData(lineChart.getLegendDataFromLineChart());
    getChoreographer().register(legend);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    monitorPanel.add(legendPanel, GBC_FULL);
    monitorPanel.add(axisPanel, GBC_FULL);
    monitorPanel.add(lineChartPanel, GBC_FULL);

    gbc.gridy = 1;
    gbc.weighty = 1;
    panel.add(monitorPanel, gbc);

    return panel;
  }

  private void heapDumpSelected() {

  }

  private void heapDiffSelected() {

  }

  private void rangeSelected() {

  }
}
