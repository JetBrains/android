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

import static com.android.tools.profilers.cpu.DetailedCpuChart.PanelSizing.DETAILS;
import static com.android.tools.profilers.cpu.DetailedCpuChart.PanelSizing.MONITOR;
import static com.android.tools.profilers.cpu.DetailedCpuChart.PanelSizing.THREADS;

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.ui.components.JBPanel;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class DetailedCpuChart {

  protected enum PanelSizing {
    /**
     * Sizing string for the CPU graph.
     */
    MONITOR("140px", 0),

    /**
     * Sizing string for the threads / kernel view.
     */
    DETAILS("*", 1),

    /**
     * Sizing string for the threads portion of the details view.
     */
    THREADS("*", 0);

    @NotNull private final String myRowRule;
    private final int myRow;

    PanelSizing(@NotNull String rowRule, int row) {
      myRowRule = rowRule;
      myRow = row;
    }

    @NotNull
    public String getRowRule() {
      return myRowRule;
    }

    public int getRow() {
      return myRow;
    }
  }

  @NotNull private final CpuThreadsView myThreads;

  @NotNull private final StudioProfilersView myProfilersView;
  @NotNull private final CpuUsageView myUsageView;
  @NotNull private final StudioProfilers myStudioProfilers;
  private final AspectModel<CpuProfilerAspect> myAspect;

  public DetailedCpuChart(StudioProfilersView profilersView,
                          LiveCpuUsageModel liveCpuUsageModel) {
    this(profilersView,
         liveCpuUsageModel.getCpuUsageAxis(),
         liveCpuUsageModel.getThreadCountAxis(),
         liveCpuUsageModel.getCpuUsage(),
         liveCpuUsageModel.getTraceDurations(),
         liveCpuUsageModel.getLegends(),
         liveCpuUsageModel.getName(),
         liveCpuUsageModel::setAndSelectCapture,
         liveCpuUsageModel.getThreadStates(),
         liveCpuUsageModel.getUpdatableManager(),
         liveCpuUsageModel.getStudioProfilers(),
         liveCpuUsageModel.getTimeAxisGuide(),
         liveCpuUsageModel::getSelectedThread,
         liveCpuUsageModel::setSelectedThread,
         liveCpuUsageModel.getAspect(),
         liveCpuUsageModel.getStage());
  }

  public DetailedCpuChart(StudioProfilersView profilersView,
                          CpuProfilerStage cpuProfilerStage) {
    this(profilersView,
         cpuProfilerStage.getCpuUsageAxis(),
         cpuProfilerStage.getThreadCountAxis(),
         cpuProfilerStage.getCpuUsage(),
         cpuProfilerStage.getTraceDurations(),
         cpuProfilerStage.getLegends(),
         cpuProfilerStage.getName(),
         cpuProfilerStage::setAndSelectCapture,
         cpuProfilerStage.getThreadStates(),
         cpuProfilerStage.getUpdatableManager(),
         cpuProfilerStage.getStudioProfilers(),
         cpuProfilerStage.getTimeAxisGuide(),
         cpuProfilerStage::getSelectedThread,
         cpuProfilerStage::setSelectedThread,
         cpuProfilerStage.getAspect(),
         cpuProfilerStage);
  }

  private DetailedCpuChart(StudioProfilersView profilersView,
                          final AxisComponentModel cpuUsageAxis,
                          final AxisComponentModel threadCountAxis,
                          final DetailedCpuUsage detailedCpuUsage,
                          final DurationDataModel<CpuTraceInfo> traceDurations,
                          final CpuProfilerStage.CpuStageLegends cpuStageLegends,
                          final String name,
                          final Consumer<Long> stageSetAndSelectCapture,
                          final CpuThreadsModel threadStates,
                          final UpdatableManager updatableManager,
                          final StudioProfilers studioProfilers,
                          final AxisComponentModel timeAxisGuide,
                          final Supplier<Integer> getSelectedThread,
                          final Consumer<Integer> setSelectedThread,
                          final AspectModel<CpuProfilerAspect> aspect,
                          final Stage stage) {
    myProfilersView = profilersView;
    myStudioProfilers = studioProfilers;
    myAspect = aspect;
    myUsageView =
      new CpuUsageView(cpuUsageAxis, threadCountAxis, detailedCpuUsage, traceDurations, cpuStageLegends, name, stageSetAndSelectCapture);
    myThreads =
      new CpuThreadsView(threadStates, updatableManager, myStudioProfilers, timeAxisGuide, getSelectedThread, setSelectedThread, aspect,
                         stage);
  }

  JPanel createCpuDetailsPanel(final int toolTipRowSpan,
                               RangeTooltipComponent myTooltipComponent) {

    // "Fit" for the event profiler, "*" for everything else.
    final JPanel details = new JPanel(new TabularLayout("*", "Fit-,*"));
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters as such our tooltip component should be first so it draws on top of all elements.
    details.add(myTooltipComponent, new TabularLayout.Constraint(0, 0, toolTipRowSpan, 1));

    TabularLayout mainLayout = new TabularLayout("*");
    mainLayout.setRowSizing(MONITOR.getRow(), MONITOR.getRowRule());
    mainLayout.setRowSizing(DETAILS.getRow(), DETAILS.getRowRule());
    final JPanel mainPanel = new JBPanel<>(mainLayout);
    mainPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    mainPanel.add(myUsageView, new TabularLayout.Constraint(MONITOR.getRow(), 0));
    mainPanel.add(createCpuStatePanel(myTooltipComponent), new TabularLayout.Constraint(DETAILS.getRow(), 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));
    return details;
  }

  @NotNull
  JPanel createCpuStatePanel(RangeTooltipComponent myTooltipComponent) {
    TabularLayout cpuStateLayout = new TabularLayout("*");
    JPanel cpuStatePanel = new JBPanel<>(cpuStateLayout);

    cpuStatePanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    cpuStateLayout.setRowSizing(THREADS.getRow(), THREADS.getRowRule());

    //region CpuThreadsView
    myTooltipComponent.registerListenersOn(myThreads.getComponent());
    cpuStatePanel.add(myThreads.getComponent(), new TabularLayout.Constraint(THREADS.getRow(), 0));
    //endregion

    return cpuStatePanel;
  }

  @NotNull
  public StudioProfilersView getProfilersView() {
    return myProfilersView;
  }

  public CpuUsageView getUsageView() {
    return myUsageView;
  }

  public CpuThreadsView getThreadsView() {
    return myThreads;
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }
}