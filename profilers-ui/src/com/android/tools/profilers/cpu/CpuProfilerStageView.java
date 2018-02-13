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
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.profilers.ProfilerColors.CPU_CAPTURE_BACKGROUND;
import static com.android.tools.profilers.ProfilerLayout.*;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {

  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  /**
   * Contains the status of the capture, e.g. "Starting record...", "Recording - XXmXXs", etc.
   */
  private final JLabel myCaptureStatus;
  private final JBList<CpuThreadsModel.RangedCpuThread> myThreads;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  @NotNull private final JBSplitter mySplitter;

  @NotNull private final LoadingPanel myCaptureViewLoading;

  @Nullable private CpuCaptureView myCaptureView;

  @NotNull private final JComboBox<ProfilingConfiguration> myProfilingConfigurationCombo;

  /**
   * Panel to let user know to take a capture.
   */
  @NotNull private final JPanel myHelpTipPanel;

  @NotNull private final SelectionComponent mySelection;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(profilersView, stage);
    myStage = stage;

    stage.getAspect().addDependency(this)
      .onChange(CpuProfilerAspect.CAPTURE_STATE, this::updateCaptureState)
      .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCaptureSelection)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection)
      .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
      .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    getTooltipBinder().bind(CpuUsageTooltip.class, CpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);
    StudioProfilers profilers = stage.getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();

    TabularLayout layout = new TabularLayout("*");
    JPanel details = new JPanel(layout);
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getThreadCountAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setHideTickAtMin(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    mySelection = new SelectionComponent(getStage().getSelectionModel(), timeline.getViewRange());
    mySelection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(mySelection);
    overlayPanel.add(overlay, BorderLayout.CENTER);
    installContextMenu();

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);

    DetailedCpuUsage cpuUsage = getStage().getCpuUsage();
    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    lineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    lineChart.setFillEndGap(true);

    CpuProfilerStage.CpuStageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE_CAPTURED));
    legend.configure(legends.getOthersLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_OTHER_USAGE_CAPTURED));
    legend.configure(legends.getThreadsLegend(),
                     new LegendConfig(LegendConfig.IconType.DASHED_LINE, ProfilerColors.THREADS_COUNT_CAPTURED));

    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    DurationDataRenderer<CpuTraceInfo> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(CPU_CAPTURE_BACKGROUND)
        .setLabelProvider(this::formatCaptureLabel)
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(traceInfo -> getStage().setAndSelectCapture(traceInfo.getTraceId()))
        .build();

    traceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    overlay.addDurationDataRenderer(traceRenderer);
    lineChart.addCustomRenderer(traceRenderer);

    DurationDataRenderer<DefaultDurationData> inProgressTraceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getInProgressTraceDuration(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(CPU_CAPTURE_BACKGROUND)
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .build();

    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    overlay.addDurationDataRenderer(inProgressTraceRenderer);
    lineChart.addCustomRenderer(inProgressTraceRenderer);

    if (!getStage().hasUserUsedCpuCapture()) {
      installProfilingInstructions(monitorPanel);
    }
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(mySelection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    CpuThreadsModel model = myStage.getThreadStates();
    myThreads = new JBList<>(model);
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
        if (myThreads.getSelectedIndex() < 0 && myThreads.getItemsCount() > 0) {
          myThreads.setSelectedIndex(0);
        }
      }
    });

    JScrollPane scrollingThreads = new MyScrollPane();
    scrollingThreads.setBorder(MONITOR_BORDER);
    scrollingThreads.setViewportView(myThreads);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    details.add(eventsComponent, new TabularLayout.Constraint(0, 0));
    RangeTooltipComponent tooltipComponent = createTooltip(overlayPanel, overlay, myThreads);
    eventsView.registerTooltip(tooltipComponent, getStage());
    details.add(tooltipComponent, new TabularLayout.Constraint(1, 0, 2, 1));

    // Double-clicking the chart should remove a capture selection if one exists.
    MouseAdapter doubleClick = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isConsumed()) {
          clearSelection();
        }
      }
    };
    overlay.addMouseListener(doubleClick);
    overlayPanel.addMouseListener(doubleClick);

    layout.setRowSizing(1, "4*");
    details.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    layout.setRowSizing(2, "6*");
    AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });
    details.add(timeAxisGuide, new TabularLayout.Constraint(2, 0));
    details.add(scrollingThreads, new TabularLayout.Constraint(2, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));

    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    myHelpTipPanel = new JPanel(new BorderLayout());
    InstructionsPanel infoMessage = new InstructionsPanel.Builder(
      new TextInstruction(INFO_MESSAGE_HEADER_FONT, "Thread details unavailable"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(INFO_MESSAGE_DESCRIPTION_FONT, "Click the record button "),
      new IconInstruction(StudioIcons.Profiler.Toolbar.RECORD, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
      new TextInstruction(INFO_MESSAGE_DESCRIPTION_FONT, " to start CPU profiling"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(INFO_MESSAGE_DESCRIPTION_FONT, "or select a capture in the timeline."))
      .setColors(JBColor.foreground(), null)
      .build();
    myHelpTipPanel.add(infoMessage, BorderLayout.CENTER);

    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myCaptureButton = new CommonButton();
    myCaptureButton.addActionListener(event -> capture());

    myCaptureStatus = new JLabel("");
    myCaptureStatus.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(12f));
    myCaptureStatus.setBorder(new EmptyBorder(0, 5, 0, 0));
    myCaptureStatus.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    myCaptureViewLoading = getProfilersView().getIdeProfilerComponents().createLoadingPanel(-1);
    myCaptureViewLoading.setLoadingText("Parsing capture...");

    myProfilingConfigurationCombo = new ComboBox<>();
    JComboBoxView<ProfilingConfiguration, CpuProfilerAspect> profilingConfiguration =
      new JComboBoxView<>(myProfilingConfigurationCombo, stage.getAspect(), CpuProfilerAspect.PROFILING_CONFIGURATION,
                          stage::getProfilingConfigurations, stage::getProfilingConfiguration, stage::setProfilingConfiguration);
    profilingConfiguration.bind();
    // Do not support keyboard accessibility until it is supported product-wide in Studio.
    myProfilingConfigurationCombo.setFocusable(false);
    myProfilingConfigurationCombo.addKeyListener(new KeyAdapter() {
      /**
       * Select the next item, skipping over any separators encountered
       */
      private void skipSeparators(int indexDelta) {
        int selectedIndex = myProfilingConfigurationCombo.getSelectedIndex() + indexDelta;
        if (selectedIndex < 0 || selectedIndex == myProfilingConfigurationCombo.getItemCount()) {
          return;
        }
        while (myProfilingConfigurationCombo.getItemAt(selectedIndex) == CpuProfilerStage.CONFIG_SEPARATOR_ENTRY) {
          selectedIndex += indexDelta;
        }
        myProfilingConfigurationCombo.setSelectedIndex(selectedIndex);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          skipSeparators(1);
          e.consume();
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP) {
          skipSeparators(-1);
          e.consume();
        }
      }
    });
    myProfilingConfigurationCombo.setRenderer(new ProfilingConfigurationRenderer());

    updateCaptureState();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStageView.class);
  }

  private void clearSelection() {
    getStage().getStudioProfilers().getTimeline().getSelectionRange().clear();
  }

  /**
   * Installs a context menu on {@link #mySelection}.
   */
  private void installContextMenu() {
    ContextMenuInstaller contextMenuInstaller = getIdeComponents().createContextMenuInstaller();
    // Add the item to trigger a recording
    installRecordMenuItem(contextMenuInstaller);

    // Add the item to export a trace file.
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isExportCpuTraceEnabled()) {
      installExportTraceMenuItem(contextMenuInstaller);
    }
    // TODO(b/73338399): add actions to navigate through captures.

    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(mySelection);
  }

  /**
   * Installs the {@link ContextMenuItem} corresponding to the "Export Trace" feature on {@link #mySelection}.
   */
  private void installExportTraceMenuItem(ContextMenuInstaller contextMenuInstaller) {
    ProfilerAction exportTrace = new ProfilerAction.Builder("Export trace...").setIcon(StudioIcons.Common.EXPORT).build();
    // TODO (b/73296572)  provide a default file name for exporting CPU trace file
    contextMenuInstaller.installGenericContextMenu(
      mySelection, exportTrace,
      x -> getTraceIntersectingWithMouseX(x) != null,
      x -> getIdeComponents().createExportDialog().open(
        () -> "Export trace as",
        () -> "trace",
        () -> "trace",
        file -> getStage().getStudioProfilers().getIdeServices().saveFile(
          file,
          (output) -> exportTraceFile(output, getTraceIntersectingWithMouseX(x)),
          null)));
    contextMenuInstaller.installGenericContextMenu(mySelection, ContextMenuItem.SEPARATOR);
  }

  /**
   * Install the {@link ContextMenuItem} corresponding to the Start/Stop recording action on {@link #mySelection}.
   */
  private void installRecordMenuItem(ContextMenuInstaller contextMenuInstaller) {
    ProfilerAction record = new ProfilerAction.Builder(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                                             ? "Stop recording" : "Record CPU trace")
      .setIcon(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                     ? StudioIcons.Profiler.Toolbar.STOP_RECORDING : StudioIcons.Profiler.Toolbar.RECORD)
      .setEnableBooleanSupplier(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                      || myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE)
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK))
      .setActionRunnable(() -> capture())
      .build();

    contextMenuInstaller.installGenericContextMenu(mySelection, record);
    contextMenuInstaller.installGenericContextMenu(mySelection, ContextMenuItem.SEPARATOR);
  }

  /**
   * Copies the content of the trace file corresponding to a {@link CpuTraceInfo} to a given {@link FileOutputStream}.
   */
  private static void exportTraceFile(FileOutputStream output, CpuTraceInfo traceInfo) {
    // Export trace file action is only called when "Export trace..." is enabled and that only happens with non-null traces
    assert traceInfo != null;

    // Copy temp trace file to the output stream.
    try (FileInputStream input = new FileInputStream(traceInfo.getTraceFilePath())) {
      FileUtil.copy(input, output);
    }
    catch (IOException e) {
      getLogger().warn("Failed to export CPU trace file:\n" + e);
    }
  }

  /**
   * Returns the trace ID of a capture that intersects with the mouse X coordinate within {@link #mySelection}.
   */
  private CpuTraceInfo getTraceIntersectingWithMouseX(int mouseXLocation) {
    Range range = getTimeline().getViewRange();
    double pos = mouseXLocation / mySelection.getSize().getWidth() * range.getLength() + range.getMin();
    return getStage().getIntersectingTraceInfo(new Range(pos, pos));
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    Icon recordIcon = UIUtil.isUnderDarcula()
                      ? IconUtil.darker(StudioIcons.Profiler.Toolbar.RECORD, 3)
                      : IconUtil.brighter(StudioIcons.Profiler.Toolbar.RECORD, 3);
    InstructionsPanel panel = new InstructionsPanel.Builder(new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "Click "),
                                                            new IconInstruction(recordIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
                                                            new TextInstruction(PROFILING_INSTRUCTIONS_FONT, " to start method profiling"))
      .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
      .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
      .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  private static class ProfilingConfigurationRenderer extends ColoredListCellRenderer<ProfilingConfiguration> {
    ProfilingConfigurationRenderer() {
      super();
      setIpad(new JBInsets(0, UIUtil.isUnderNativeMacLookAndFeel() ? 5 : UIUtil.getListCellHPadding(), 0, 0));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ProfilingConfiguration> list,
                                                  ProfilingConfiguration value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value == CpuProfilerStage.CONFIG_SEPARATOR_ENTRY) {
        TitledSeparator separator = new TitledSeparator("");
        separator.setBorder(new EmptyBorder(0, TitledSeparator.SEPARATOR_LEFT_INSET * -1, 0, TitledSeparator.SEPARATOR_RIGHT_INSET * -1));
        return separator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends ProfilingConfiguration> list,
                                         ProfilingConfiguration value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value == CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY) {
        setIcon(AllIcons.Actions.EditSource);
        append("Edit configurations...");
      }
      else {
        append(value.getName());
      }
    }
  }

  @NotNull
  private RangeTooltipComponent createTooltip(@NotNull JPanel overlayPanel,
                                              @NotNull JComponent overlay,
                                              @NotNull JBList<CpuThreadsModel.RangedCpuThread> threads) {
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();

    MouseListener usageListener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuUsageTooltip(myStage));
    overlay.addMouseListener(usageListener);
    overlayPanel.addMouseListener(usageListener);

    threads.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuThreadsTooltip(myStage)));
    threads.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = threads.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = threads.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuThreadsTooltip) {
            CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }
    });

    RangeTooltipComponent tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                                              timeline.getDataRange(), getTooltipPanel(), ProfilerLayeredPane.class);
    // TODO: This needs to be refactored, because probably we don't handle mouse events
    //       properly when components are layered, currently mouse events should happen on the OverlayComponent.
    tooltip.registerListenersOn(overlay);
    tooltip.registerListenersOn(overlayPanel);
    tooltip.registerListenersOn(threads);
    return tooltip;
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());

    toolbar.add(myProfilingConfigurationCombo);
    toolbar.add(Box.createHorizontalStrut(3));
    toolbar.add(myCaptureButton);
    toolbar.add(myCaptureStatus);

    StudioProfilers profilers = getStage().getStudioProfilers();
    profilers.addDependency(this).onChange(ProfilerAspect.SESSIONS, () -> myCaptureButton.setEnabled(profilers.isSessionAlive()));
    myCaptureButton.setEnabled(profilers.isSessionAlive());

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  private String formatCaptureLabel(CpuTraceInfo info) {
    Range range = getStage().getStudioProfilers().getTimeline().getDataRange();
    long min = (long)(info.getRange().getMin() - range.getMin());
    long max = (long)(info.getRange().getMax() - range.getMin());
    return TimeAxisFormatter.DEFAULT.getClockFormattedString(min) + " - " + TimeAxisFormatter.DEFAULT.getClockFormattedString(max);
  }

  private void updateCaptureState() {
    myCaptureViewLoading.stopLoading();
    switch (myStage.getCaptureState()) {
      case IDLE:
        myCaptureButton.setEnabled(true);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Record a method trace");
        myCaptureButton.setIcon(StudioIcons.Profiler.Toolbar.RECORD);
        myProfilingConfigurationCombo.setEnabled(true);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
        break;
      case CAPTURING:
        myCaptureButton.setEnabled(true);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Stop recording");
        myCaptureButton.setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING);
        myProfilingConfigurationCombo.setEnabled(false);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
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
        myProfilingConfigurationCombo.setEnabled(false);
        break;
      case START_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
      case STOPPING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Stopping record...");
        myCaptureButton.setToolTipText("");
        myProfilingConfigurationCombo.setEnabled(false);
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
    else if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE) {
      // Capture has finished. Create a CpuCaptureView to display it.
      myCaptureView = new CpuCaptureView(this);
      mySplitter.setSecondComponent(myCaptureView.getComponent());
    }
  }

  private void updateCaptureElapsedTime() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      long elapsedTimeUs = myStage.getCaptureElapsedTimeUs();
      myCaptureStatus.setText("Recording - " + TimeAxisFormatter.DEFAULT.getClockFormattedString(elapsedTimeUs));
    }
  }

  private void capture() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      myStage.stopCapturing();
    }
    else {
      myStage.startCapturing();
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

  private static class MyScrollPane extends JBScrollPane {

    private MyScrollPane() {
      super();
      getVerticalScrollBar().setOpaque(false);
    }

    @Override
    protected JViewport createViewport() {
      if (SystemInfo.isMac) {
        return super.createViewport();
      }
      // Overrides it because, when not on mac, JBViewport adds the width of the scrollbar to the right inset of the border,
      // which would consequently misplace the threads state chart.
      return new JViewport();
    }
  }

  private static class ThreadCellRenderer implements ListCellRenderer<CpuThreadsModel.RangedCpuThread> {

    /**
     * Label to display the thread name on a cell.
     */
    private final JLabel myLabel;

    /**
     * Maps a thread id to a {@link StateChartData} containing the chart that should be rendered on the cell corresponding to that thread.
     */
    private final Map<Integer, StateChartData> myStateCharts;

    /**
     * Keep the index of the item currently hovered.
     */
    private int myHoveredIndex = -1;

    /**
     * {@link UpdatableManager} responsible for managing the threads state charts.
     */
    private final UpdatableManager myUpdatableManager;

    /**
     * Maps a {@link StateChart} to a {@link EnumColors} helper class to return the proper color object for the {@link StateChart}
     */
    @NotNull
    private final Map<StateChart<CpuProfilerStage.ThreadState>, EnumColors<CpuProfilerStage.ThreadState>> myColors;

    public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list, UpdatableManager updatableManager) {
      myLabel = new JLabel();
      myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
      Border rightSeparator = BorderFactory.createMatteBorder(0, 0, 0, 1, ProfilerColors.THREAD_LABEL_BORDER);
      Border marginLeft = new EmptyBorder(0, 10, 0, 0);
      myLabel.setBorder(new CompoundBorder(rightSeparator, marginLeft));
      myLabel.setOpaque(true);
      myUpdatableManager = updatableManager;
      myStateCharts = new HashMap<>();
      myColors = new HashMap<>();
      list.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          Point p = new Point(e.getX(), e.getY());
          myHoveredIndex = list.locationToIndex(p);
        }
      });
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  CpuThreadsModel.RangedCpuThread value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
      panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, JBUI.scale(15)));
      panel.setBackground(list.getBackground());

      myLabel.setText(value.getName());
      myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
      myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);

      // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
      // corresponding to each thread and their models. StateChart#setModel is currently expensive and will make StateChart#render
      // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
      // recalculating the render states. This causes the rendering time to be substantially improved.
      int tid = value.getThreadId();
      StateChartModel<CpuProfilerStage.ThreadState> model = value.getModel();
      if (myStateCharts.containsKey(tid) && !model.equals(myStateCharts.get(tid).getModel())) {
        // The model associated to the tid has changed. That might have happened because the tid was recycled and
        // assigned to another thread. The current model needs to be unregistered.
        myUpdatableManager.unregister(myStateCharts.get(tid).getModel());
      }
      StateChart<CpuProfilerStage.ThreadState> stateChart = getOrCreateStateChart(tid, model);
      stateChart.setOpaque(true);
      // 1 is index of the selected color, 0 is of the non-selected
      // See more: {@link ProfilerColors#THREAD_STATES}
      myColors.get(stateChart).setColorIndex(isSelected ? 1 : 0);

      if (isSelected) {
        // Cell is selected. Update its background accordingly.
        panel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
        myLabel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
        myLabel.setForeground(ProfilerColors.SELECTED_THREAD_LABEL_TEXT);
        // As the state chart is opaque the selected background wouldn't be visible
        // if we didn't set the opaqueness to false if the cell is selected.
        stateChart.setOpaque(false);
      }
      else if (myHoveredIndex == index) {
        // Cell is hovered. Draw the hover overlay over it.
        JPanel overlay = new JPanel();
        overlay.setBackground(ProfilerColors.DEFAULT_HOVER_COLOR);
        panel.add(overlay, new TabularLayout.Constraint(0, 0, 2));
      }

      panel.add(myLabel, new TabularLayout.Constraint(0, 0));
      panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
      return panel;
    }

    /**
     * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
     */
    private StateChart<CpuProfilerStage.ThreadState> getOrCreateStateChart(int tid, StateChartModel<CpuProfilerStage.ThreadState> model) {
      if (myStateCharts.containsKey(tid) && myStateCharts.get(tid).getModel().equals(model)) {
        // State chart is already saved on the map. Return it.
        return myStateCharts.get(tid).getChart();
      }
      // The state chart corresponding to the thread is not stored on the map. Create a new one.
      EnumColors<CpuProfilerStage.ThreadState> enumColors = ProfilerColors.THREAD_STATES.build();
      StateChart<CpuProfilerStage.ThreadState> stateChart = new StateChart<>(model, enumColors::getColor);
      StateChartData data = new StateChartData(stateChart, model);
      stateChart.setHeightGap(0.40f);
      myStateCharts.put(tid, data);
      myColors.put(stateChart, enumColors);
      myUpdatableManager.register(model);
      return stateChart;
    }

    /**
     * Contains a state chart and its corresponding model.
     */
    private static class StateChartData {
      private final StateChart<CpuProfilerStage.ThreadState> myChart;
      private final StateChartModel<CpuProfilerStage.ThreadState> myModel;

      public StateChartData(StateChart<CpuProfilerStage.ThreadState> chart, StateChartModel<CpuProfilerStage.ThreadState> model) {
        myChart = chart;
        myModel = model;
      }

      public StateChart<CpuProfilerStage.ThreadState> getChart() {
        return myChart;
      }

      public StateChartModel<CpuProfilerStage.ThreadState> getModel() {
        return myModel;
      }
    }
  }
}
