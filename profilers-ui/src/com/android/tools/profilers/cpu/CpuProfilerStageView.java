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
import com.android.tools.adtui.model.*;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.icons.AllIcons;
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

import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {

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

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(profilersView, stage);
    myStage = stage;

    stage.getAspect().addDependency()
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
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);

    EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
    JComponent eventsComponent = eventsView.initialize();

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);


    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getThreadCountAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));

    SelectionModel selectionModel = new SelectionModel(timeline.getSelectionRange(), timeline.getViewRange());
    SelectionComponent selection = new SelectionComponent(selectionModel);
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlayPanel.add(overlay, BorderLayout.CENTER);
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    DetailedCpuUsage cpuUsage = getStage().getCpuUsage();
    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true));
    lineChart.configure(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true));
    lineChart.configure(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_COLOR)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE));
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    CpuProfilerStage.CpuStageLegends legends = getStage().getLegends();
    final LegendComponent legend = new LegendComponent(legends);
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getCpuSeries())));
    legend.configure(legends.getOthersLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getOtherCpuSeries())));
    legend.configure(legends.getThreadsLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getThreadsCountSeries())));

    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));

    DurationDataRenderer<CpuCapture> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setLabelProvider(this::formatCaptureLabel)
        .setStroke(new BasicStroke(1))
        .setLabelColors(new Color(0x70000000, true), Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(getStage()::setCapture)
        .build();


    lineChart.addCustomRenderer(traceRenderer);
    overlay.addDurationDataRenderer(traceRenderer);

    CpuThreadsModel model = myStage.getThreadStates();
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

    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads));

    details.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "4*");
    details.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    layout.setRowSizing(2, "6*");
    details.add(scrollingThreads, new TabularLayout.Constraint(2, 0));

    details.add(scrollbar, new TabularLayout.Constraint(3, 0));
    AxisComponent timeAxis = buildTimeAxis(profilers);

    details.add(timeAxis, new TabularLayout.Constraint(4, 0));

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

    return String.format("%d:%02d:%03d", min, sec, mil);
  }

  private void updateCapture() {
    CpuCapture capture = myStage.getCapture();

    if (capture == null) {
      mySplitter.setSecondComponent(null);
      myCaptureView = null;
    }
    else {
      myCaptureView = new CpuCaptureView(capture, this);
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

    private final StateChart<CpuProfilerStage.ThreadState> myStateChart;

    //private AnimatedListRenderer<CpuThreadsModel.RangedCpuThread, StateChart<CpuProfilerStage.ThreadState>> myStateCharts;

    /**
     * Keep the index of the item currently hovered.
     */
    private int myHoveredIndex = -1;

    public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list) {
      myLabel = new JLabel();
      myLabel.setFont(myLabel.getFont().deriveFont(10.0f));
      myStateChart = new StateChart<>(new StateChartModel<>(), ProfilerColors.THREAD_STATES);
      myStateChart.setHeightGap(0.35f);
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
      panel.add(myStateChart, new TabularLayout.Constraint(0, 0));
      myStateChart.setModel(value.getModel());
      return panel;
    }
  }
}
