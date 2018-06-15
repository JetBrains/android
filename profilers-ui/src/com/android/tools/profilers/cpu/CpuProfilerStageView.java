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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profiler.proto.CpuProfiler.TraceInitiationType;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.*;

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
     * Sizing string for the threads portion of the details view, when it is hidden.
     */
    THREADS_COLLAPSED("Fit-"),

    /**
     * Sizing string for the threads portion of the details view, when it is expanded.
     */
    THREADS_EXPANDED("*");


    private final String myLayoutString;

    PanelSpacing(@NotNull String layoutType) {
      myLayoutString = layoutType;
    }

    @Override
    public String toString() {
      return myLayoutString;
    }
  }

  private static final String RECORD_TEXT = "Record";
  private static final String STOP_TEXT = "Stop";

  private static final int MONITOR_PANEL_ROW = 0;
  private static final int DETAILS_PANEL_ROW = 1;
  private static final int DETAILS_KERNEL_PANEL_ROW = 0;
  private static final int DETAILS_THREADS_PANEL_ROW = 1;

  @VisibleForTesting
  static final String ATRACE_BUFFER_OVERFLOW_TITLE = "System Trace Buffer Overflow Detected";

  @VisibleForTesting
  static final String ATRACE_BUFFER_OVERFLOW_MESSAGE = "Your capture exceeded the buffer limit, some data may be missing. Consider recording a shorter trace.";


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

  private final JButton myCaptureButton;
  /**
   * Contains the status of the capture, e.g. "Starting record...", "Recording - XXmXXs", etc.
   */
  private final JLabel myCaptureStatus;
  @NotNull private final DragAndDropList<CpuThreadsModel.RangedCpuThread> myThreads;
  @NotNull private final JList<CpuKernelModel.CpuState> myCpus;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  @NotNull private final JBSplitter mySplitter;

  @NotNull private final LoadingPanel myCaptureViewLoading;

  @Nullable private CpuCaptureView myCaptureView;

  @NotNull private final CpuProfilingConfigurationView myProfilingConfigurationView;

  /**
   * Panel to let user know to take a capture.
   */
  @NotNull private final JPanel myHelpTipPanel;

  @NotNull private final RangeTooltipComponent myTooltipComponent;

  @NotNull private final JLabel myImportedSelectedProcessLabel;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    ProfilerTimeline timeline = getTimeline();
    myImportedSelectedProcessLabel = new JLabel();
    stage.getAspect().addDependency(this)
         .onChange(CpuProfilerAspect.CAPTURE_STATE, this::updateCaptureState)
         .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCaptureSelection)
         .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection)
         .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
         .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    getTooltipBinder().bind(CpuUsageTooltip.class, CpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuKernelTooltip.class, CpuKernelTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    final CpuUsageView usageView = new CpuUsageView(myStage);
    final boolean[] isMouseOverUsageView = new boolean[]{false};
    usageView.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        isMouseOverUsageView[0] = true;
      }

      @Override
      public void mouseExited(MouseEvent e) {
        isMouseOverUsageView[0] = false;
      }
    });

    myTooltipComponent = new RangeTooltipComponent(timeline.getTooltipRange(),
                                                   timeline.getViewRange(),
                                                   timeline.getDataRange(),
                                                   getTooltipPanel(),
                                                   getProfilersView().getComponent(),
                                                   () -> isMouseOverUsageView[0] && usageView.showTooltipSeekComponent());
    if (!myStage.isImportTraceMode()) {
      myTooltipComponent.registerListenersOn(usageView);
    }

    myThreads = new DragAndDropList<>(myStage.getThreadStates());
    myCpus = new JBList<>(myStage.getCpuKernelModel());


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
    detailsLayout.setRowSizing(DETAILS_THREADS_PANEL_ROW, PanelSpacing.THREADS_EXPANDED.toString());
    final JPanel detailsPanel = new JBPanel(detailsLayout);
    detailsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    configureKernelPanel(detailsPanel);
    configureThreadsPanel(detailsPanel, detailsLayout);

    mainPanel.add(usageView, new TabularLayout.Constraint(MONITOR_PANEL_ROW, 0));
    mainPanel.add(detailsPanel, new TabularLayout.Constraint(DETAILS_PANEL_ROW, 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    myHelpTipPanel = new JPanel(new BorderLayout());
    configureHelpTipPanel();

    // The first component in the splitter is the L2 components, the 2nd component is the L3 components.
    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myProfilingConfigurationView = new CpuProfilingConfigurationView(myStage, getIdeComponents());

    // Set to the longest text this button will show as to initialize the persistent size properly.
    // Call setPreferredSize to avoid the initialized size being overwritten.
    // TODO: b/80546414 Use common button instead.
    myCaptureButton = new JButton(RECORD_TEXT);

    // Make the record button's height same with myProfilingConfigurationView.
    myCaptureButton.setPreferredSize(JBDimension.create(myCaptureButton.getPreferredSize()).withHeight(
      (int)myProfilingConfigurationView.getComponent().getPreferredSize().getHeight()));
    myCaptureButton.addActionListener(event -> myStage.toggleCapturing());

    myCaptureStatus = new JLabel("");
    myCaptureStatus.setFont(ProfilerFonts.STANDARD_FONT);
    myCaptureStatus.setBorder(JBUI.Borders.emptyLeft(5));
    myCaptureStatus.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    myCaptureViewLoading = getProfilersView().getIdeProfilerComponents().createLoadingPanel(-1);
    myCaptureViewLoading.setLoadingText("Parsing capture...");

    updateCaptureState();

    CpuProfilerContextMenuInstaller.install(myStage, getIdeComponents(), usageView, getComponent());
    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(usageView);

    if (!getStage().hasUserUsedCpuCapture() && !getStage().isImportTraceMode()) {
      installProfilingInstructions(usageView);
    }
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

  /**
   * This function handles the layout and rendering of the cpu kernel panel. This panel represents
   * each core found in an atrace file and the state associated with each core.
   *
   * @param detailsPanel  panel that is assumed to contain the Kernel list, as well as the Threads List.
   */
  private void configureKernelPanel(@NotNull JPanel detailsPanel) {
    CpuKernelModel cpuModel = myStage.getCpuKernelModel();
    myCpus.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myCpus.setCellRenderer(new CpuKernelCellRenderer(getStage().getStudioProfilers().getIdeServices().getFeatureConfig(),
                                                     myStage.getStudioProfilers().getSession().getPid(),
                                                     myStage.getUpdatableManager(), myCpus, myThreads));

    // Handle selection.
    myCpus.addListSelectionListener((e) -> cpuKernelRunningStateSelected(cpuModel));
    myCpus.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        cpuKernelRunningStateSelected(cpuModel);
        getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectCpuKernelElement();
      }
    });

    // Handle Tooltip
    myCpus.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuKernelTooltip(myStage)));
    myCpus.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myCpus.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuKernelModel.CpuState model = myCpus.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuKernelTooltip) {
            CpuKernelTooltip tooltip = (CpuKernelTooltip)myStage.getTooltip();
            tooltip.setCpuSeries(model.getCpuId(), model.getSeries());
          }
        }
      }
    });
    myTooltipComponent.registerListenersOn(myCpus);

    // Create hideable panel for CPU list.
    HideablePanel kernelsPanel = new HideablePanel.Builder("KERNEL", new CpuListScrollPane(myCpus, detailsPanel))
      .setShowSeparator(false)
      // We want to keep initially expanded to false because the kernel layout is set to "Fix" by default. As such when
      // we later change the contents to have elements and expand the view we also want to trigger the StateChangedListener below
      // to properly set the layout to be expanded. If we set initially expanded to true, then the StateChangedListener will never
      // get triggered and we will not update our layout.
      .setInitiallyExpanded(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();

    // Handle when we get CPU data we want to show the cpu list.
    cpuModel.addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        boolean hasElements = myCpus.getModel().getSize() != 0;
        // Lets only show 4 cores max the user can scroll to view the rest.
        myCpus.setVisibleRowCount(Math.min(4, myCpus.getModel().getSize()));
        kernelsPanel.setVisible(hasElements);
        kernelsPanel.setExpanded(hasElements);
        kernelsPanel.setTitle(String.format("KERNEL (%d)", myCpus.getModel().getSize()));
        // When the CpuKernelModel is updated we adjust the splitter. The higher the number the more space
        // the first component occupies. For when we are showing Kernel elements we want to take up more space
        // than when we are not. As such each time we modify the CpuKernelModel (when a trace is selected) we
        // adjust the proportion of the splitter accordingly.
        if (hasElements) {
          mySplitter.setProportion(KERNEL_VIEW_SPLITTER_RATIO);
        }
        else {
          mySplitter.setProportion(SPLITTER_DEFAULT_RATIO);
        }
        detailsPanel.revalidate();
      }

      @Override
      public void intervalAdded(ListDataEvent e) {
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
      }
    });
    // Hide CPU panel by default
    kernelsPanel.setVisible(false);

    // Clear border set by default on the hideable panel.
    kernelsPanel.setBorder(JBUI.Borders.empty());
    kernelsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    kernelsPanel.addStateChangedListener(
      (e) -> getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuKernelHideablePanel());
    detailsPanel.add(kernelsPanel, new TabularLayout.Constraint(DETAILS_KERNEL_PANEL_ROW, 0));
  }

  private void configureThreadsPanel(@NotNull JPanel detailsPanel, TabularLayout detailsLayout) {
    // TODO(b/62447834): Make a decision on how we want to handle thread selection.
    myThreads.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreads.setBorder(null);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    CpuThreadsModel model = myStage.getThreadStates();
    myThreads.addListSelectionListener((e) -> {
      int selectedIndex = myThreads.getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        if (myStage.getSelectedThread() != thread.getThreadId()) {
          myStage.setSelectedThread(thread.getThreadId());
          myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        }
      }
      else {
        myStage.setSelectedThread(CaptureModel.NO_THREAD);
      }
    });

    myThreads.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myThreads.getSelectedIndex() < 0 && myThreads.getModel().getSize() > 0) {
          myThreads.setSelectedIndex(0);
        }
      }
    });

    myThreads.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuThreadsTooltip(myStage)));
    myThreads.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myThreads.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = myThreads.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuThreadsTooltip) {
            CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }
    });
    myTooltipComponent.registerListenersOn(myThreads);

    // Add AxisComponent only to scrollable section of threads list.
    final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    CpuListScrollPane scrollingThreads = new CpuListScrollPane(myThreads, detailsPanel);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });

    final JPanel threads = new JPanel(new TabularLayout("*", "*"));
    threads.add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
    threads.add(scrollingThreads, new TabularLayout.Constraint(0, 0));

    final HideablePanel threadsPanel = new HideablePanel.Builder("THREADS", threads)
      .setShowSeparator(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();
    threadsPanel.addStateChangedListener((actionEvent) -> {
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuThreadsHideablePanel();
      // On expanded set row sizing to initial ratio.
      PanelSpacing panelSpacing = threadsPanel.isExpanded() ? PanelSpacing.THREADS_EXPANDED : PanelSpacing.THREADS_COLLAPSED;
      detailsLayout.setRowSizing(DETAILS_THREADS_PANEL_ROW, panelSpacing.toString());
    });
    // Clear border set by default on the hideable panel.
    threadsPanel.setBorder(JBUI.Borders.customLine(ProfilerColors.CPU_AXIS_GUIDE_COLOR, 2, 0, 0, 0));
    threadsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myThreads.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {

      }

      @Override
      public void intervalRemoved(ListDataEvent e) {

      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        threadsPanel.setTitle(String.format("THREADS (%d)", myThreads.getModel().getSize()));
      }
    });
    threads.setBorder(JBUI.Borders.empty());
    detailsPanel.add(threadsPanel, new TabularLayout.Constraint(DETAILS_THREADS_PANEL_ROW, 0));
  }

  private void configureHelpTipPanel() {
    FontMetrics headerMetrics = SwingUtilities2.getFontMetrics(myHelpTipPanel, ProfilerFonts.H3_FONT);
    FontMetrics bodyMetrics = SwingUtilities2.getFontMetrics(myHelpTipPanel, ProfilerFonts.STANDARD_FONT);
    InstructionsPanel infoMessage = new InstructionsPanel.Builder(
      new TextInstruction(headerMetrics, "Thread details unavailable"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "Click Record to start capturing CPU activity"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "or select a capture in the timeline."))
      .setColors(JBColor.foreground(), null)
      .build();
    myHelpTipPanel.add(infoMessage, BorderLayout.CENTER);
  }

  /**
   * When a running state is selected from the CPU {@link JBList} this function handles
   * finding the proper thread and selecting the thread as well as triggering the feature tracker.
   */
  private void cpuKernelRunningStateSelected(CpuKernelModel cpuModel) {
    int selectedIndex = myCpus.getSelectedIndex();
    if (selectedIndex < 0) {
      myStage.setSelectedThread(CaptureModel.NO_THREAD);
      return;
    }
    CpuKernelModel.CpuState state = cpuModel.getElementAt(selectedIndex);
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<CpuThreadSliceInfo>> process = state.getModel().getSeries().get(0).getDataSeries().getDataForXRange(tooltipRange);
    if (process.isEmpty()) {
      return;
    }

    int id = process.get(0).value.getId();
    CpuThreadsModel threadsModel = getStage().getThreadStates();
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = threadsModel.getElementAt(i);
      if (id == thread.getThreadId()) {
        myStage.setSelectedThread(thread.getThreadId());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        break;
      }
    }
  }

  private void clearSelection() {
    getStage().getStudioProfilers().getTimeline().getSelectionRange().clear();
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    FontMetrics metrics = SwingUtilities2.getFontMetrics(parent, ProfilerFonts.H2_FONT);
    InstructionsPanel panel =
      new InstructionsPanel.Builder(new TextInstruction(metrics, "Click Record to start capturing CPU activity"))
        .setEaseOut(myStage.getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  @Override
  public JComponent getToolbar() {
    // We shouldn't display the CPU toolbar in import trace mode, so we return an empty panel.
    if (myStage.isImportTraceMode()) {
      JPanel panel = new JPanel(new TabularLayout("8px,*", "*"));
      panel.add(myImportedSelectedProcessLabel, new TabularLayout.Constraint(0,1));
      return panel;
    }
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());

    toolbar.add(myProfilingConfigurationView.getComponent());
    toolbar.add(myCaptureButton);
    toolbar.add(myCaptureStatus);

    SessionsManager sessions = getStage().getStudioProfilers().getSessionsManager();
    sessions.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, () -> myCaptureButton.setEnabled(shouldEnableCaptureButton()));
    myCaptureButton.setEnabled(shouldEnableCaptureButton());

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  /**
   * Should enable the capture button for recording and stopping only when session is alive and no API-initiated tracing is
   * in progress.
   */
  private boolean shouldEnableCaptureButton() {
    return myStage.getStudioProfilers().getSessionsManager().isSessionAlive() && !myStage.isApiInitiatedTracingInProgress();
  }

  @Override
  public boolean navigationControllersEnabled() {
    return !myStage.isImportTraceMode();
  }

  private void updateCaptureState() {
    myCaptureViewLoading.stopLoading();
    switch (myStage.getCaptureState()) {
      case IDLE:
        myCaptureButton.setEnabled(shouldEnableCaptureButton());
        myCaptureButton.setText(RECORD_TEXT);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Record a trace");
        myProfilingConfigurationView.getComponent().setEnabled(true);
        break;
      case CAPTURING:
        if (getStage().getCaptureInitiationType().equals(TraceInitiationType.INITIATED_BY_API)) {
          myCaptureButton.setEnabled(false);
        }
        else {
          myCaptureButton.setEnabled(shouldEnableCaptureButton());
        }
        myCaptureButton.setText(STOP_TEXT);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Stop recording");
        myProfilingConfigurationView.getComponent().setEnabled(false);
        break;
      case PARSING:
        myCaptureViewLoading.startLoading();
        mySplitter.setSecondComponent(myCaptureViewLoading.getComponent());
        break;
      case PARSING_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
      case STARTING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Starting record...");
        myCaptureButton.setToolTipText("");
        myProfilingConfigurationView.getComponent().setEnabled(false);
        break;
      case START_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
      case STOPPING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Stopping record...");
        myCaptureButton.setToolTipText("");
        myProfilingConfigurationView.getComponent().setEnabled(false);
        break;
      case STOP_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
    }
  }

  private void updateCaptureSelection() {
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      // If the capture is still being parsed, the splitter second component should be myCaptureViewLoading
      if (myStage.getCaptureState() != CpuProfilerStage.CaptureState.PARSING) {
        if (myStage.isSelectionFailure()) {
          mySplitter.setSecondComponent(myHelpTipPanel);
        }
        else {
          mySplitter.setSecondComponent(null);
        }
      }
      // Clear the selection if it exists
      clearSelection();
      myCaptureView = null;
    }
    else if ((myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE)
             || (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING)) {
      // Capture has finished parsing. Create a CpuCaptureView to display it.
      myCaptureView = new CpuCaptureView(this);
      mySplitter.setSecondComponent(myCaptureView.getComponent());
      ensureCaptureInViewRange();
      if (capture.getType() == com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ATRACE) {
        if (myStage.isImportTraceMode()) {
          CaptureNode node = capture.getCaptureNode(capture.getMainThreadId());
          assert node != null;
          myImportedSelectedProcessLabel.setText("Process: " + node.getData().getName());
        }
        else if (((AtraceCpuCapture)capture).isMissingData()) {
          myStage.getStudioProfilers().getIdeServices().showWarningBalloon(ATRACE_BUFFER_OVERFLOW_TITLE,
                                                                         ATRACE_BUFFER_OVERFLOW_MESSAGE,
                                                                         null,
                                                                         null);
        }
      }
      else {
        myImportedSelectedProcessLabel.setText("");
      }
    }
  }

  private void updateCaptureElapsedTime() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      long elapsedTimeUs = myStage.getCaptureElapsedTimeUs();
      myCaptureStatus.setText(TimeFormatter.getSemiSimplifiedClockString(elapsedTimeUs));
    }
  }

  private void updateThreadSelection() {
    if (myStage.getSelectedThread() == CaptureModel.NO_THREAD) {
      myThreads.clearSelection();
      return;
    }

    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
        break;
      }
    }

    if (myStage.getSelectedThread() != CaptureModel.NO_THREAD && myStage.isSelectionFailure()) {
      // If the help tip info panel is already showing and the user clears thread selection, we'll leave the panel showing.
      mySplitter.setSecondComponent(myHelpTipPanel);
    }
  }

  private void updateCaptureDetails() {
    if (myCaptureView != null) {
      myCaptureView.updateView();
    }
  }
}