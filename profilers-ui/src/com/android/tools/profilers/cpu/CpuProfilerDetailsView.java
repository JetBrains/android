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

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBPanel;
import java.awt.FlowLayout;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CpuProfilerDetailsView extends StageView<CpuProfilerStage> {
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

  protected final CpuProfilerStage myStage;

  @NotNull protected final CpuThreadsView myThreads;

  @NotNull protected final RangeTooltipComponent myTooltipComponent;

  @NotNull protected final StudioProfilersView profilersView;

  @NotNull protected final CpuUsageView usageView;

  public CpuProfilerDetailsView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    this.profilersView = profilersView;
    myThreads = new CpuThreadsView(myStage);
    myTooltipComponent = new RangeTooltipComponent(getStage().getTimeline(),
                                                   getTooltipPanel(),
                                                   getProfilersView().getComponent(),
                                                   this::shouldShowTooltipSeekComponent);
    usageView = new CpuUsageView(myStage);
  }

  JPanel getCpuDetails(final CpuUsageView usageView, final int toolTipRowSpan) {
    getTooltipBinder().bind(CpuProfilerStageCpuUsageTooltip.class, CpuProfilerStageCpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, (stageView, tooltip) -> new CpuThreadsTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    myTooltipComponent.registerListenersOn(usageView);
    MouseListener listener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuProfilerStageCpuUsageTooltip(myStage));
    usageView.addMouseListener(listener);

    // "Fit" for the event profiler, "*" for everything else.
    final JPanel details = new JPanel(new TabularLayout("*", "Fit-,*"));
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters as our tooltip component should be first so it draws on top of all elements.
    details.add(myTooltipComponent, new TabularLayout.Constraint(0, 0, toolTipRowSpan, 1));

    TabularLayout mainLayout = new TabularLayout("*");
    mainLayout.setRowSizing(PanelSizing.MONITOR.getRow(), PanelSizing.MONITOR.getRowRule());
    mainLayout.setRowSizing(PanelSizing.DETAILS.getRow(), PanelSizing.DETAILS.getRowRule());
    final JPanel mainPanel = new JBPanel<>(mainLayout);
    mainPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    mainPanel.add(usageView, new TabularLayout.Constraint(PanelSizing.MONITOR.getRow(), 0));
    mainPanel.add(createCpuStatePanel(), new TabularLayout.Constraint(PanelSizing.DETAILS.getRow(), 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));

    return details;
  }

  @Override
  public JPanel getToolbar() {
    return null;
  }

  @NotNull
  JPanel createCpuStatePanel() {
    TabularLayout cpuStateLayout = new TabularLayout("*");
    JPanel cpuStatePanel = new JBPanel<>(cpuStateLayout);

    cpuStatePanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    cpuStateLayout.setRowSizing(PanelSizing.THREADS.getRow(), PanelSizing.THREADS.getRowRule());

    //region CpuThreadsView
    myTooltipComponent.registerListenersOn(myThreads.getComponent());
    cpuStatePanel.add(myThreads.getComponent(), new TabularLayout.Constraint(PanelSizing.THREADS.getRow(), 0));
    //endregion

    return cpuStatePanel;
  }

  @VisibleForTesting
  boolean shouldShowTooltipSeekComponent() {
    return true;
  }
}