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

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.flat.FlatButton;
import com.android.tools.adtui.flat.FlatToggleButton;
import com.android.tools.adtui.instructions.*;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.PlatformIcons;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.adtui.instructions.InstructionsPanel.Builder.DEFAULT_PADDING_Y_PX;
import static com.android.tools.profilers.ProfilerLayout.*;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {
  @NotNull private final MemoryCaptureView myCaptureView = new MemoryCaptureView(getStage(), getIdeComponents());
  @NotNull private final MemoryHeapView myHeapView = new MemoryHeapView(getStage());
  @NotNull private final MemoryClassifierView myClassifierView = new MemoryClassifierView(getStage(), getIdeComponents());
  @NotNull private final MemoryClassGrouping myClassGrouping = new MemoryClassGrouping(getStage());
  @NotNull private final MemoryClassSetView myClassSetView = new MemoryClassSetView(getStage(), getIdeComponents());
  @NotNull private final MemoryInstanceDetailsView myInstanceDetailsView = new MemoryInstanceDetailsView(getStage(), getIdeComponents());
  @NotNull private final MemoryStageTooltipView myMemoryStageTooltipView = new MemoryStageTooltipView(getStage());

  @Nullable private CaptureObject myCaptureObject = null;

  @NotNull private final JBSplitter myMainSplitter = new JBSplitter(false);
  @NotNull private final JBSplitter myChartCaptureSplitter = new JBSplitter(true);
  @NotNull private final JPanel myCapturePanel;
  @Nullable private LoadingPanel myCaptureLoadingPanel;
  @NotNull private final JBSplitter myInstanceDetailsSplitter = new JBSplitter(true);

  @NotNull private JButton myHeapDumpButton;
  @NotNull private JButton myAllocationButton;
  @NotNull private final JLabel myCaptureElapsedTime;

  public MemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MemoryProfilerStage stage) {
    super(profilersView, stage);

    // Turns on the auto-capture selection functionality - this would select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected.
    getStage().enableSelectLatestCapture(true, SwingUtilities::invokeLater);

    myMainSplitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);
    myChartCaptureSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    myInstanceDetailsSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);

    myChartCaptureSplitter.setFirstComponent(buildMonitorUi());
    myCapturePanel = buildCaptureUi();
    myInstanceDetailsSplitter.setOpaque(true);
    myInstanceDetailsSplitter.setFirstComponent(myClassSetView.getComponent());
    myInstanceDetailsSplitter.setSecondComponent(myInstanceDetailsView.getComponent());
    myMainSplitter.setFirstComponent(myChartCaptureSplitter);
    myMainSplitter.setSecondComponent(myInstanceDetailsSplitter);
    myMainSplitter.setProportion(0.6f);
    getComponent().add(myMainSplitter, BorderLayout.CENTER);

    myHeapDumpButton = new FlatButton(StudioIcons.Profiler.Toolbar.HEAP_DUMP);
    myHeapDumpButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.HEAP_DUMP));
    myHeapDumpButton.setToolTipText("Dump Java heap");
    myHeapDumpButton.addActionListener(e -> {
      getStage().requestHeapDump();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackDumpHeap();
    });

    myCaptureElapsedTime = new JLabel("");
    myCaptureElapsedTime.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(12f));
    myCaptureElapsedTime.setBorder(new EmptyBorder(0, 5, 0, 0));
    myCaptureElapsedTime.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    myAllocationButton = new FlatButton();
    myAllocationButton.setText("");
    myAllocationButton
      .addActionListener(e -> {
        if (getStage().isTrackingAllocations()) {
          getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackRecordAllocations();
        }
        getStage().trackAllocations(!getStage().isTrackingAllocations());
      });
    myAllocationButton.setVisible(!getStage().useLiveAllocationTracking());

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, this::allocationTrackingChanged)
      .onChange(MemoryProfilerAspect.CURRENT_CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    captureObjectChanged();
    allocationTrackingChanged();
  }

  @Override
  public JComponent getToolbar() {
    JPanel toolBar = new JPanel(TOOLBAR_LAYOUT);
    JButton forceGarbageCollectionButton = new FlatButton(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION);
    forceGarbageCollectionButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION));
    forceGarbageCollectionButton.setToolTipText("Force garbage collection");
    forceGarbageCollectionButton.addActionListener(e -> {
      getStage().forceGarbageCollection();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackForceGc();
    });
    toolBar.add(forceGarbageCollectionButton);

    toolBar.add(myHeapDumpButton);
    toolBar.add(myAllocationButton);
    toolBar.add(myCaptureElapsedTime);

    StudioProfilers profilers = getStage().getStudioProfilers();
    Runnable toggleButtons = () -> {
      forceGarbageCollectionButton.setEnabled(profilers.isProcessAlive());
      myHeapDumpButton.setEnabled(profilers.isProcessAlive());
      myAllocationButton.setEnabled(profilers.isProcessAlive());
    };
    profilers.addDependency(this).onChange(ProfilerAspect.PROCESSES, toggleButtons);
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
  @NotNull
  JLabel getCaptureElapsedTimeLabel() {
    return myCaptureElapsedTime;
  }

  private void allocationTrackingChanged() {
    //TODO enable/disable hprof/allocation if they cannot be performed
    if (getStage().isTrackingAllocations()) {
      myAllocationButton.setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myAllocationButton.setToolTipText("Stop recording");
      myCaptureElapsedTime.setText("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(0, 0, true));
    }
    else {
      myCaptureElapsedTime.setText("");
      myAllocationButton.setIcon(StudioIcons.Profiler.Toolbar.RECORD);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myAllocationButton.setToolTipText("Record memory allocations");
    }
  }

  private void updateCaptureElapsedTime() {
    if (getStage().isTrackingAllocations() && !getStage().useLiveAllocationTracking()) {
      long elapsedTimeUs = TimeUnit.NANOSECONDS.toMicros(getStage().getAllocationTrackingElapsedTimeNs());
      myCaptureElapsedTime.setText("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(elapsedTimeUs, elapsedTimeUs, true));
    }
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

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

    DurationDataRenderer<GcDurationData> gcRenderer =
      new DurationDataRenderer.Builder<>(getStage().getGcStats(), Color.BLACK)
        .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
        .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconWidth() / 2f,
                         StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconHeight() / 2f)
        .build();
    lineChart.addCustomRenderer(gcRenderer);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel(), timeline.getViewRange());
    selection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlay.addDurationDataRenderer(gcRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    // Only shows allocation tracking visuals in pre-O, since we are always tracking in O+.
    if (!getStage().useLiveAllocationTracking()) {
      DurationDataRenderer<CaptureDurationData<CaptureObject>> allocationRenderer =
        new DurationDataRenderer.Builder<>(getStage().getAllocationInfosDurations(), Color.LIGHT_GRAY)
          .setDurationBg(ProfilerColors.MEMORY_ALLOC_BG)
          .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
          .setLabelProvider(
            data -> String.format("Allocation record (%s)", data.getDuration() == Long.MAX_VALUE ? "in progress" :
                                                            TimeAxisFormatter.DEFAULT
                                                              .getFormattedString(viewRange.getLength(), data.getDuration(), true)))
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

    DurationDataRenderer<CaptureDurationData<CaptureObject>> heapDumpRenderer =
      new DurationDataRenderer.Builder<>(getStage().getHeapDumpSampleDurations(), Color.DARK_GRAY)
        .setDurationBg(ProfilerColors.MEMORY_HEAP_DUMP_BG)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setLabelProvider(
          data -> String.format("Dump (%s)", data.getDuration() == Long.MAX_VALUE ? "in progress" :
                                             TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true)))
        .build();

    for (RangedContinuousSeries series : memoryUsage.getSeries()) {
      LineConfig config = lineChart.getLineConfig(series);
      int gray = (config.getColor().getBlue() + config.getColor().getRed() + config.getColor().getGreen()) / 3;
      LineConfig newConfig = LineConfig.copyOf(config).setColor(Gray.get(gray));
      heapDumpRenderer.addCustomLineConfig(series, newConfig);
    }
    lineChart.addCustomRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(heapDumpRenderer);

    RangeTooltipComponent tooltip =
      new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(),
                                myMemoryStageTooltipView.createComponent(), ProfilerLayeredPane.class);
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
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING).build();
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
    monitorPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    RenderInstruction[] instructions;
    if (getStage().useLiveAllocationTracking()) {
      TextInstruction allocInstruction = getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isMemorySnapshotEnabled() ?
                                         new TextInstruction(PROFILING_INSTRUCTIONS_FONT,
                                                             "Select a point/range to inspect snapshot/allocations") :
                                         new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "Select a range to inspect allocations");
      RenderInstruction[] liveAllocInstructions = {
        allocInstruction,
        new NewRowInstruction(DEFAULT_PADDING_Y_PX),
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "or click  "),
        new IconInstruction(StudioIcons.Profiler.Toolbar.HEAP_DUMP, PROFILING_INSTRUCTIONS_ICON_PADDING, JBColor.background()),
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "  for heap dump")
      };
      instructions = liveAllocInstructions;
    }
    else {
      RenderInstruction[] legacyInstructions = {
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "Click  "),
        new IconInstruction(StudioIcons.Profiler.Toolbar.RECORD, PROFILING_INSTRUCTIONS_ICON_PADDING, JBColor.background()),
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, " to record allocations"),
        new NewRowInstruction(DEFAULT_PADDING_Y_PX),
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "or  "),
        new IconInstruction(StudioIcons.Profiler.Toolbar.HEAP_DUMP, PROFILING_INSTRUCTIONS_ICON_PADDING, JBColor.background()),
        new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "  for heap dump")
      };
      instructions = legacyInstructions;
    }

    InstructionsPanel panel = new InstructionsPanel.Builder(instructions)
      .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
      .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC, PROFILING_INSTRUCTIONS_BACKGROUND_ARC)
      .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  @NotNull
  private JPanel buildCaptureUi() {
    JPanel toolbar = new JPanel(TOOLBAR_LAYOUT);
    toolbar.add(myCaptureView.getComponent());
    toolbar.add(myHeapView.getComponent());
    toolbar.add(myClassGrouping.getComponent());

    JPanel headingPanel = new JPanel(new BorderLayout());
    headingPanel.add(toolbar, BorderLayout.WEST);

    if (getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isMemoryCaptureFilterEnabled()) {
      FlatToggleButton button = new FlatToggleButton("", StudioIcons.Common.FILTER);
      headingPanel.add(button, BorderLayout.EAST);
      SearchComponent searchTextArea = getIdeComponents()
        .createProfilerSearchTextArea(getClass().getName(), ProfilerLayout.FILTER_TEXT_FIELD_WIDTH,
                                      ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS);
      searchTextArea.addOnFilterChange(pattern -> getStage().selectCaptureFilter(pattern));
      headingPanel.add(searchTextArea.getComponent(), BorderLayout.SOUTH);
      searchTextArea.getComponent().setVisible(false);
      button.addActionListener(event -> {
        searchTextArea.getComponent().setVisible(button.isSelected());
        if (button.isSelected()) {
          searchTextArea.setText("");
        }
        else {
          getStage().selectCaptureFilter(null);
        }
        headingPanel.revalidate();
      });

      headingPanel.registerKeyboardAction(event -> {
                                            button.setSelected(!button.isSelected());
                                            for (ActionListener listener : button.getActionListeners()) {
                                              listener.actionPerformed(event);
                                            }
                                          },
                                          KeyStroke.getKeyStroke(KeyEvent.VK_F, SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK),
                                          JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    JPanel capturePanel = new JPanel(new BorderLayout());
    capturePanel.add(headingPanel, BorderLayout.PAGE_START);
    capturePanel.add(myClassifierView.getComponent(), BorderLayout.CENTER);
    return capturePanel;
  }

  private void captureObjectChanged() {
    // Forcefully ends the previous loading operation if it is still ongoing.
    stopLoadingUi();
    myCaptureObject = getStage().getSelectedCapture();
    if (myCaptureObject == null) {
      myAllocationButton.setEnabled(true);
      myHeapDumpButton.setEnabled(true);
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
    myAllocationButton.setEnabled(true);
    myHeapDumpButton.setEnabled(true);
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
}
