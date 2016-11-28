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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedListModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {

  private static final SingleUnitAxisFormatter CPU_USAGE_AXIS = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  private final JBList myThreads;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  private final Splitter mySplitter;

  @Nullable
  private CpuCaptureView myCaptureView;

  public CpuProfilerStageView(@NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(stage);
    myStage = stage;

    stage.getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(CpuProfilerAspect.CAPTURE, this::updateCapture)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection);

    StudioProfilers profilers = stage.getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = timeline.getViewRange();
    Range dataRange = timeline.getDataRange();

    TabularLayout layout = new TabularLayout("*");
    JPanel details = new JPanel(layout);
    details.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(getChoreographer(), timeline, details);
    getChoreographer().register(scrollbar);

    CpuMonitor cpu = new CpuMonitor(profilers);
    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());

    Range leftYRange = new Range(0, 100);
    Range rightYRange = new Range(0, 8);
    JPanel monitorPanel = new JBPanel(new GridBagLayout());
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);

    RangedContinuousSeries thisCpuSeries =
      new RangedContinuousSeries("App", viewRange, leftYRange, cpu.getThisProcessCpuUsage());
    RangedContinuousSeries otherCpuSeries =
      new RangedContinuousSeries("Others", viewRange, leftYRange, cpu.getOtherProcessesCpuUsage());
    RangedContinuousSeries threadsCountSeries =
      new RangedContinuousSeries("Threads", viewRange, rightYRange, cpu.getThreadsCount());

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent.Builder leftAxisBuilder =
      new AxisComponent.Builder(leftYRange, CPU_USAGE_AXIS, AxisComponent.AxisOrientation.RIGHT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent leftAxis = leftAxisBuilder.build();
    axisPanel.add(leftAxis, BorderLayout.WEST);

    AxisComponent.Builder rightAxisBuilder =
      new AxisComponent.Builder(rightYRange, NUM_THREADS_AXIS, AxisComponent.AxisOrientation.LEFT)
        .showAxisLine(false)
        .showMax(true)
        .showUnitAtMax(true)
        .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
        .clampToMajorTicks(true).setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent rightAxis = rightAxisBuilder.build();
    axisPanel.add(rightAxis, BorderLayout.EAST);
    monitorPanel.add(axisPanel, GBC_FULL);

    SelectionComponent selection = new SelectionComponent(timeline.getSelectionRange(), timeline.getViewRange());
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlayPanel.add(overlay, BorderLayout.CENTER);
    monitorPanel.add(overlayPanel, GBC_FULL);
    monitorPanel.add(selection, GBC_FULL);    // Selection needs to be behind the OverlayComponent which handles clicking of DurationData.

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    LineChart lineChart = new LineChart();
    lineChart.addLine(thisCpuSeries, new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true));
    lineChart.addLine(otherCpuSeries, new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true));
    lineChart.addLine(threadsCountSeries, new LineConfig(ProfilerColors.THREADS_COUNT_COLOR)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE));
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    monitorPanel.add(lineChartPanel, GBC_FULL);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    ArrayList<LegendRenderData> legendData = new ArrayList<>();
    legendData.add(lineChart.createLegendRenderData(thisCpuSeries, CPU_USAGE_AXIS, dataRange));
    legendData.add(lineChart.createLegendRenderData(otherCpuSeries, CPU_USAGE_AXIS, dataRange));
    legendData.add(lineChart.createLegendRenderData(threadsCountSeries, NUM_THREADS_AXIS, dataRange));
    legend.setLegendData(legendData);

    final JLabel label = new JLabel(cpu.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);
    monitorPanel.add(legendPanel, GBC_FULL);

    // Create an event representing the traces within the range.
    DurationDataRenderer<CpuCapture> traceRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, myStage.getCpuTraceDataSeries()), ProfilerColors.CPU_CAPTURE_EVENT)
        .setLabelProvider(this::formatCaptureLabel)
        .setStroke(new BasicStroke(1))
        .setLabelColors(new Color(0x70000000, true), Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(getStage()::setCapture)
        .build();


    lineChart.addCustomRenderer(traceRenderer);
    overlay.addDurationDataRenderer(traceRenderer);

    RangedListModel<CpuThreadsModel.RangedCpuThread> model = myStage.getThreadStates();
    myThreads = new JBList(model);
    myThreads.addListSelectionListener((e) -> {
      // TODO: support selecting multiple threads simultaneously.
      int selectedIndex = myThreads.getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        myStage.setSelectedThread(thread.getThreadId());
      }
    });
    JScrollPane scrollingThreads = new JBScrollPane();
    scrollingThreads.setViewportView(myThreads);

    myThreads.setCellRenderer(new ThreadCellRenderer(getChoreographer(), myThreads));
    RangedList rangedList = new RangedList(getTimeline().getViewRange(), model);

    details.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "4*");
    details.add(monitorPanel, new TabularLayout.Constraint(1, 0));
    getChoreographer().register(lineChart);
    getChoreographer().register(traceRenderer);
    getChoreographer().register(leftAxis);
    getChoreographer().register(rightAxis);
    getChoreographer().register(selection);
    getChoreographer().register(legend);

    layout.setRowSizing(2, "6*");
    details.add(scrollingThreads, new TabularLayout.Constraint(2, 0));

    details.add(scrollbar, new TabularLayout.Constraint(3, 0));
    AxisComponent timeAxis = buildTimeAxis(profilers);
    getChoreographer().register(timeAxis);

    details.add(timeAxis, new TabularLayout.Constraint(4, 0));
    getChoreographer().register(rangedList);

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myCaptureButton = new JButton();
    myCaptureButton.addActionListener(event -> capture());

    updateCapture();
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel toolbar = new JPanel();

    JButton button = new JButton();
    button.setIcon(AllIcons.Actions.Back);
    button.addActionListener(action -> myStage.getStudioProfilers().setMonitoringStage());
    toolbar.add(button);
    toolbar.add(myCaptureButton);

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  private String formatCaptureLabel(CpuCapture capture) {
    Range range = getStage().getStudioProfilers().getTimeline().getDataRange();

    long min = (long)(capture.getRange().getMin() - range.getMin());
    long max = (long)(capture.getRange().getMax() - range.getMin());
    return formatTime(min) + " - " + formatTime(max);
  }

  private static String formatTime(long micro) {
    // TODO unify with TimeAxisFormatter
    long min = micro / (1000000 * 60);
    long sec = (micro % (1000000 * 60)) / 1000000;
    long mil = (micro % 1000000) / 1000;

    return String.format("%d:%d:%03d", min, sec, mil);
  }

  private void updateCapture() {
    CpuCapture capture = myStage.getCapture();

    if (myCaptureView != null && myCaptureView.getCapture() != capture) {
      myCaptureView.unregister(getChoreographer());
    }

    if (capture == null) {
      mySplitter.setSecondComponent(null);
      myCaptureView = null;
    }
    else {
      myCaptureView = new CpuCaptureView(capture, this);
      myCaptureView.register(getChoreographer());
      mySplitter.setSecondComponent(myCaptureView.getComponent());
    }

    myCaptureButton.setText(myStage.isCapturing() ? "Stop" : "Record");
  }

  private void capture() {
    if (myStage.isCapturing()) {
      myStage.stopCapturing();
    }
    else {
      myStage.startCapturing();
    }
  }

  private void updateThreadSelection() {
    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = (CpuThreadsModel.RangedCpuThread)myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
      }
    }
    if (myCaptureView != null) {
      myCaptureView.updateThread();
    }
  }

  private static class ThreadCellRenderer implements ListCellRenderer<CpuThreadsModel.RangedCpuThread> {

    private final JLabel myLabel;

    private AnimatedListRenderer<CpuThreadsModel.RangedCpuThread, StateChart<CpuProfilerStage.ThreadState>> myStateCharts;

    /**
     * Keep the index of the item currently hovered.
     */
    private int myHoveredIndex = -1;

    public ThreadCellRenderer(Choreographer choreographer, JList<CpuThreadsModel.RangedCpuThread> list) {
      myLabel = new JLabel();
      myLabel.setFont(myLabel.getFont().deriveFont(10.0f));
      myStateCharts = new AnimatedListRenderer<>(choreographer, list, thread -> {
        StateChart<CpuProfilerStage.ThreadState> chart = new StateChart<>(ProfilerColors.THREAD_STATES);
        chart.setHeightGap(0.35f);
        chart.addSeries(thread.getDataSeries());
        return chart;
      });
      list.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          Point p = new Point(e.getX(), e.getY());
          myHoveredIndex = list.locationToIndex(p);
        }
      });
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  CpuThreadsModel.RangedCpuThread value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JPanel panel = new JPanel(new TabularLayout("*"));
      myLabel.setText(value.getName());

      Color cellBackground = ProfilerColors.MONITOR_BACKGROUND;
      if (isSelected) {
        cellBackground = ProfilerColors.THREAD_SELECTED_BACKGROUND;
      }
      else if (myHoveredIndex == index) {
        cellBackground = ProfilerColors.THREAD_HOVER_BACKGROUND;
      }
      panel.setBackground(cellBackground);

      panel.add(myLabel, new TabularLayout.Constraint(0, 0));
      panel.add(myStateCharts.get(index), new TabularLayout.Constraint(0, 0));
      return panel;
    }
  }
}
