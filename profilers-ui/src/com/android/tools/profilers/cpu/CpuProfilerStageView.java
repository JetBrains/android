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
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedListModel;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
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

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline);
    getChoreographer().register(scrollbar);

    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);

    CpuMonitor cpu = new CpuMonitor(profilers);

    JPanel details = new JPanel(new GridBagLayout());
    details.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());

    Range leftYRange = new Range(0, 100);
    Range rightYRange = new Range();
    JPanel monitorPanel = new JBPanel(new GridBagLayout());
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);

    SelectionComponent selection = new SelectionComponent(timeline.getSelectionRange(), timeline.getViewRange());
    monitorPanel.add(selection, GBC_FULL);

    RangedContinuousSeries thisCpuSeries =
      new RangedContinuousSeries("App", viewRange, leftYRange, cpu.getThisProcessCpuUsage());
    RangedContinuousSeries otherCpuSeries =
      new RangedContinuousSeries("Others", viewRange, leftYRange, cpu.getOtherProcessesCpuUsage());

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    LineChart lineChart = new LineChart();
    lineChart.addLine(thisCpuSeries, new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true));
    lineChart.addLine(otherCpuSeries, new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true));
    // TODO add num threads series.
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    monitorPanel.add(lineChartPanel, GBC_FULL);

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

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    ArrayList<LegendRenderData> legendData = new ArrayList<>();
    legendData.add(lineChart.createLegendRenderData(thisCpuSeries, CPU_USAGE_AXIS, dataRange));
    legendData.add(lineChart.createLegendRenderData(otherCpuSeries, CPU_USAGE_AXIS, dataRange));
    legend.setLegendData(legendData);

    final JLabel label = new JLabel(cpu.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);
    monitorPanel.add(legendPanel, GBC_FULL);

    RangedListModel<CpuThreadsModel.RangedCpuThread> model = cpu.getThreadStates();
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

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 0.0;
    details.add(eventsComponent, c);
    c.gridy = 1;
    c.weighty = 0.4;
    details.add(monitorPanel, c);
    getChoreographer().register(lineChart);
    getChoreographer().register(leftAxis);
    getChoreographer().register(rightAxis);
    getChoreographer().register(selection);
    getChoreographer().register(legend);
    c.gridy = 2;
    c.weighty = 0.6;
    details.add(scrollingThreads, c);
    c.gridy = 3;
    c.weighty = 0;
    details.add(scrollbar, c);
    AxisComponent timeAxis = buildTimeAxis(profilers);
    getChoreographer().register(timeAxis);
    c.weighty = 0;
    c.gridy = 4;
    details.add(timeAxis, c);
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

    JButton button = new JButton("<-");
    button.addActionListener(action -> returnToStudioStage());
    toolbar.add(button);
    toolbar.add(myCaptureButton);

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
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
      myCaptureView = new CpuCaptureView(capture, getStage().getStudioProfilers());
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
  }

  private static class ThreadCellRenderer implements ListCellRenderer<CpuThreadsModel.RangedCpuThread> {

    private final JLabel myLabel;

    private AnimatedListRenderer<CpuThreadsModel.RangedCpuThread, StateChart<CpuProfiler.GetThreadsResponse.State>> myStateCharts;

    /**
     * Keep the index of the item currently hovered.
     */
    private int myHoveredIndex = -1;

    public ThreadCellRenderer(Choreographer choreographer, JList<CpuThreadsModel.RangedCpuThread> list) {
      myLabel = new JLabel();
      myLabel.setFont(myLabel.getFont().deriveFont(10.0f));
      myStateCharts = new AnimatedListRenderer<>(choreographer, list, thread -> {
        StateChart<CpuProfiler.GetThreadsResponse.State> chart = new StateChart<>(ProfilerColors.THREAD_STATES);
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
      JLayeredPane panel = new JLayeredPane();
      panel.setLayout(new GridBagLayout());
      panel.setOpaque(true);
      myLabel.setText(value.getName());

      Color cellBackground = ProfilerColors.MONITOR_BACKGROUND;
      if (isSelected) {
        cellBackground = ProfilerColors.THREAD_SELECTED_BACKGROUND;
      }
      else if (myHoveredIndex == index) {
        cellBackground = ProfilerColors.THREAD_HOVER_BACKGROUND;
      }
      panel.setBackground(cellBackground);

      panel.add(myLabel, ProfilerLayout.GBC_FULL);
      myLabel.setOpaque(false);
      panel.add(myStateCharts.get(index), ProfilerLayout.GBC_FULL);
      return panel;
    }
  }
}
