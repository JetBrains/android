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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerScrollbar;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureView;
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private enum PanelSizing {
    /**
     * Sizing string for the CPU graph.
     */
    MONITOR("140px", 0),

    /**
     * Sizing string for the threads / kernel view.
     */
    DETAILS("*", 1),

    /**
     * Sizing string for the frames portion of the details view.
     */
    FRAME("Fit", 0),

    /**
     * Sizing string for the kernel portion of the details view.
     */
    KERNEL("Fit", 1),

    /**
     * Sizing string for the threads portion of the details view.
     */
    THREADS("*", 2);

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

  /**
   * Default ratio of splitter. The splitter ratio adjust the first elements size relative to the bottom elements size.
   * A ratio of 1 means only the first element is shown, while a ratio of 0 means only the bottom element is shown.
   */
  private static final float SPLITTER_DEFAULT_RATIO = 0.5f;

  /**
   * When we are showing the kernel data we want to increase the size of the kernel and threads view. This in turn reduces
   * the size of the view used for the CallChart, FlameChart, ect..
   */
  private static final float KERNEL_VIEW_SPLITTER_RATIO = 0.75f;

  private final CpuProfilerStage myStage;

  @NotNull private final CpuThreadsView myThreads;
  @NotNull private final CpuKernelsView myCpus;
  @NotNull private final CpuFramesView myFrames;

  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   * This will be null if {@link FeatureConfig::isCpuCaptureStageEnabled}
   */
  @NotNull private final JBSplitter mySplitter;

  @NotNull private final CpuCaptureView myCaptureView;

  @NotNull private final RangeTooltipComponent myTooltipComponent;

  @NotNull private final CpuUsageView myUsageView;

  @NotNull private final CpuProfilerToolbar myToolbar;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    myThreads = new CpuThreadsView(myStage);
    myCpus = new CpuKernelsView(myStage);
    myFrames = new CpuFramesView(myStage);

    if (myStage.isImportTraceMode()) {
      myUsageView = new CpuUsageView.ImportModeView(myStage);
      myToolbar = new CpuProfilerToolbar.ImportMode(myStage);
    } else {
      myUsageView = new CpuUsageView.NormalModeView(myStage);
      myToolbar = new CpuProfilerToolbar.NormalMode(stage, getIdeComponents());
    }

    myTooltipComponent = new RangeTooltipComponent(getStage().getTimeline(),
                                                   getTooltipPanel(),
                                                   getProfilersView().getComponent(),
                                                   this::shouldShowTooltipSeekComponent);

    stage.getAspect().addDependency(this)
         .onChange(CpuProfilerAspect.CAPTURE_STATE, myToolbar::update)
         .onChange(CpuProfilerAspect.CAPTURE_SELECTION, myToolbar::update);

    getTooltipBinder().bind(CpuProfilerStageCpuUsageTooltip.class, CpuProfilerStageCpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuKernelTooltip.class, (stageView, tooltip) -> new CpuKernelTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(CpuThreadsTooltip.class, (stageView, tooltip) -> new CpuThreadsTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(CpuFrameTooltip.class, (stageView, tooltip) -> new CpuFrameTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    if (!myStage.isImportTraceMode()) {
      myTooltipComponent.registerListenersOn(myUsageView);
      MouseListener listener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuProfilerStageCpuUsageTooltip(myStage));
      myUsageView.addMouseListener(listener);
    }

    // "Fit" for the event profiler, "*" for everything else.
    final JPanel details = new JPanel(new TabularLayout("*", "Fit-,*"));
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters as such our tooltip component should be first so it draws on top of all elements.
    details.add(myTooltipComponent, new TabularLayout.Constraint(0, 0, 3, 1));

    if (!myStage.isImportTraceMode()) {
      // We shouldn't display the events monitor while in import trace mode.
      final EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
      eventsView.registerTooltip(myTooltipComponent, getStage());
      details.add(eventsView.getComponent(), new TabularLayout.Constraint(0, 0));
    }

    TabularLayout mainLayout = new TabularLayout("*");
    mainLayout.setRowSizing(PanelSizing.MONITOR.getRow(), PanelSizing.MONITOR.getRowRule());
    mainLayout.setRowSizing(PanelSizing.DETAILS.getRow(), PanelSizing.DETAILS.getRowRule());
    final JPanel mainPanel = new JBPanel(mainLayout);
    mainPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    mainPanel.add(myUsageView, new TabularLayout.Constraint(PanelSizing.MONITOR.getRow(), 0));
    mainPanel.add(createCpuStatePanel(), new TabularLayout.Constraint(PanelSizing.DETAILS.getRow(), 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));
    details.add(buildTimeAxis(myStage.getStudioProfilers()), new TabularLayout.Constraint(3, 0));
    details.add(new ProfilerScrollbar(myStage.getTimeline(), details), new TabularLayout.Constraint(4, 0));

    // The first component in the splitter is the L2 components, the 2nd component is the L3 components.
    myCaptureView = new CpuCaptureView(this);
    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    stage.getStudioProfilers().addDependency(this)
      .onChange(ProfilerAspect.MODE, this::updateCaptureViewVisibility);

    updateCaptureViewVisibility();

    CpuProfilerContextMenuInstaller.install(myStage, getIdeComponents(), myUsageView, getComponent());
    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(myUsageView);

    if (!getStage().hasUserUsedCpuCapture() && !getStage().isImportTraceMode()) {
      installProfilingInstructions(myUsageView);
    }

    SessionsManager sessions = getStage().getStudioProfilers().getSessionsManager();
    sessions.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, myToolbar::update);
  }

  @NotNull
  private JPanel createCpuStatePanel() {
    TabularLayout cpuStateLayout = new TabularLayout("*");
    JPanel cpuStatePanel = new JBPanel(cpuStateLayout);

    cpuStatePanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    cpuStateLayout.setRowSizing(PanelSizing.FRAME.getRow(), PanelSizing.FRAME.getRowRule());
    cpuStateLayout.setRowSizing(PanelSizing.KERNEL.getRow(), PanelSizing.KERNEL.getRowRule());
    cpuStateLayout.setRowSizing(PanelSizing.THREADS.getRow(), PanelSizing.THREADS.getRowRule());

    //region CpuThreadsView
    myTooltipComponent.registerListenersOn(myThreads.getComponent());
    cpuStatePanel.add(myThreads.getComponent(), new TabularLayout.Constraint(PanelSizing.THREADS.getRow(), 0));
    //endregion

    //region CpuKernelsView
    myCpus.getComponent().addComponentListener(new ComponentAdapter() {
      // When the CpuKernelModel is updated we adjust the splitter. The higher the number the more space
      // the first component occupies. For when we are showing Kernel elements we want to take up more space
      // than when we are not. As such each time we modify the CpuKernelModel (when a trace is selected) we
      // adjust the proportion of the splitter accordingly.

      @Override
      public void componentShown(ComponentEvent e) {
        mySplitter.setProportion(KERNEL_VIEW_SPLITTER_RATIO);
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        mySplitter.setProportion(SPLITTER_DEFAULT_RATIO);
      }
    });
    myTooltipComponent.registerListenersOn(myCpus.getComponent());
    cpuStatePanel.add(myCpus.getComponent(), new TabularLayout.Constraint(PanelSizing.KERNEL.getRow(), 0));
    //endregion

    //region CpuFramesView
    myTooltipComponent.registerListenersOn(myFrames.getComponent());
    cpuStatePanel.add(myFrames.getComponent(), new TabularLayout.Constraint(PanelSizing.FRAME.getRow(), 0));
    //endregion

    return cpuStatePanel;
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    FontMetrics metrics = UIUtilities.getFontMetrics(parent, ProfilerFonts.H2_FONT);
    InstructionsPanel panel =
      new InstructionsPanel.Builder(new TextInstruction(metrics, "Click Record to start capturing CPU activity"))
        .setEaseOut(myStage.getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    // Add the instructions panel as the first component of |parent|, so that |parent| renders the instructions on top of other components.
    parent.add(panel, new TabularLayout.Constraint(0, 0), 0);
  }

  @Override
  public JComponent getToolbar() {
    return myToolbar.getComponent();
  }

  @Override
  public boolean supportsStreaming() {
    if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureStageEnabled()) {
      // Imported traces will be displayed in the new capture stage. No need to override here.
      return super.supportsStreaming();
    }
    return !myStage.isImportTraceMode();
  }

  @Override
  public boolean supportsStageNavigation() {
    if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureStageEnabled()) {
      // Imported traces will be displayed in the new capture stage. No need to override here.
      return super.supportsStageNavigation();
    }
    return !myStage.isImportTraceMode();
  }

  private void updateCaptureViewVisibility() {
    if (myStage.getProfilerMode() == ProfilerMode.EXPANDED) {
      mySplitter.setSecondComponent(myCaptureView.getComponent());
    }
  }

  /**
   * @return true if the blue seek component from {@link RangeTooltipComponent} should be visible.
   * @see {@link RangeTooltipComponent#myShowSeekComponent}
   */
  @VisibleForTesting
  boolean shouldShowTooltipSeekComponent() {
    return myStage.getTooltip() instanceof CpuProfilerStageCpuUsageTooltip && myUsageView.shouldShowTooltipSeekComponent();
  }
}