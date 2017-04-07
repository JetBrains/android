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
import com.android.tools.adtui.AnimatedRange;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.hchart.*;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.tool.TraceRequestHandler;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.cpu.model.AppTrace;
import com.android.tools.idea.monitor.ui.cpu.model.TraceDataStore;
import com.android.tools.profilers.cpu.ThreadStateDataSeries;
import com.android.utils.SparseArray;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

public final class CpuProfilerUiManager extends BaseProfilerUiManager implements ThreadsSegment.ThreadSelectedListener {

  static final Dimension DEFAULT_DIMENSION = new Dimension(2000, 2000);

  private ThreadsSegment myThreadSegment;

  private AnimatedRange myTimeSelectionRangeUs;

  private HTreeChart<Method> myExecutionChart;

  private HTreeChart<MethodUsage> myFlameChart;

  private JBTabbedPane myTabbedPane;

  private final DeviceProfilerService mySelectedDeviceProfilerService;

  private final DeviceContext myDeviceContext;

  private final Project myProject;

  private JPanel myTopdownJpanel;

  private JPanel myBottomupJPanel;

  private CPUTraceController myCpuTraceControlsUi;

  private AnimatedRange myFlameChartRange;

  public CpuProfilerUiManager(@NotNull Range timeCurrentRangeUs,
                              @NotNull AnimatedRange timeSelectionRangeUs,
                              @NotNull Choreographer choreographer,
                              @NotNull SeriesDataStore dataStore,
                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher,
                              @NotNull DeviceProfilerService selectedDeviceProfilerService,
                              @NotNull DeviceContext deviceContext, Project project) {
    super(timeCurrentRangeUs, choreographer, dataStore, eventDispatcher);
    myTimeSelectionRangeUs = timeSelectionRangeUs;
    mySelectedDeviceProfilerService = selectedDeviceProfilerService;
    myDeviceContext = deviceContext;
    myProject = project;
    createDetailedViewCharts();
  }

  private void createDetailedViewCharts() {
    myExecutionChart = new HTreeChart<>();
    myExecutionChart.setXRange(myTimeSelectionRangeUs);

    myFlameChart = new HTreeChart<>(HTreeChart.Orientation.BOTTOM_UP);
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    return Sets.newHashSet();
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);

    myThreadSegment = new ThreadsSegment(myTimeCurrentRangeUs, myDataStore, myEventDispatcher, this);
    myChoreographer.register(myThreadSegment);
    setupAndRegisterSegment(myThreadSegment, DEFAULT_MONITOR_MIN_HEIGHT, DEFAULT_MONITOR_PREFERRED_HEIGHT, DEFAULT_MONITOR_MAX_HEIGHT);
    overviewPanel.add(myThreadSegment);
    setSegmentState(overviewPanel, myThreadSegment, AccordionLayout.AccordionState.MAXIMIZE);
    myTabbedPane = new JBTabbedPane();
    myTopdownJpanel = new JPanel(new BorderLayout());
    myBottomupJPanel = new JPanel(new BorderLayout());
    createTracingButton(toolbar);
  }

  private void createTracingButton(@NotNull JPanel toolbar) {
    TraceRequestHandler traceRequestHandler = new TraceRequestHandler(mySelectedDeviceProfilerService, myDeviceContext, myProject);
    myCpuTraceControlsUi = new CPUTraceController(traceRequestHandler);
    toolbar.add(myCpuTraceControlsUi);
  }

  @Override
  public void setupDetailedViewUi(@NotNull JPanel toolbar, @NotNull JPanel detailPanel) {
    super.setupDetailedViewUi(toolbar, detailPanel);
    // TODO: Default dimension should not be needed. Find out why (Need to use a BorderLayout maybe?)
    myExecutionChart.setPreferredSize(DEFAULT_DIMENSION);
    myChoreographer.register(myExecutionChart);
    myTabbedPane.add("Execution Chart", myExecutionChart);

    // TODO: Default dimension should not be needed. Find out why (Need to use a BorderLayout maybe?)
    myFlameChart.setPreferredSize(DEFAULT_DIMENSION);
    myChoreographer.register(myFlameChart);
    myTabbedPane.add("Flame Chart", myFlameChart);
    myTabbedPane.add("Top-down stats", myTopdownJpanel);
    myTabbedPane.add("Bottom-up stats", myBottomupJPanel);
    detailPanel.add(myTabbedPane);

    myFlameChartRange = new AnimatedRange();
    myChoreographer.register(myFlameChartRange);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    if (myThreadSegment != null) {
      myChoreographer.unregister(myThreadSegment);
      overviewPanel.remove(myThreadSegment);
      myThreadSegment = null;
    }

    if (myFlameChartRange != null) {
      myChoreographer.unregister(myFlameChartRange);
      myFlameChartRange = null;
    }

    if (myTabbedPane != null) {
      detailPanel.remove(myTabbedPane);
    }

    if (myCpuTraceControlsUi != null) {
      toolbar.remove(myCpuTraceControlsUi);
      myCpuTraceControlsUi = null;
    }

    myChoreographer.unregister(myFlameChart);
    myChoreographer.unregister(myExecutionChart);
  }

  private void resetDetailedComponents() {
    myBottomupJPanel.removeAll();
    myTopdownJpanel.removeAll();
    myExecutionChart.setHTree(null);
    myFlameChart.setHTree(null);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range timeCurrentRangeUs,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new CpuUsageSegment(timeCurrentRangeUs, dataStore, eventDispatcher);
  }

  @Override
  public void onSelected(@NotNull List<ThreadStateDataSeries> selectedThreads) {
    // TODO: Only support mono-selection for now:
    if (selectedThreads.size() != 1) {
      return;
    }

    myEventDispatcher.getMulticaster().profilerExpanded(ProfilerType.CPU);

    resetDetailedComponents();

    ThreadStateDataSeries selectedThread = selectedThreads.get(0);
    int threadId = selectedThread.getProcessId();
    AppTrace trace = TraceDataStore.getInstance().getLastThreadsActivity(myProject.getName());
    if (trace == null) { // No trace have been generated.
      return;
    }

    SparseArray<HNode<Method>> availableThreads = trace.getThreadsGraph();

    if (availableThreads.get(threadId) == null) {
      return;
    }

    // Setup topdown panel
    SparseArray<JComponent> topDownTrees = trace.getTopDownTrees();
    myTopdownJpanel.add(topDownTrees.get(threadId), BorderLayout.CENTER);

    // Setup bottomup panel
    SparseArray<JComponent> bottomUpTrees = trace.getBottomUpTrees();
    myBottomupJPanel.add(bottomUpTrees.get(threadId), BorderLayout.CENTER);

    // Setup execution panel
    HNode<Method> executionTree = availableThreads.get(threadId);
    myExecutionChart.setHTree(executionTree);
    if (AppTrace.Source.ART == trace.getSource()) {
      myExecutionChart.setHRenderer(new NativeMethodHRenderer());
    } else {
      myExecutionChart.setHRenderer(new JavaMethodHRenderer());
    }

    // Setup flame graph
    SparseArray<HNode<MethodUsage>> usageTrees = trace.getTopdownStats();
    HNode<MethodUsage> usageTree = usageTrees.get(threadId);
    myFlameChart.setHTree(usageTree);
    myFlameChartRange.set(usageTree.getStart(), usageTree.getEnd());
    myFlameChart.setXRange(myFlameChartRange);
    if (AppTrace.Source.ART == trace.getSource()) {
      myFlameChart.setHRenderer(new NativeMethodUsageHRenderer());
    } else {
      myFlameChart.setHRenderer(new JavaMethodUsageHRenderer());
    }

    // TODO: Selection doesn't seem right. Fix it and also set the proper color (red highlight).
    // TODO: Reinvestigate sycning selection with the LineChart
    myTimeSelectionRangeUs.set(executionTree.getStart(), executionTree.getEnd());
    //myTimeSelectionRangeUs.lockValues();
    // Setup view with a little bit of margin so selection can be seen.
    //long duration = executionTree.getEnd() - executionTree.getStart();
    //long durationMargin = duration / 10;
    //myTimeCurrentRangeUs.set(executionTree.getStart() - durationMargin, executionTree.getEnd() + durationMargin);
    //myTimeCurrentRangeUs.lockValues();
  }
}
