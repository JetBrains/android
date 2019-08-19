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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_WIDTH;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_HISTORY_SIZE;
import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;
import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.FilterComponent;
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
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.RenderInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
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
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.StageView;
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
import com.intellij.ui.Gray;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Insets;
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

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStageView.class);
  }
  private static final String RECORD_TEXT = "Record";
  private static final String STOP_TEXT = "Stop";

  @NotNull private final MemoryCaptureView myCaptureView = new MemoryCaptureView(getStage(), getIdeComponents());
  @NotNull private final MemoryHeapView myHeapView = new MemoryHeapView(getStage());
  @NotNull private final MemoryClassifierView myClassifierView = new MemoryClassifierView(getStage(), getIdeComponents());
  @NotNull private final MemoryClassGrouping myClassGrouping = new MemoryClassGrouping(getStage());
  @NotNull private final MemoryInstanceFilterView myInstanceFilterView = new MemoryInstanceFilterView(getStage());
  @NotNull private final MemoryClassSetView myClassSetView = new MemoryClassSetView(getStage(), getIdeComponents());
  @NotNull private final MemoryInstanceDetailsView myInstanceDetailsView = new MemoryInstanceDetailsView(getStage(), getIdeComponents());
  @Nullable private RangeSelectionComponent myRangeSelectionComponent;
  @Nullable private CaptureObject myCaptureObject = null;

  @NotNull private final JBSplitter myMainSplitter = new JBSplitter(false);
  @NotNull private final JBSplitter myChartCaptureSplitter = new JBSplitter(true);
  @NotNull private final JPanel myCapturePanel;
  @Nullable private LoadingPanel myCaptureLoadingPanel;
  @NotNull private final JBSplitter myInstanceDetailsSplitter = new JBSplitter(true);

  @NotNull private JButton myForceGarbageCollectionButton;
  @NotNull private JButton myHeapDumpButton;
  @NotNull private JButton myAllocationButton;
  @NotNull private JComboBox myAllocationSamplingRateDropDown;
  @NotNull private ProfilerAction myForceGarbageCollectionAction;
  @NotNull private ProfilerAction myHeapDumpAction;
  @NotNull private ProfilerAction myAllocationAction;
  @NotNull private ProfilerAction myStopAllocationAction;
  @NotNull private final JLabel myCaptureElapsedTime;
  @NotNull private final JLabel myCaptureInfoMessage;
  @NotNull private final JLabel myAllocationSamplingRateLabel;

  @NotNull private DurationDataRenderer<GcDurationData> myGcDurationDataRenderer;
  @NotNull private DurationDataRenderer<AllocationSamplingRateDurationData> myAllocationSamplingRateRenderer;

  public MemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MemoryProfilerStage stage) {
    super(profilersView, stage);

    // Turns on the auto-capture selection functionality - this would select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected.
    getStage().enableSelectLatestCapture(true, SwingUtilities::invokeLater);

    getTooltipBinder().bind(MemoryUsageTooltip.class, MemoryUsageTooltipView::new);
    getTooltipBinder().bind(LifecycleTooltip.class, LifecycleTooltipView::new);
    getTooltipBinder().bind(UserEventTooltip.class, UserEventTooltipView::new);

    myMainSplitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);
    myChartCaptureSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    myInstanceDetailsSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);

    // Do not initialize the monitor UI if it only contains heap dump data.
    // In this case, myRangeSelectionComponent is null and we will not build the context menu.
    if (!getStage().isMemoryCaptureOnly()) {
      myChartCaptureSplitter.setFirstComponent(buildMonitorUi());
    }

    myCaptureInfoMessage = new JLabel(StudioIcons.Common.WARNING);
    myCaptureInfoMessage.setBorder(TOOLBAR_ICON_BORDER);
    // preset the minimize size of the info to only show the icon, so the text can be truncated when the user resizes the vertical splitter.
    myCaptureInfoMessage.setMinimumSize(myCaptureInfoMessage.getPreferredSize());
    myCaptureInfoMessage.setVisible(false);
    myCapturePanel = buildCaptureUi();
    myInstanceDetailsSplitter.setOpaque(true);
    myInstanceDetailsSplitter.setFirstComponent(myClassSetView.getComponent());
    myInstanceDetailsSplitter.setSecondComponent(myInstanceDetailsView.getComponent());
    myMainSplitter.setFirstComponent(myChartCaptureSplitter);
    myMainSplitter.setSecondComponent(myInstanceDetailsSplitter);
    myMainSplitter.setProportion(0.6f);
    getComponent().add(myMainSplitter, BorderLayout.CENTER);

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
    myAllocationButton.setVisible(!getStage().useLiveAllocationTracking());
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

    myAllocationSamplingRateLabel = new JLabel("Allocation Tracking");
    myAllocationSamplingRateLabel.setBorder(JBUI.Borders.empty(0, 8));
    myAllocationSamplingRateDropDown = new ProfilerCombobox();

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, this::allocationTrackingChanged)
      .onChange(MemoryProfilerAspect.CURRENT_CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, this::updateCaptureInfoMessage);

    captureObjectChanged();
    allocationTrackingChanged();
    buildContextMenu();
  }

  @Override
  public boolean isToolbarVisible() {
    return !getStage().isMemoryCaptureOnly();
  }

  @VisibleForTesting
  JButton getGarbageCollectionButtion() {
    return myForceGarbageCollectionButton;
  }

  @VisibleForTesting
  JButton getHeapDumpButton() {
    return myHeapDumpButton;
  }

  @VisibleForTesting
  JButton getAllocationButton() {
    return myAllocationButton;
  }

  @VisibleForTesting
  JLabel getAllocationCaptureElaspedTimeLabel() {
    return myCaptureElapsedTime;
  }

  @VisibleForTesting
  JComboBox getAllocationSamplingRateDropDown() {
    return myAllocationSamplingRateDropDown;
  }

  @VisibleForTesting
  JLabel getAllocationSamplingRateLabel() {
    return myAllocationSamplingRateLabel;
  }

  @VisibleForTesting
  DurationDataRenderer<GcDurationData> getGcDurationDataRenderer() {
    return myGcDurationDataRenderer;
  }

  @VisibleForTesting
  DurationDataRenderer<AllocationSamplingRateDurationData> getAllocationSamplingRateRenderer() {
    return myAllocationSamplingRateRenderer;
  }

  @Override
  public JComponent getToolbar() {
    JPanel toolBar = new JPanel(createToolbarLayout());
    toolBar.add(myForceGarbageCollectionButton);
    toolBar.add(myHeapDumpButton);
    if (getStage().useLiveAllocationTracking() &&
        getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()) {
      JComboBoxView sampleRateComboView =
        new JComboBoxView<LiveAllocationSamplingMode, MemoryProfilerAspect>(myAllocationSamplingRateDropDown,
                                                                            getStage().getAspect(),
                                                                            MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE,
                                                                            getStage()::getSupportedLiveAllocationSamplingMode,
                                                                            getStage()::getLiveAllocationSamplingMode,
                                                                            getStage()::requestLiveAllocationSamplingModeUpdate);
      sampleRateComboView.bind();
      myAllocationSamplingRateDropDown.setRenderer(new LiveAllocationSamplingModeRenderer());
      toolBar.add(myAllocationSamplingRateLabel);
      toolBar.add(myAllocationSamplingRateDropDown);
    } else {
      toolBar.add(myAllocationButton);
      toolBar.add(myCaptureElapsedTime);
    }

    StudioProfilers profilers = getStage().getStudioProfilers();
    Runnable toggleButtons = () -> {
      boolean isAlive = profilers.getSessionsManager().isSessionAlive();
      myForceGarbageCollectionButton.setEnabled(isAlive);
      myHeapDumpButton.setEnabled(isAlive);
      myAllocationButton.setEnabled(isAlive);
      myAllocationSamplingRateDropDown.setEnabled(isAlive);
    };
    profilers.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, toggleButtons);
    toggleButtons.run();

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolBar, BorderLayout.WEST);
    return panel;
  }

  @VisibleForTesting
  @NotNull
  public Splitter getMainSplitter() {
    return myMainSplitter;
  }

  @VisibleForTesting
  @NotNull
  public Splitter getChartCaptureSplitter() {
    return myChartCaptureSplitter;
  }

  @VisibleForTesting
  @NotNull
  public JPanel getCapturePanel() {
    return myCapturePanel;
  }

  @VisibleForTesting
  @NotNull
  MemoryCaptureView getCaptureView() {
    return myCaptureView;
  }

  @VisibleForTesting
  @NotNull
  MemoryHeapView getHeapView() {
    return myHeapView;
  }

  @VisibleForTesting
  @NotNull
  MemoryClassGrouping getClassGrouping() {
    return myClassGrouping;
  }

  @VisibleForTesting
  @NotNull
  MemoryClassifierView getClassifierView() {
    return myClassifierView;
  }

  @VisibleForTesting
  @NotNull
  MemoryClassSetView getClassSetView() {
    return myClassSetView;
  }

  @VisibleForTesting
  @NotNull
  MemoryInstanceDetailsView getInstanceDetailsView() {
    return myInstanceDetailsView;
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
    return myCaptureInfoMessage;
  }

  private void allocationTrackingChanged() {
    if (getStage().isTrackingAllocations()) {
      myAllocationButton.setText(STOP_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myAllocationButton.setToolTipText("Stop recording");
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(0));
    }
    else {
      myCaptureElapsedTime.setText("");
      myAllocationButton.setText(RECORD_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myAllocationButton.setToolTipText("Record memory allocations");
    }
  }

  private void updateCaptureElapsedTime() {
    if (getStage().isTrackingAllocations() && !getStage().useLiveAllocationTracking()) {
      long elapsedTimeUs = TimeUnit.NANOSECONDS.toMicros(getStage().getAllocationTrackingElapsedTimeNs());
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(elapsedTimeUs));
    }
  }

  private void updateCaptureInfoMessage() {
    CaptureObject capture = getStage().getSelectedCapture();
    String infoMessage = capture == null ? null : capture.getInfoMessage();

    if (infoMessage != null) {
      myCaptureInfoMessage.setVisible(true);
      myCaptureInfoMessage.setText(infoMessage);
      myCaptureInfoMessage.setToolTipText(infoMessage);
    }
    else {
      myCaptureInfoMessage.setVisible(false);
    }
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();
    myRangeSelectionComponent = new RangeSelectionComponent(getStage().getRangeSelectionModel(), timeline.getViewRange());
    myRangeSelectionComponent.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    RangeTooltipComponent tooltip =
      new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(),
                                getTooltipPanel(), getProfilersView().getComponent(),
                                () -> myRangeSelectionComponent.shouldShowSeekComponent());
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
    if (getStage().useLiveAllocationTracking()) {
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
    lineChart.configure(memoryUsage.getTotalMemorySeries(), new LineConfig(new Color(0, 0, 0, 0)));
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    lineChart.setFillEndGap(true);

    myGcDurationDataRenderer = new DurationDataRenderer.Builder<>(memoryUsage.getGcDurations(), Color.BLACK)
        .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
        // Need to offset the GcDurationData by the margin difference between the overlay component and the
        // line chart. This ensures we are able to render the Gc events in the proper locations on the line.
        .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconWidth() / 2f,
                         StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconHeight() / 2f)
        .setHostInsets(new Insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
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
    if (!getStage().useLiveAllocationTracking()) {
      DurationDataRenderer<CaptureDurationData<CaptureObject>> allocationRenderer =
        new DurationDataRenderer.Builder<>(getStage().getAllocationInfosDurations(), Color.LIGHT_GRAY)
          .setDurationBg(ProfilerColors.MEMORY_ALLOC_BG)
          .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
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
    else if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()){
      myAllocationSamplingRateRenderer = new DurationDataRenderer.Builder<>(getStage().getAllocationSamplingRateDurations(), Color.BLACK)
        .setDurationBg(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
        .setIconMapper(durationData -> {
            LiveAllocationSamplingMode mode = LiveAllocationSamplingMode
              .getModeFromFrequency(durationData.getCurrentRate().getSamplingNumInterval());
            return getIconForSamplingMode(mode);
          })
        .setLabelOffsets(-StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconWidth() / 2f,
                           StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconHeight() / 2f)
        .setHostInsets(new Insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
        .setClickRegionPadding(0, 0)
        .setHoverHandler(getStage().getTooltipLegends().getSamplingRateDurationLegend()::setPickData)
        .build();
      lineChart.addCustomRenderer(myAllocationSamplingRateRenderer);
      overlay.addDurationDataRenderer(myAllocationSamplingRateRenderer);
    }

    DurationDataRenderer<CaptureDurationData<CaptureObject>> heapDumpRenderer =
      new DurationDataRenderer.Builder<>(getStage().getHeapDumpSampleDurations(), Color.DARK_GRAY)
        .setDurationBg(ProfilerColors.MEMORY_HEAP_DUMP_BG)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setLabelProvider(
          data -> String.format("Dump (%s)", data.getDurationUs() == Long.MAX_VALUE ? "in progress" :
                                             TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDurationUs(), true)))
        .build();

    for (RangedContinuousSeries series : memoryUsage.getSeries()) {
      LineConfig config = lineChart.getLineConfig(series);
      int gray = (config.getColor().getBlue() + config.getColor().getRed() + config.getColor().getGreen()) / 3;
      LineConfig newConfig = LineConfig.copyOf(config).setColor(Gray.get(gray));
      heapDumpRenderer.addCustomLineConfig(series, newConfig);
    }
    lineChart.addCustomRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(heapDumpRenderer);

    overlay.addMouseListener(new ProfilerTooltipMouseAdapter(getStage(), () -> new MemoryUsageTooltip(getStage())));
    overlayPanel.addMouseListener(new ProfilerTooltipMouseAdapter(getStage(), () -> new MemoryUsageTooltip(getStage())));


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

    if (!getStage().useLiveAllocationTracking()) {
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myAllocationAction);
      contextMenuInstaller.installGenericContextMenu(myRangeSelectionComponent, myStopAllocationAction);
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
    Range range = getTimeline().getViewRange();
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
    if (getStage().useLiveAllocationTracking()) {
      RenderInstruction[] liveAllocInstructions = {
        new TextInstruction(metrics, "Select a range to inspect allocations"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(metrics, "or click "),
        new IconInstruction(heapDumpIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
        new TextInstruction(metrics, " for a heap dump")
      };
      instructions = liveAllocInstructions;
    }
    else {
      RenderInstruction[] legacyInstructions = {
        new TextInstruction(metrics, "Click the record button to inspect allocations"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(metrics, "or "),
        new IconInstruction(heapDumpIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
        new TextInstruction(metrics, " for a heap dump")
      };
      instructions = legacyInstructions;
    }
    InstructionsPanel panel = new InstructionsPanel.Builder(instructions)
      .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
      .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
      .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  @NotNull
  private JPanel buildCaptureUi() {
    JPanel capturePanel = new JPanel(new BorderLayout());

    JPanel toolbar = new JPanel(createToolbarLayout());
    toolbar.add(myCaptureView.getComponent());
    toolbar.add(myHeapView.getComponent());
    toolbar.add(myClassGrouping.getComponent());
    toolbar.add(myInstanceFilterView.getComponent());
    if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()) {
      toolbar.add(myCaptureInfoMessage);
    }

    JPanel headingPanel = new JPanel(new BorderLayout());
    JPanel buttonToolbar = new JPanel(createToolbarLayout());
    buttonToolbar.setBorder(new JBEmptyBorder(3, 0, 0, 0));
    if (!getStage().isMemoryCaptureOnly()) {
      buttonToolbar.add(getSelectionTimeLabel());
    }
    if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isMemoryCaptureFilterEnabled()) {
      CommonToggleButton button = FilterComponent.createFilterToggleButton();
      buttonToolbar.add(new FlatSeparator());
      buttonToolbar.add(button);
      FilterComponent filterComponent =
        new FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS);

      filterComponent.getModel().setFilterHandler(getStage().getFilterHandler());
      headingPanel.add(filterComponent, BorderLayout.SOUTH);
      filterComponent.setVisible(false);
      filterComponent.setBorder(new JBEmptyBorder(0, 4, 0, 0));
      FilterComponent.configureKeyBindingAndFocusBehaviors(capturePanel, filterComponent, button);
    }

    // Add the right side toolbar so that it is on top of the truncated |myCaptureInfoMessage|.
    headingPanel.add(buttonToolbar, BorderLayout.EAST);
    headingPanel.add(toolbar, BorderLayout.WEST);

    capturePanel.add(headingPanel, BorderLayout.PAGE_START);
    capturePanel.add(myClassifierView.getComponent(), BorderLayout.CENTER);
    return capturePanel;
  }

  private void captureObjectChanged() {
    // Forcefully ends the previous loading operation if it is still ongoing.
    stopLoadingUi();
    myCaptureObject = getStage().getSelectedCapture();
    if (myCaptureObject == null) {
      boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
      myAllocationButton.setEnabled(isAlive);
      myHeapDumpButton.setEnabled(isAlive);
      myChartCaptureSplitter.setSecondComponent(null);
      return;
    }

    if (myCaptureObject.isDoneLoading()) {
      // If a capture is initiated on stage enter, we will not have gotten a chance to listen in on the capture done loading event.``
      captureObjectFinishedLoading();
    }
    else {
      myAllocationButton.setEnabled(false);
      myHeapDumpButton.setEnabled(false);
      myCaptureLoadingPanel = getProfilersView().getIdeProfilerComponents().createLoadingPanel(-1);
      myCaptureLoadingPanel.setLoadingText("Fetching results");
      myCaptureLoadingPanel.startLoading();
      myChartCaptureSplitter.setSecondComponent(myCaptureLoadingPanel.getComponent());
    }
  }

  private void captureObjectFinishedLoading() {
    boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
    myAllocationButton.setEnabled(isAlive);
    // If the capture is an imported file, myRangeSelectionComponent is null.
    // If it is part of a profiler session, myRangeSelectionComponent is not null and should obtain the focus.
    if (myRangeSelectionComponent != null) {
      myRangeSelectionComponent.requestFocus();
    }
    myHeapDumpButton.setEnabled(isAlive);
    if (myCaptureObject != getStage().getSelectedCapture() || myCaptureObject == null) {
      return;
    }

    stopLoadingUi();
    myChartCaptureSplitter.setSecondComponent(myCapturePanel);
  }

  private void stopLoadingUi() {
    if (myCaptureObject == null || myCaptureLoadingPanel == null) {
      return;
    }

    myCaptureLoadingPanel.stopLoading();
    myCaptureLoadingPanel = null;
    myChartCaptureSplitter.setSecondComponent(null);
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
