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
package com.android.tools.idea.monitor.ui.cpu.view;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.hchart.*;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.tool.TraceRequestHandler;
import com.android.tools.idea.monitor.ui.cpu.model.AppTrace;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.TraceDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.model.CpuDataPoller;
import com.android.tools.idea.monitor.ui.cpu.model.ThreadStatesDataModel;
import com.android.utils.SparseArray;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.EventDispatcher;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

public final class CpuProfilerUiManager extends BaseProfilerUiManager implements ThreadsSegment.ThreadSelectedListener {

  static final Dimension DEFAULT_DIEMENSION = new Dimension(2000, 2000);

  private ThreadsSegment myThreadSegment;

  private CpuDataPoller myCpuDataPoller;

  private Range myTimeSelectionRange;

  private HTreeChart myExecutionChart;

  private HTreeChart myFlameChart;

  private JBTabbedPane myTabbedPane;

  private final DeviceProfilerService mySelectedDeviceProfilerService;

  private final DeviceContext myDeviceContext;

  private final Project myProject;

  JPanel myTopdownJpanel;

  private JButton myProfileButton;

  public CpuProfilerUiManager(@NotNull Range timeViewRange,
                              @NotNull Range timeSelectionRange,
                              @NotNull Choreographer choreographer,
                              @NotNull SeriesDataStore dataStore,
                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher,
                              @NotNull DeviceProfilerService selectedDeviceProfilerService,
                              @NotNull DeviceContext deviceContext, Project project) {
    super(timeViewRange, choreographer, dataStore, eventDispatcher);
    myTimeSelectionRange = timeSelectionRange;
    myExecutionChart = new HTreeChart();
    myExecutionChart.setHRenderer(new MethodHRenderer());
    myExecutionChart.setXRange(myTimeSelectionRange);
    mySelectedDeviceProfilerService = selectedDeviceProfilerService;
    myDeviceContext = deviceContext;
    myProject = project;
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    myCpuDataPoller = new CpuDataPoller(myDataStore, pid);
    return Sets.newHashSet(myCpuDataPoller);
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);
    myThreadSegment = new ThreadsSegment(myTimeViewRange, myDataStore, myEventDispatcher, this);
    assert myCpuDataPoller != null;
    myCpuDataPoller.setThreadAddedNotifier(myThreadSegment.getThreadAddedNotifier());
    myChoreographer.register(myThreadSegment);
    setupAndRegisterSegment(myThreadSegment, DEFAULT_MONITOR_MIN_HEIGHT, DEFAULT_MONITOR_PREFERRED_HEIGHT, DEFAULT_MONITOR_MAX_HEIGHT);
    overviewPanel.add(myThreadSegment);
    setSegmentState(overviewPanel, myThreadSegment, AccordionLayout.AccordionState.MAXIMIZE);
    myProfileButton  = new JButton();
    myTabbedPane = new JBTabbedPane();
    myTopdownJpanel = new JPanel(new BorderLayout());
  }

  @Override
  public void setupDetailedViewUi(@NotNull JPanel toolbar, @NotNull JPanel detailPanel) {
    super.setupDetailedViewUi(toolbar, detailPanel);

    myProfileButton.setVisible(true);
    myProfileButton.setIcon(AndroidIcons.Ddms.StartMethodProfiling);
    myProfileButton.setText("Start Tracing...");
    myProfileButton.addActionListener(new TraceRequestHandler(myProfileButton, mySelectedDeviceProfilerService, myDeviceContext, myProject));
    toolbar.add(myProfileButton);

    // TODO: Default dimension should not be needed. Find out why (Need to use a BorderLayout maybe?
    myExecutionChart.setPreferredSize(DEFAULT_DIEMENSION);
    myChoreographer.register(myExecutionChart);

    myTabbedPane.add("Execution Chart", myExecutionChart);

    myFlameChart = new HTreeChart(HTreeChart.Orientation.BOTTOM_UP);
    // TODO: Default dimension should not be needed. Find out why (Need to use a BorderLayout maybe?
    myFlameChart.setPreferredSize(DEFAULT_DIEMENSION);
    myFlameChart.setHRenderer(new MethodUsageHRenderer());
    myChoreographer.register(myFlameChart);
    myTabbedPane.add("Flame Chart", myFlameChart);

    myTabbedPane.add("Top-down stats", myTopdownJpanel);
    detailPanel.add(myTabbedPane);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);
    myChoreographer.unregister(myThreadSegment);
    overviewPanel.remove(myThreadSegment);
    detailPanel.remove(myTabbedPane);
    toolbar.remove(myProfileButton);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new CpuUsageSegment(xRange, dataStore, eventDispatcher);
  }

  @Override
  public void onSelected(@NotNull List<ThreadStatesDataModel> selectedThreads) {
    // TODO: Only support mono-selection for now:
    if (selectedThreads.size() != 1) {
      return;
    }

    ThreadStatesDataModel selectedThread = selectedThreads.get(0);
    int threadId = selectedThread.getPid();
    AppTrace trace = TraceDataStore.getInstance().getLastThreadsActivity(myProject.getName());
    SparseArray<HNode<Method>> availableThreads = trace.getThreadsGraph();

    myTopdownJpanel.removeAll();
    if (availableThreads.get(threadId) == null) {
      myExecutionChart.setHTree(null);
      myFlameChart.setHTree(null);
      return;
    }

    // Setup topdown panel
    SparseArray<JComponent> trees = trace.getTopDownTrees();
    myTopdownJpanel.add(trees.get(threadId), BorderLayout.CENTER);

    // Setup execution panel
    HNode<Method> executionTree = availableThreads.get(threadId);
    myExecutionChart.setHTree(executionTree);

    // Setup flame graph
    SparseArray<HNode<MethodUsage>> usageTrees = trace.getTopdownStats();
    HNode<MethodUsage> usageTree = usageTrees.get(threadId);
    myFlameChart.setHTree(usageTree);
    myFlameChart.setXRange(new Range(usageTree.getStart(), usageTree.getEnd()));

    // Setup selection (blue highlight)
    myTimeSelectionRange.set(executionTree.getStart(), executionTree.getEnd());
    myTimeSelectionRange.lockValues();

    // Setup view with a little bit of margin so selection can be seen.
    long duration = executionTree.getEnd() - executionTree.getStart();
    long durationMargin = duration / 10;
    myTimeViewRange.set(executionTree.getStart() - durationMargin, executionTree.getEnd() + durationMargin);
    myTimeViewRange.lockValues();
  }
}
