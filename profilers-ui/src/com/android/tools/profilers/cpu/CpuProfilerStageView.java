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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureView;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private enum PanelSpacing {
    /**
     * Sizing string for the CPU graph.
     */
    MONITOR("140px"),

    /**
     * Sizing string for the threads / kernel view.
     */
    DETAILS("*"),

    /**
     * Sizing string for the kernel portion of the details view.
     */
    KERNEL("Fit"),

    /**
     * Sizing string for the threads portion of the details view.
     */
    THREADS("*");

    private final String myLayoutString;

    PanelSpacing(@NotNull String layoutType) {
      myLayoutString = layoutType;
    }

    @Override
    public String toString() {
      return myLayoutString;
    }
  }

  private static final int MONITOR_PANEL_ROW = 0;
  private static final int DETAILS_PANEL_ROW = 1;
  private static final int DETAILS_KERNEL_PANEL_ROW = 0;
  private static final int DETAILS_THREADS_PANEL_ROW = 1;

  @VisibleForTesting
  static final String ATRACE_BUFFER_OVERFLOW_TITLE = "System Trace Buffer Overflow Detected";

  @VisibleForTesting
  static final String ATRACE_BUFFER_OVERFLOW_MESSAGE = "Your capture exceeded the buffer limit, some data may be missing. " +
                                                       "Consider recording a shorter trace.";

  /**
   * Default ratio of splitter. The splitter ratio adjust the first elements size relative to the bottom elements size.
   * A ratio of 1 means only the first element is shown, while a ratio of 0 means only the bottom element is shown.
   */
  @VisibleForTesting
  static final float SPLITTER_DEFAULT_RATIO = 0.5f;

  /**
   * When we are showing the kernel data we want to increase the size of the kernel and threads view. This in turn reduces
   * the size of the view used for the CallChart, FlameChart, ect..
   */
  @VisibleForTesting
  static final float KERNEL_VIEW_SPLITTER_RATIO = 0.75f;

  private final CpuProfilerStage myStage;

  @NotNull private final CpuThreadsView myThreads;
  @NotNull private final CpuKernelsView myCpus;

  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  @NotNull private final JBSplitter mySplitter;

  @NotNull private final CpuCaptureView myCaptureView;

  @NotNull private final RangeTooltipComponent myTooltipComponent;

  @NotNull private final CpuUsageView myUsageView;

  @NotNull private final CpuProfilerToolbar myToolbar;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    myCaptureView = new CpuCaptureView(this);
    if (myStage.isImportTraceMode()) {
      myToolbar = new CpuProfilerToolbar.ImportMode(myStage);
    } else {
      myToolbar = new CpuProfilerToolbar.NormalMode(stage, getIdeComponents());
    }

    ProfilerTimeline timeline = getTimeline();
    stage.getAspect().addDependency(this)
         .onChange(CpuProfilerAspect.CAPTURE_STATE, myToolbar::update)
         .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::onCaptureSelection)
         .onChange(CpuProfilerAspect.CAPTURE_SELECTION, myToolbar::update);

    stage.getStudioProfilers().addDependency(this)
         .onChange(ProfilerAspect.MODE, this::updateCaptureViewVisibility);

    getTooltipBinder().bind(CpuUsageTooltip.class, CpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuKernelTooltip.class, CpuKernelTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    if (myStage.isImportTraceMode()) {
      myUsageView = new CpuUsageView.ImportModeView(myStage);
    } else {
      myUsageView = new CpuUsageView.NormalModeView(myStage);
    }

    myTooltipComponent = new RangeTooltipComponent(timeline.getTooltipRange(),
                                                   timeline.getViewRange(),
                                                   timeline.getDataRange(),
                                                   getTooltipPanel(),
                                                   getProfilersView().getComponent(),
                                                   this::showTooltipSeekComponent);
    if (!myStage.isImportTraceMode()) {
      myTooltipComponent.registerListenersOn(myUsageView);
      MouseListener listener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuUsageTooltip(myStage));
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

    JComponent timeAxis = buildTimeAxis(myStage.getStudioProfilers());
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);

    TabularLayout mainLayout = new TabularLayout("*");
    mainLayout.setRowSizing(MONITOR_PANEL_ROW, PanelSpacing.MONITOR.toString());
    mainLayout.setRowSizing(DETAILS_PANEL_ROW, PanelSpacing.DETAILS.toString());
    final JPanel mainPanel = new JBPanel(mainLayout);
    mainPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    TabularLayout detailsLayout = new TabularLayout("*");
    detailsLayout.setRowSizing(DETAILS_KERNEL_PANEL_ROW, PanelSpacing.KERNEL.toString());
    detailsLayout.setRowSizing(DETAILS_THREADS_PANEL_ROW, PanelSpacing.THREADS.toString());
    final JPanel detailsPanel = new JBPanel(detailsLayout);
    detailsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    myThreads = new CpuThreadsView(stage);
    myCpus = new CpuKernelsView(myStage);
    addKernelPanelToDetails(detailsPanel);
    addThreadsPanelToDetails(detailsPanel);
    mainPanel.add(myUsageView, new TabularLayout.Constraint(MONITOR_PANEL_ROW, 0));
    mainPanel.add(detailsPanel, new TabularLayout.Constraint(DETAILS_PANEL_ROW, 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    // The first component in the splitter is the L2 components, the 2nd component is the L3 components.
    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    CpuProfilerContextMenuInstaller.install(myStage, getIdeComponents(), myUsageView, getComponent());
    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(myUsageView);

    if (!getStage().hasUserUsedCpuCapture() && !getStage().isImportTraceMode()) {
      installProfilingInstructions(myUsageView);
    }
    updateCaptureViewVisibility();

    SessionsManager sessions = getStage().getStudioProfilers().getSessionsManager();
    sessions.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, myToolbar::update);
  }

  private void addThreadsPanelToDetails(@NotNull JPanel detailsPanel) {
    myTooltipComponent.registerListenersOn(myThreads.getComponent());
    detailsPanel.add(myThreads.getComponent(), new TabularLayout.Constraint(DETAILS_THREADS_PANEL_ROW, 0));
  }

  /**
   * Makes sure the selected capture fits entirely in user's view range.
   */
  private void ensureCaptureInViewRange() {
    CpuCapture capture = myStage.getCapture();
    assert capture != null;

    // Give a padding to the capture. 5% of the view range on each side.
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    double padding = timeline.getViewRange().getLength() * 0.05;
    // Now makes sure the capture range + padding is within view range and in the middle if possible.
    timeline.adjustRangeCloseToMiddleView(new Range(capture.getRange().getMin() - padding, capture.getRange().getMax() + padding));
  }

  private void addKernelPanelToDetails(@NotNull JPanel detailsPanel) {
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
    detailsPanel.add(myCpus.getComponent(), new TabularLayout.Constraint(DETAILS_KERNEL_PANEL_ROW, 0));
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    FontMetrics metrics = SwingUtilities2.getFontMetrics(parent, ProfilerFonts.H2_FONT);
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
  public boolean navigationControllersEnabled() {
    return !myStage.isImportTraceMode();
  }

  private void onCaptureSelection() {
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      return;
    }
    if ((myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE)
        || (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING)) {
      // Capture has finished parsing.
      ensureCaptureInViewRange();
      if (capture.getType() == CpuProfilerType.ATRACE) {
        if (!myStage.isImportTraceMode() && ((AtraceCpuCapture)capture).isMissingData()) {
          myStage.getStudioProfilers().getIdeServices().showWarningBalloon(ATRACE_BUFFER_OVERFLOW_TITLE,
                                                                           ATRACE_BUFFER_OVERFLOW_MESSAGE,
                                                                           null,
                                                                           null);
        }
      }
    }
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
  boolean showTooltipSeekComponent() {
    return myStage.getTooltip() instanceof CpuUsageTooltip && myUsageView.showTooltipSeekComponent();
  }
}