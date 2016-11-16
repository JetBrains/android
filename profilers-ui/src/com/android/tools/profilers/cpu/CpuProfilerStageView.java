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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.SelectionComponent;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {

  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  private final HTreeChart<MethodModel> myCaptureTreeChart;
  private final JBList myThreads;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  private ActionListener myCaptureActionListener;
  private final JPanel myCapturePanel;

  public CpuProfilerStageView(@NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(stage);
    myStage = stage;

    stage.getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(CpuProfilerAspect.CAPTURED_TREE, this::updateCallTree)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection);

    StudioProfilers profilers = stage.getStudioProfilers();
    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);

    CpuMonitor cpu = new CpuMonitor(profilers);

    JPanel details = new JPanel(new GridBagLayout());
    details.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());

    Range leftYRange = new Range(0, 100);
    JLayeredPane layered = new JLayeredPane();
    layered.setLayout(new GridBagLayout());

    ProfilerTimeline timeline = profilers.getTimeline();
    SelectionComponent selection = new SelectionComponent(timeline.getSelectionRange(), timeline.getViewRange());
    layered.add(selection, ProfilerLayout.GBC_FULL);

    LineChart lineChart = new LineChart();
    lineChart.addLine(new RangedContinuousSeries("App", getTimeline().getViewRange(), leftYRange, cpu.getThisProcessCpuUsage()),
                      new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Others", getTimeline().getViewRange(), leftYRange, cpu.getOtherProcessesCpuUsage()),
                      new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true));
    layered.add(lineChart, ProfilerLayout.GBC_FULL);

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

    myCaptureTreeChart = new HTreeChart<>();
    myCaptureTreeChart.setHRenderer(new SampledMethodUsageHRenderer());
    myCaptureTreeChart.setXRange(profilers.getTimeline().getSelectionRange());

    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline);
    getChoreographer().register(scrollbar);

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
    details.add(layered, c);
    getChoreographer().register(lineChart);
    getChoreographer().register(selection);
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

    myCapturePanel = new JPanel(new BorderLayout());
    myCapturePanel.add(myCaptureTreeChart, BorderLayout.CENTER);
    getChoreographer().register(myCaptureTreeChart);

    Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(details);
    splitter.setSecondComponent(myCapturePanel);
    getComponent().add(splitter, BorderLayout.CENTER);

    myCaptureButton = new JButton();
    updateCallTree();
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

  // TODO: better naming + javadoc + implement proper behavior
  private void updateCallTree() {
    // Remove the current action listener
    myCaptureButton.removeActionListener(myCaptureActionListener);
    switch (myStage.getCaptureState()) {
      case NONE:
        myCaptureActionListener = action -> myStage.startCapturing();
        break;
      case CAPTURING:
        // TODO: clean panel
        myCaptureActionListener = action -> myStage.stopCapturing();
        break;
      case CAPTURED:
        myCaptureActionListener = action -> myStage.startCapturing();
    }
    myCaptureButton.setText(myStage.getCaptureState() == CpuCaptureState.CAPTURING ? "Stop" : "Record");
    myCapturePanel.setVisible(myStage.getCaptureState() == CpuCaptureState.CAPTURED);
    myCaptureButton.addActionListener(myCaptureActionListener);
  }

  private void updateThreadSelection() {
    // Updates the tree displayed in capture panel
    myCaptureTreeChart.setHTree(myStage.getCaptureTree());
    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = (CpuThreadsModel.RangedCpuThread) myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
      }
    }
  }
}
