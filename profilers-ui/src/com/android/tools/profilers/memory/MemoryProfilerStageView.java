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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;
import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.DataVisualizationColors;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.RenderInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ProfilerAction;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerCombobox;
import com.android.tools.profilers.ProfilerComboboxCellRenderer;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.ProfilerLayeredPane;
import com.android.tools.profilers.ProfilerScrollbar;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.android.tools.profilers.memory.MemoryProfilerStage.LiveAllocationSamplingMode;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryProfilerStageView extends BaseMemoryProfilerStageView<MemoryProfilerStage> {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStageView.class);
  }

  private static final String RECORD_TEXT = "Record";
  private static final String STOP_TEXT = "Stop";
  private static final String LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP =
    "Allocation tracking isn't ready. Please wait.";
  @VisibleForTesting
  static final String RECORD_NATIVE_TEXT = "Record native allocations";
  @VisibleForTesting
  static final String X86_RECORD_NATIVE_TOOLTIP = "Native memory recording is unavailable on x86 or x86_64 devices";
  @VisibleForTesting
  static final String STOP_NATIVE_TEXT = "Stop recording";

  private final MemoryProfilerStageLayout myLayout;

  @Nullable private RangeSelectionComponent myRangeSelectionComponent;
  @Nullable private CaptureObject myCaptureObject = null;

  @NotNull private JButton myForceGarbageCollectionButton;
  @NotNull private JButton myHeapDumpButton;
  @NotNull private JButton myAllocationButton;
  @NotNull private final JButton myNativeAllocationButton;
  @NotNull private JComboBox myAllocationSamplingRateDropDown;
  @NotNull private ProfilerAction myForceGarbageCollectionAction;
  @NotNull private ProfilerAction myHeapDumpAction;
  @NotNull private ProfilerAction myAllocationAction;
  @NotNull private ProfilerAction myStopAllocationAction;
  @NotNull private ProfilerAction myNativeAllocationAction;
  @NotNull private ProfilerAction myStopNativeAllocationAction;
  @NotNull private final JLabel myCaptureElapsedTime;
  @NotNull private final JLabel myAllocationSamplingRateLabel;
  @NotNull private final LoadingPanel myHeapDumpLoadingPanel;

  @NotNull private DurationDataRenderer<GcDurationData> myGcDurationDataRenderer;
  @NotNull private DurationDataRenderer<AllocationSamplingRateDurationData> myAllocationSamplingRateRenderer;

  public MemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MemoryProfilerStage stage) {
    super(profilersView, stage);

    // Turns on the auto-capture selection functionality - this would select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected.
    getStage().enableSelectLatestCapture(true, SwingUtilities::invokeLater);

    getTooltipBinder().bind(MemoryUsageTooltip.class, MemoryUsageTooltipView::new);
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));

    // Do not initialize the monitor UI if it only contains heap dump data.
    // In this case, myRangeSelectionComponent is null and we will not build the context menu.
    JPanel monitorUi = getStage().isMemoryCaptureOnly() ? null : buildMonitorUi();
    CapturePanel capturePanel = new CapturePanel(getProfilersView(),
                                                 getStage().getCaptureSelection(),
                                                 getStage().isMemoryCaptureOnly() ? null : getSelectionTimeLabel(),
                                                 getStage().getRangeSelectionModel().getSelectionRange(),
                                                 getIdeComponents(),
                                                 getStage().getTimeline(),
                                                 false);

    myLayout = new MemoryProfilerStageLayout(monitorUi, capturePanel, this::makeLoadingPanel);
    getComponent().add(myLayout.getComponent(), BorderLayout.CENTER);

    myHeapDumpLoadingPanel = getIdeComponents().createLoadingPanel(-1);
    myHeapDumpLoadingPanel.setLoadingText("Capturing heap dump");

    myForceGarbageCollectionButton = new CommonButton(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION);
    myForceGarbageCollectionButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION));
    myForceGarbageCollectionButton.addActionListener(e -> {
      getStage().forceGarbageCollection();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackForceGc();
    });
    myForceGarbageCollectionAction =
      new ProfilerAction.Builder("Force garbage collection")
        .setContainerComponent(getComponent())
        .setIcon(myForceGarbageCollectionButton.getIcon())
        .setActionRunnable(() -> myForceGarbageCollectionButton.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_G, AdtUiUtils.getActionMask())).build();
    myForceGarbageCollectionButton.setToolTipText(myForceGarbageCollectionAction.getDefaultToolTipText());


    myHeapDumpButton = new CommonButton(StudioIcons.Profiler.Toolbar.HEAP_DUMP);
    myHeapDumpButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.HEAP_DUMP));
    myHeapDumpButton.addActionListener(e -> {
      getStage().requestHeapDump();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackDumpHeap();
    });
    myHeapDumpAction =
      new ProfilerAction.Builder("Dump Java heap")
        .setContainerComponent(getComponent())
        .setIcon(myHeapDumpButton.getIcon())
        .setActionRunnable(() -> myHeapDumpButton.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_D, AdtUiUtils.getActionMask())).build();
    myHeapDumpButton.setToolTipText(myHeapDumpAction.getDefaultToolTipText());

    myCaptureElapsedTime = new JLabel("");
    myCaptureElapsedTime.setFont(ProfilerFonts.STANDARD_FONT);
    myCaptureElapsedTime.setBorder(JBUI.Borders.emptyLeft(5));
    myCaptureElapsedTime.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    // Set to the longest text this button will show as to initialize the persistent size properly.
    // Call setPreferredSize to avoid the initialized size being overwritten.
    // TODO: b/80546414 Use common button instead.
    myAllocationButton = new JButton(RECORD_TEXT);
    myAllocationButton.setPreferredSize(myAllocationButton.getPreferredSize());

    myAllocationButton
      .addActionListener(e -> {
        if (getStage().isTrackingAllocations()) {
          getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackRecordAllocations();
        }
        getStage().trackAllocations(!getStage().isTrackingAllocations());
      });
    myAllocationButton.setVisible(!getStage().isLiveAllocationTrackingSupported());
    myAllocationAction =
      new ProfilerAction.Builder("Record allocations")
        .setIcon(StudioIcons.Profiler.Toolbar.RECORD)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> !getStage().isTrackingAllocations())
        .setActionRunnable(() -> myAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask())).build();
    myStopAllocationAction =
      new ProfilerAction.Builder("Stop recording")
        .setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> getStage().isTrackingAllocations())
        .setActionRunnable(() -> myAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_S, AdtUiUtils.getActionMask())).build();

    myNativeAllocationButton = new JButton(RECORD_NATIVE_TEXT);
    myNativeAllocationButton.setPreferredSize(myNativeAllocationButton.getPreferredSize());
    myNativeAllocationButton
      .addActionListener(e -> {
        getStage().toggleNativeAllocationTracking();
        disableRecordingButtons();
      });
    myNativeAllocationButton.setVisible(getStage().isNativeAllocationSamplingEnabled());
    myNativeAllocationAction =
      new ProfilerAction.Builder(RECORD_NATIVE_TEXT)
        .setIcon(StudioIcons.Profiler.Toolbar.RECORD)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> !getStage().isTrackingAllocations())
        .setActionRunnable(() -> myNativeAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask())).build();
    myStopNativeAllocationAction =
      new ProfilerAction.Builder(STOP_NATIVE_TEXT)
        .setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> getStage().isTrackingAllocations())
        .setActionRunnable(() -> myNativeAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_S, AdtUiUtils.getActionMask())).build();

    myAllocationSamplingRateLabel = new JLabel("Allocation Tracking");
    myAllocationSamplingRateLabel.setBorder(JBUI.Borders.empty(0, 8));
    myAllocationSamplingRateDropDown = new ProfilerCombobox();

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, this::allocationTrackingChanged)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_STARTED, this::showHeapDumpInProgress)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_FINISHED, this::hideHeapDumpInProgress);
    getStage().getCaptureSelection().getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)

      .onChange(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    captureObjectChanged();
    allocationTrackingChanged();
    buildContextMenu();
  }

  @Override
  public boolean isToolbarVisible() {
    return !getStage().isMemoryCaptureOnly();
  }

  @VisibleForTesting
  MemoryProfilerStageLayout getLayout() {
    return myLayout;
  }

  @VisibleForTesting
  JButton getGarbageCollectionButtion() {
    return myForceGarbageCollectionButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getHeapDumpButton() {
    return myHeapDumpButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getAllocationButton() {
    return myAllocationButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getNativeAllocationButton() {
    return myNativeAllocationButton;
  }

  @VisibleForTesting
  JLabel getAllocationCaptureElaspedTimeLabel() {
    return myCaptureElapsedTime;
  }

  @VisibleForTesting
  @NotNull
  JComboBox getAllocationSamplingRateDropDown() {
    return myAllocationSamplingRateDropDown;
  }

  @VisibleForTesting
  @NotNull
  JLabel getAllocationSamplingRateLabel() {
    return myAllocationSamplingRateLabel;
  }

  @VisibleForTesting
  @NotNull
  DurationDataRenderer<GcDurationData> getGcDurationDataRenderer() {
    return myGcDurationDataRenderer;
  }

  @VisibleForTesting
  @NotNull
  DurationDataRenderer<AllocationSamplingRateDurationData> getAllocationSamplingRateRenderer() {
    return myAllocationSamplingRateRenderer;
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());
    panel.add(toolbar, BorderLayout.WEST);
    toolbar.removeAll();
    toolbar.add(myForceGarbageCollectionButton);
    toolbar.add(myHeapDumpButton);
    if (getStage().isLiveAllocationTrackingSupported() &&
        getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()) {
      toolbar.add(myAllocationSamplingRateLabel);
      toolbar.add(myAllocationSamplingRateDropDown);
      if (getStage().isNativeAllocationSamplingEnabled()) {
        toolbar.add(new FlatSeparator());
        toolbar.add(myNativeAllocationButton);
        toolbar.add(myCaptureElapsedTime);
      }

      JComboBoxView<LiveAllocationSamplingMode, MemoryProfilerAspect> sampleRateComboView =
        new JComboBoxView<>(myAllocationSamplingRateDropDown,
                            getStage().getAspect(),
                            MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE,
                            getStage()::getSupportedLiveAllocationSamplingMode,
                            getStage()::getLiveAllocationSamplingMode,
                            getStage()::requestLiveAllocationSamplingModeUpdate);
      sampleRateComboView.bind();
      myAllocationSamplingRateDropDown.setRenderer(new LiveAllocationSamplingModeRenderer());
    }
    else {
      // useLiveAllocationTracking can return false if the user previously set live allocation tracking to "None". In this case
      // no live allocation tracking events are returned and false is returned. As such we enter this case and we still need to check
      // if we should be using native allocation tracking or not.
      // TODO (b/150651682): Remove this condition when useLiveAllocationTracking bug is fixed.
      if (getStage().isNativeAllocationSamplingEnabled()) {
        toolbar.add(new FlatSeparator());
        toolbar.add(myNativeAllocationButton);
      }
      else {
        toolbar.add(myAllocationButton);
      }
      toolbar.add(myCaptureElapsedTime);
    }

    StudioProfilers profilers = getStage().getStudioProfilers();
    Runnable toggleButtons = () -> {
      resetRecordingButtons();
      liveAllocationStatusChanged();  // update myAllocationSamplingRateLabel and myAllocationSamplingRateDropDown
    };
    profilers.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, toggleButtons);
    getStage().getAspect().addDependency(this).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_STATUS,
                                                        this::liveAllocationStatusChanged);
    toggleButtons.run();
    return panel;
  }

  private void resetRecordingButtons() {
    boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
    myForceGarbageCollectionButton.setEnabled(isAlive);
    myHeapDumpButton.setEnabled(isAlive && !getStage().isTrackingAllocations());
    myAllocationButton.setEnabled(isAlive);
    myNativeAllocationButton.setEnabled(isAlive && !isSelectedSessionDeviceX86OrX64());
  }

  private void disableRecordingButtons() {
    myAllocationButton.setEnabled(false);
    myNativeAllocationButton.setEnabled(false);
    myHeapDumpButton.setEnabled(false);
  }

  @VisibleForTesting
  @NotNull
  public Splitter getMainSplitter() {
    return myLayout.getMainSplitter();
  }

  @VisibleForTesting
  @NotNull
  public Splitter getChartCaptureSplitter() {
    return myLayout.getChartCaptureSplitter();
  }

  @VisibleForTesting
  @NotNull
  public JPanel getCapturePanel() {
    return myLayout.getCapturePanel().getComponent();
  }

  @VisibleForTesting
  @NotNull
  MemoryCaptureView getCaptureView() {
    return myLayout.getCapturePanel().getCaptureView();
  }

  @VisibleForTesting
  @NotNull
  MemoryHeapView getHeapView() {
    return myLayout.getCapturePanel().getHeapView();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassGrouping getClassGrouping() {
    return myLayout.getCapturePanel().getClassGrouping();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassifierView getClassifierView() {
    return myLayout.getCapturePanel().getClassifierView();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassSetView getClassSetView() {
    return myLayout.getCapturePanel().getClassSetView();
  }

  @VisibleForTesting
  @NotNull
  MemoryInstanceDetailsView getInstanceDetailsView() {
    return myLayout.getCapturePanel().getInstanceDetailsView();
  }

  @VisibleForTesting
  @Nullable
  RangeSelectionComponent getRangeSelectionComponent() {
    return myRangeSelectionComponent;
  }

  @VisibleForTesting
  @NotNull
  JLabel getCaptureElapsedTimeLabel() {
    return myCaptureElapsedTime;
  }

  @VisibleForTesting
  @NotNull
  JLabel getCaptureInfoMessage() {
    return myLayout.getCapturePanel().getCaptureInfoMessage();
  }

  private void liveAllocationStatusChanged() {
    boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
    boolean isReady = getStage().isLiveAllocationTrackingReady();
    if (isAlive) {
      if (isReady) {
        myAllocationSamplingRateLabel.setEnabled(true);
        myAllocationSamplingRateDropDown.setEnabled(true);
        myAllocationSamplingRateLabel.setToolTipText(null);
        myAllocationSamplingRateDropDown.setToolTipText(null);
      }
      else {
        myAllocationSamplingRateLabel.setEnabled(false);
        myAllocationSamplingRateDropDown.setEnabled(false);
        myAllocationSamplingRateLabel.setToolTipText(LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP);
        myAllocationSamplingRateDropDown.setToolTipText(LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP);
      }
    }
    else {
      myAllocationSamplingRateLabel.setEnabled(false);
      myAllocationSamplingRateDropDown.setEnabled(false);
    }
  }

  private boolean isSelectedSessionDeviceX86OrX64() {
    String abi = getStage().getStudioProfilers().getSessionsManager().getSelectedSessionMetaData().getProcessAbi();
    return abi.equalsIgnoreCase("x86") || abi.equalsIgnoreCase("x86_64");
  }

  private void allocationTrackingChanged() {
    if (getStage().isTrackingAllocations()) {
      myAllocationButton.setText(STOP_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myAllocationButton.setToolTipText("Stop recording");
      myNativeAllocationButton.setText(STOP_NATIVE_TEXT);
      myNativeAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myNativeAllocationButton.setToolTipText(STOP_NATIVE_TEXT);
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(0));
    }
    else {
      myCaptureElapsedTime.setText("");
      myAllocationButton.setText(RECORD_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myAllocationButton.setToolTipText("Record memory allocations");
      myNativeAllocationButton.setText(RECORD_NATIVE_TEXT);
      myNativeAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myNativeAllocationButton.setToolTipText(isSelectedSessionDeviceX86OrX64() ? X86_RECORD_NATIVE_TOOLTIP : RECORD_NATIVE_TEXT);
    }
    resetRecordingButtons();
  }

  private void updateCaptureElapsedTime() {
    if (getStage().isTrackingAllocations() &&
        (!getStage().isLiveAllocationTrackingReady() || getStage().isNativeAllocationSamplingEnabled())) {
      long elapsedTimeUs = TimeUnit.NANOSECONDS.toMicros(getStage().getAllocationTrackingElapsedTimeNs());
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(elapsedTimeUs));
    }
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    StreamingTimeline timeline = getStage().getTimeline();
    Range viewRange = timeline.getViewRange();
    HeapDumpRenderer heapDumpRenderer = new HeapDumpRenderer(getStage().getHeapDumpSampleDurations(), viewRange);
    myRangeSelectionComponent = new RangeSelectionComponent(getStage().getRangeSelectionModel());
    myRangeSelectionComponent.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    myRangeSelectionComponent.setRangeOcclusionTest(heapDumpRenderer::isMouseOverHeapDump);
    RangeTooltipComponent tooltip = new RangeTooltipComponent(getStage().getTimeline(),
                                                              getTooltipPanel(),
                                                              getProfilersView().getComponent(),
                                                              () -> myRangeSelectionComponent.shouldShowSeekComponent() &&
                                                                    !heapDumpRenderer.isMouseOverHeapDump());
    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    // Order matters, as such we want to put the tooltip component first so we draw the tooltip line on top of all other
    // components.
    panel.add(tooltip, new TabularLayout.Constraint(0, 0, 2, 1));

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(3, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(2, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    panel.add(eventsView.getComponent(), new TabularLayout.Constraint(0, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);

    DetailedMemoryUsage memoryUsage = getStage().getDetailedMemoryUsage();
    final LineChart lineChart = new LineChart(memoryUsage);
    if (getStage().isLiveAllocationTrackingReady()) {
      // Always show series in their captured state in live allocation mode.
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_JAVA_CAPTURED, memoryUsage.getJavaSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_NATIVE_CAPTURED, memoryUsage.getNativeSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_GRAPHICS_CAPTURED, memoryUsage.getGraphicsSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_STACK_CAPTURED, memoryUsage.getStackSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_CODE_CAPTURED, memoryUsage.getCodeSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_OTHERS_CAPTURED, memoryUsage.getOtherSeries());
      lineChart.configure(memoryUsage.getObjectsSeries(), new LineConfig(ProfilerColors.MEMORY_OBJECTS_CAPTURED)
        .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));
    }
    else {
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_JAVA, memoryUsage.getJavaSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_NATIVE, memoryUsage.getNativeSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_GRAPHICS, memoryUsage.getGraphicsSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_STACK, memoryUsage.getStackSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_CODE, memoryUsage.getCodeSeries());
      configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_OTHERS, memoryUsage.getOtherSeries());
      lineChart.configure(memoryUsage.getObjectsSeries(), new LineConfig(ProfilerColors.MEMORY_OBJECTS)
        .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));
    }
    // The "Total" series is only added in the LineChartModel so it can calculate the max Y value across all the series. We don't want to
    // draw it as an extra line so we hide it by setting it to transparent.
    lineChart.configure(memoryUsage.getTotalMemorySeries(), new LineConfig(JBColor.BLACK));
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    lineChart.setFillEndGap(true);

    myGcDurationDataRenderer = new DurationDataRenderer.Builder<>(memoryUsage.getGcDurations(), JBColor.BLACK)
      .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
      // Need to offset the GcDurationData by the margin difference between the overlay component and the
      // line chart. This ensures we are able to render the Gc events in the proper locations on the line.
      .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconWidth() / 2f,
                       StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconHeight() / 2f)
      .setHostInsets(JBUI.insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .setHoverHandler(getStage().getTooltipLegends().getGcDurationLegend()::setPickData)
      .setClickRegionPadding(0, 0)
      .build();
    lineChart.addCustomRenderer(myGcDurationDataRenderer);

    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(myRangeSelectionComponent);
    overlay.addDurationDataRenderer(myGcDurationDataRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);
    // Only shows allocation tracking visuals in pre-O, since we are always tracking in O+.
    if (!getStage().isLiveAllocationTrackingReady()) {
      DurationDataRenderer<CaptureDurationData<CaptureObject>> allocationRenderer =
        new DurationDataRenderer.Builder<>(getStage().getAllocationInfosDurations(), JBColor.LIGHT_GRAY)
          .setDurationBg(ProfilerColors.MEMORY_ALLOC_BG)
          .setLabelColors(JBColor.DARK_GRAY, JBColor.GRAY, JBColor.LIGHT_GRAY, JBColor.WHITE)
          .setLabelProvider(
            data -> String.format("Allocation record (%s)", data.getDurationUs() == Long.MAX_VALUE ? "in progress" :
                                                            TimeAxisFormatter.DEFAULT
                                                              .getFormattedString(viewRange.getLength(), data.getDurationUs(), true)))
          .build();
      allocationRenderer.addCustomLineConfig(memoryUsage.getJavaSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getJavaSeries())).setColor(ProfilerColors.MEMORY_JAVA_CAPTURED));
      allocationRenderer.addCustomLineConfig(memoryUsage.getNativeSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getNativeSeries())).setColor(ProfilerColors.MEMORY_NATIVE_CAPTURED));
      allocationRenderer.addCustomLineConfig(memoryUsage.getGraphicsSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getGraphicsSeries())).setColor(ProfilerColors.MEMORY_GRAPHICS_CAPTURED));
      allocationRenderer.addCustomLineConfig(memoryUsage.getStackSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getStackSeries())).setColor(ProfilerColors.MEMORY_STACK_CAPTURED));
      allocationRenderer.addCustomLineConfig(memoryUsage.getCodeSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getCodeSeries())).setColor(ProfilerColors.MEMORY_CODE_CAPTURED));
      allocationRenderer.addCustomLineConfig(memoryUsage.getOtherSeries(), LineConfig
        .copyOf(lineChart.getLineConfig(memoryUsage.getOtherSeries())).setColor(ProfilerColors.MEMORY_OTHERS_CAPTURED));
      lineChart.addCustomRenderer(allocationRenderer);
      overlay.addDurationDataRenderer(allocationRenderer);
    }
    else if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()) {
      myAllocationSamplingRateRenderer = new DurationDataRenderer.Builder<>(getStage().getAllocationSamplingRateDurations(), JBColor.BLACK)
        .setDurationBg(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
        .setIconMapper(durationData -> {
          LiveAllocationSamplingMode mode = LiveAllocationSamplingMode
            .getModeFromFrequency(durationData.getCurrentRate().getSamplingNumInterval());
          return getIconForSamplingMode(mode);
        })
        .setLabelOffsets(-StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconWidth() / 2f,
                         StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconHeight() / 2f)
        .setHostInsets(JBUI.insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
        .setClickRegionPadding(0, 0)
        .setHoverHandler(getStage().getTooltipLegends().getSamplingRateDurationLegend()::setPickData)
        .build();
      lineChart.addCustomRenderer(myAllocationSamplingRateRenderer);
      overlay.addDurationDataRenderer(myAllocationSamplingRateRenderer);
    }
    // Order matters so native allocation tracking goes to the top of the stack. This means when a native allocation recording is captured
    // the capture appears on top of the other renderers.
    if (getStage().isNativeAllocationSamplingEnabled()) {
      DurationDataRenderer<CaptureDurationData<CaptureObject>> allocationRenderer =
        new DurationDataRenderer.Builder<>(getStage().getNativeAllocationInfosDurations(), JBColor.LIGHT_GRAY)
          .setDurationBg(ProfilerColors.MEMORY_HEAP_DUMP_BG)
          .setLabelColors(JBColor.DARK_GRAY, JBColor.GRAY, JBColor.LIGHT_GRAY, JBColor.WHITE)
          .setLabelProvider(
            data -> String.format("Native Allocation record (%s)", data.getDurationUs() == Long.MAX_VALUE ? "in progress" :
                                                                   TimeAxisFormatter.DEFAULT
                                                                     .getFormattedString(viewRange.getLength(), data.getDurationUs(),
                                                                                         true)))
          .build();
      for (RangedContinuousSeries series : memoryUsage.getSeries()) {
        LineConfig config = lineChart.getLineConfig(series);
        LineConfig newConfig = LineConfig.copyOf(config).setColor(DataVisualizationColors.INSTANCE.toGrayscale(config.getColor()));
        allocationRenderer.addCustomLineConfig(series, newConfig);
      }
      lineChart.addCustomRenderer(allocationRenderer);
      overlay.addDurationDataRenderer(allocationRenderer);
    }

    lineChart.addCustomRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(heapDumpRenderer);
    heapDumpRenderer.addHeapDumpHoverListener(hovered -> {
      if (hovered) {
        getStage().setTooltip(null);
      } else if (getStage().getTooltip() == null) {
        getStage().setTooltip(new MemoryUsageTooltip(getStage()));
      }
    });

    eventsView.registerTooltip(tooltip, getStage());
    // TODO: Probably this needs to be refactored.
    //       We register in both of them because mouse events received by overly will not be received by overlyPanel.
    tooltip.registerListenersOn(overlay);
    tooltip.registerListenersOn(overlayPanel);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent memoryAxis = new AxisComponent(getStage().getMemoryAxis(), AxisComponent.AxisOrientation.RIGHT);
    memoryAxis.setShowAxisLine(false);
    memoryAxis.setShowMax(true);
    memoryAxis.setShowUnitAtMax(true);
    memoryAxis.setHideTickAtMin(true);
    memoryAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    memoryAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(memoryAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getObjectsAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setHideTickAtMin(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    MemoryProfilerStage.MemoryStageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(legends.getJavaLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getJavaSeries())));
    legend.configure(legends.getNativeLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getNativeSeries())));
    legend.configure(legends.getGraphicsLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getGraphicsSeries())));
    legend.configure(legends.getStackLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getStackSeries())));
    legend.configure(legends.getCodeLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getCodeSeries())));
    legend.configure(legends.getOtherLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getOtherSeries())));
    legend.configure(legends.getTotalLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getTotalMemorySeries())));
    legend.configure(legends.getObjectsLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getObjectsSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    if (!getStage().hasUserUsedMemoryCapture()) {
      installProfilingInstructions(monitorPanel);
    }

    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(myRangeSelectionComponent, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));
    return panel;
  }

  static Icon getIconForSamplingMode(LiveAllocationSamplingMode mode) {
    // TODO(b/116430034): use real icons when they're done.
    switch (mode) {
      case FULL:
        return StudioIcons.Profiler.Events.ALLOCATION_TRACKING_FULL;
      case SAMPLED:
        return StudioIcons.Profiler.Events.ALLOCATION_TRACKING_SAMPLED;
      case NONE:
        return StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE;
    }
    throw new AssertionError("Unhandled sampling mode: " + mode);
  }

  private void buildContextMenu() {
    if (myRangeSelectionComponent == null) {
      return;
    }

    IdeProfilerComponents ideProfilerComponents = getIdeComponents();
    ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

    ProfilerAction exportHeapDumpAction = new ProfilerAction.Builder("Export...").setIcon(AllIcons.ToolbarDecorator.Export).build();
    contextMenuInstaller.installGenericContextMenu(
      myRangeSelectionComponent, exportHeapDumpAction,
      x -> getCaptureIntersectingWithMouseX(x) != null &&
           getCaptureIntersectingWithMouseX(x).isExportable(),
      x -> getIdeComponents().createExportDialog().open(
        () -> "Export capture to file",
        () -> MemoryProfiler.generateCaptureFileName(),
        () -> getCaptureIntersectingWithMouseX(x).getExportableExtension(),
        file -> getStage().getStudioProfilers().getIdeServices().saveFile(
          file,
          (output) -> {
            try {
              getCaptureIntersectingWithMouseX(x).saveToFile(output);
            }
            catch (IOException e) {
              getLogger().warn(e);
            }
          }, null)));
    contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, ContextMenuItem.SEPARATOR);

    if (!getStage().isLiveAllocationTrackingReady()) {
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myAllocationAction);
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myStopAllocationAction);
    }
    if (getStage().isNativeAllocationSamplingEnabled()) {
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myNativeAllocationAction);
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myStopNativeAllocationAction);
    }
    contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myForceGarbageCollectionAction);
    contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, ContextMenuItem.SEPARATOR);
    contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myHeapDumpAction);
    contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, ContextMenuItem.SEPARATOR);

    getProfilersView().installCommonMenuItems(myRangeSelectionComponent);
  }

  /**
   * Returns the memory capture object that intersects with the mouse X coordinate within {@link #myRangeSelectionComponent}.
   */
  @Nullable
  private CaptureObject getCaptureIntersectingWithMouseX(int mouseXLocation) {
    assert myRangeSelectionComponent != null;
    Range range = getStage().getTimeline().getViewRange();
    double pos = mouseXLocation / myRangeSelectionComponent.getSize().getWidth() * range.getLength() + range.getMin();
    CaptureDurationData<? extends CaptureObject> durationData = getStage().getIntersectingCaptureDuration(new Range(pos, pos));
    return durationData == null ? null : durationData.getCaptureEntry().getCaptureObject();
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    // The heap dump icon's contrast does not stand out as well as the record icon so we use a higher tones value.
    Icon heapDumpIcon = UIUtil.isUnderDarcula()
                        ? IconUtil.darker(StudioIcons.Profiler.Toolbar.HEAP_DUMP, 6)
                        : IconUtil.brighter(StudioIcons.Profiler.Toolbar.HEAP_DUMP, 6);
    RenderInstruction[] instructions;
    FontMetrics metrics = UIUtilities.getFontMetrics(parent, ProfilerFonts.H2_FONT);
    if (getStage().isLiveAllocationTrackingReady()) {
      instructions = new RenderInstruction[]{
        new TextInstruction(metrics, "Select a range to inspect allocations"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(metrics, "or click "),
        new IconInstruction(heapDumpIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
        new TextInstruction(metrics, " for a heap dump")
      };
    }
    else {
      instructions = new RenderInstruction[]{
        new TextInstruction(metrics, "Click the record button to inspect allocations"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(metrics, "or "),
        new IconInstruction(heapDumpIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
        new TextInstruction(metrics, " for a heap dump")
      };
    }
    InstructionsPanel panel = new InstructionsPanel.Builder(instructions)
      .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
      .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
      .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  private void captureObjectChanged() {
    // Forcefully ends the previous loading operation if it is still ongoing.
    stopLoadingUi();
    myCaptureObject = getStage().getCaptureSelection().getSelectedCapture();
    if (myCaptureObject == null) {
      resetRecordingButtons();
      myLayout.setShowingCaptureUi(false);
      return;
    }

    if (myCaptureObject.isDoneLoading()) {
      // If a capture is initiated on stage enter, we will not have gotten a chance to listen in on the capture done loading event.``
      captureObjectFinishedLoading();
    }
    else {
      disableRecordingButtons();
      myLayout.setLoadingUiVisible(true);
    }
  }

  private void captureObjectFinishedLoading() {
    resetRecordingButtons();
    // If the capture is an imported file, myRangeSelectionComponent is null.
    // If it is part of a profiler session, myRangeSelectionComponent is not null and should obtain the focus.
    if (myRangeSelectionComponent != null) {
      myRangeSelectionComponent.requestFocus();
    }

    if (myCaptureObject != getStage().getCaptureSelection().getSelectedCapture() || myCaptureObject == null) {
      return;
    }

    myLayout.setShowingCaptureUi(true);
  }

  private void stopLoadingUi() {
    if (myCaptureObject != null && myLayout.isLoadingUiVisible()) {
      myLayout.setLoadingUiVisible(false);
    }
  }

  private void showHeapDumpInProgress() {
    getComponent().removeAll();
    myHeapDumpLoadingPanel.setChildComponent(myLayout.getComponent());
    getComponent().add(myHeapDumpLoadingPanel.getComponent(), BorderLayout.CENTER);
    myHeapDumpLoadingPanel.startLoading();
  }

  private void hideHeapDumpInProgress() {
    myHeapDumpLoadingPanel.stopLoading();
    getComponent().removeAll();
    myHeapDumpLoadingPanel.setChildComponent(null);
    getComponent().add(myLayout.getComponent());
  }

  private static void configureStackedFilledLine(LineChart chart, Color color, RangedContinuousSeries series) {
    chart.configure(series, new LineConfig(color).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
  }

  /**
   * TODO currently we have slightly different icons for the MemoryClassSetView vs the MemoryInstanceDetailsView.
   * Re-investigate and see if they should share the same conditions.
   */
  @NotNull
  static Icon getValueObjectIcon(@NotNull ValueObject valueObject) {
    if (valueObject instanceof FieldObject) {
      FieldObject field = (FieldObject)valueObject;
      if (field.getValueType() == ValueObject.ValueType.ARRAY) {
        return getStackedIcon(field.getAsInstance(), StudioIcons.Profiler.Overlays.ARRAY_STACK, AllIcons.Debugger.Db_array);
      }
      else if (field.getValueType().getIsPrimitive()) {
        return AllIcons.Debugger.Db_primitive;
      }
      else {
        return getStackedIcon(field.getAsInstance(), StudioIcons.Profiler.Overlays.FIELD_STACK, PlatformIcons.FIELD_ICON);
      }
    }
    else if (valueObject instanceof ReferenceObject) {
      ReferenceObject referrer = (ReferenceObject)valueObject;
      if (referrer.getReferenceInstance().getIsRoot()) {
        return AllIcons.Hierarchy.Subtypes;
      }
      else if (referrer.getReferenceInstance().getValueType() == ValueObject.ValueType.ARRAY) {
        return getStackedIcon(referrer.getReferenceInstance(), StudioIcons.Profiler.Overlays.ARRAY_STACK, AllIcons.Debugger.Db_array);
      }
      else {
        return getStackedIcon(referrer.getReferenceInstance(), StudioIcons.Profiler.Overlays.FIELD_STACK, PlatformIcons.FIELD_ICON);
      }
    }
    else if (valueObject instanceof InstanceObject) {
      return getStackedIcon((InstanceObject)valueObject, StudioIcons.Profiler.Overlays.INTERFACE_STACK, PlatformIcons.INTERFACE_ICON);
    }
    else {
      return PlatformIcons.INTERFACE_ICON;
    }
  }

  private static Icon getStackedIcon(@Nullable InstanceObject instance, @NotNull Icon stackedIcon, @NotNull Icon nonStackedIcon) {
    return (instance == null || instance.getCallStackDepth() == 0)
           ? nonStackedIcon
           : stackedIcon;
  }

  @VisibleForTesting
  static class LiveAllocationSamplingModeRenderer extends ProfilerComboboxCellRenderer<LiveAllocationSamplingMode> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         LiveAllocationSamplingMode value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      append(value.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
