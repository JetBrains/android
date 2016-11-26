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
import com.android.tools.adtui.LegendRenderData;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {
  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  @NotNull private MemoryClassDetailView myDetailView = new MemoryClassDetailView(getStage());

  @NotNull private Splitter myChartClassesSplitter = new Splitter(true);

  public MemoryProfilerStageView(@NotNull MemoryProfilerStage stage) {
    super(stage);
    JComponent monitorUi = buildMonitorUi();
    getComponent().add(monitorUi, BorderLayout.CENTER);

    getStage().getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(MemoryProfilerAspect.MEMORY_DETAILS, this::detailsChanged);
  }

  @Override
  public JComponent getToolbar() {
    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);

    JButton backButton = new JButton();
    backButton.setIcon(AllIcons.Actions.Back);
    toolBar.add(backButton);
    backButton.addActionListener(action -> getStage().getStudioProfilers().setMonitoringStage());

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

    myChartClassesSplitter.setFirstComponent(createLineChart());

    Splitter instancesDetailsSplitter = new Splitter(true);
    JPanel instancesPane = new JPanel();
    JPanel detailsPane = new JPanel();
    instancesDetailsSplitter.setFirstComponent(instancesPane);
    instancesDetailsSplitter.setSecondComponent(detailsPane);
    instancesDetailsSplitter.setVisible(false);

    mainSplitter.setFirstComponent(myChartClassesSplitter);
    mainSplitter.setSecondComponent(instancesDetailsSplitter);

    getComponent().add(mainSplitter, BorderLayout.CENTER);

    return mainSplitter;
  }

  @NotNull
  private JPanel createLineChart() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();
    Range dataRange = getTimeline().getDataRange();

    JPanel panel = new JBPanel(new GridBagLayout());
    setupPanAndZoomListeners(panel);

    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.weighty = 0;

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
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
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(monitor.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    Range leftYRange = new Range(0, 0);
    Range rightYRange = new Range(0, 0);

    RangedContinuousSeries javaSeries = new RangedContinuousSeries("Java", viewRange, leftYRange, monitor.getJavaMemory());
    RangedContinuousSeries nativeSeries = new RangedContinuousSeries("Native", viewRange, leftYRange, monitor.getNativeMemory());
    RangedContinuousSeries graphcisSeries = new RangedContinuousSeries("Graphics", viewRange, leftYRange, monitor.getGraphicsMemory());
    RangedContinuousSeries stackSeries = new RangedContinuousSeries("Stack", viewRange, leftYRange, monitor.getStackMemory());
    RangedContinuousSeries codeSeries = new RangedContinuousSeries("Code", viewRange, leftYRange, monitor.getCodeMemory());
    RangedContinuousSeries otherSeries = new RangedContinuousSeries("Others", viewRange, leftYRange, monitor.getOthersMemory());
    RangedContinuousSeries totalSeries = new RangedContinuousSeries("Total", viewRange, leftYRange, monitor.getTotalMemory());
    RangedContinuousSeries objectSeries = new RangedContinuousSeries("Objects", viewRange, rightYRange, monitor.getObjectCount());

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    lineChart.addLine(javaSeries, new LineConfig(ProfilerColors.MEMORY_JAVA).setFilled(true).setStacked(true));
    lineChart.addLine(nativeSeries, new LineConfig(ProfilerColors.MEMORY_NATIVE).setFilled(true).setStacked(true));
    lineChart.addLine(graphcisSeries, new LineConfig(ProfilerColors.MEMORY_GRAPHCIS).setFilled(true).setStacked(true));
    lineChart.addLine(stackSeries, new LineConfig(ProfilerColors.MEMORY_STACK).setFilled(true).setStacked(true));
    lineChart.addLine(codeSeries, new LineConfig(ProfilerColors.MEMORY_CODE).setFilled(true).setStacked(true));
    lineChart.addLine(otherSeries, new LineConfig(ProfilerColors.MEMORY_OTHERS).setFilled(true).setStacked(true));
    lineChart.addLine(totalSeries, new LineConfig(ProfilerColors.MEMORY_TOTAL).setFilled(true));
    lineChart.addLine(objectSeries, new LineConfig(ProfilerColors.MEMORY_OBJECTS).setStroke(LineConfig.DEFAULT_DASH_STROKE));

    // TODO set proper colors / icons
    DurationDataRenderer<HeapDumpDurationData> heapDumpRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, getStage().getHeapDumpSampleDurations()), Color.WHITE)
        .setLabelBackground(Color.DARK_GRAY, Color.GRAY, Color.lightGray)
        .setIsBlocking(true)
        .setlabelProvider(new Function<HeapDumpDurationData, String>() {
          @Override
          public String apply(HeapDumpDurationData data) {
            return String.format("Dump (%s)", TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true));
          }
        })
        .setClickHander(new Consumer<HeapDumpDurationData>() {
          @Override
          public void accept(HeapDumpDurationData data) {
            getStage().setFocusedHeapDump(data.getDumpInfo());
          }
        }).build();
    DurationDataRenderer<DurationData> allocationRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, getStage().getAllocationDumpSampleDurations()), Color.WHITE)
        .setLabelBackground(Color.DARK_GRAY, Color.GRAY, Color.lightGray)
        .setlabelProvider(new Function<DurationData, String>() {
          @Override
          public String apply(DurationData data) {
            return String.format("Allocation Record (%s)", TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true));
          }
        }).build();
    DurationDataRenderer<GcDurationData> gcRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, monitor.getGcCount()), Color.BLACK)
        .setlabelProvider(data -> data.toString())
        .setAttachLineSeries(objectSeries).build();

    lineChart.addCustomRenderer(heapDumpRenderer);
    lineChart.addCustomRenderer(allocationRenderer);
    lineChart.addCustomRenderer(gcRenderer);

    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(lineChart); // TODO add SelectionComponent
    overlay.addDurationDataRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(allocationRenderer);
    overlay.addDurationDataRenderer(gcRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    getChoreographer().register(lineChart);
    getChoreographer().register(heapDumpRenderer);
    getChoreographer().register(allocationRenderer);
    getChoreographer().register(gcRenderer);
    getChoreographer().register(overlay);
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
    ArrayList<LegendRenderData> legendData = new ArrayList<>();
    legendData.add(lineChart.createLegendRenderData(javaSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(nativeSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(graphcisSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(stackSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(codeSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(otherSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(totalSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(objectSeries, OBJECT_COUNT_AXIS_FORMATTER, dataRange));
    legend.setLegendData(legendData);
    getChoreographer().register(legend);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    monitorPanel.add(overlayPanel, GBC_FULL);
    monitorPanel.add(legendPanel, GBC_FULL);
    monitorPanel.add(axisPanel, GBC_FULL);
    monitorPanel.add(lineChartPanel, GBC_FULL);

    gbc.gridy = 1;
    gbc.weighty = 1;
    panel.add(monitorPanel, gbc);

    return panel;
  }

  private void detailsChanged() {
    myChartClassesSplitter.setSecondComponent(null);
    myDetailView.reset();

    Range viewRange = getTimeline().getViewRange();
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)viewRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)viewRange.getMax());
    myChartClassesSplitter.setSecondComponent(myDetailView.buildComponent(rangeMin, rangeMax));
  }

  private void rangeSelected() {

  }
}
