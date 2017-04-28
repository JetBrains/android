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
import com.android.tools.adtui.flat.FlatButton;
import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  private final JBList myThreads;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  private final Splitter mySplitter;

  @NotNull
  private final LoadingPanel myCaptureViewLoading;

  @Nullable
  private CpuCaptureView myCaptureView;

  @NotNull
  private final JComboBox<CpuProfiler.CpuProfilingAppStartRequest.Mode> myProfilingModesCombo;

  @NotNull
  private final CpuStageTooltipView myTooltipView;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(profilersView, stage);
    myStage = stage;

    stage.getAspect().addDependency(this)
      .onChange(CpuProfilerAspect.CAPTURE, this::updateCaptureState)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection)
      .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
      .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    stage.getStudioProfilers().getTimeline().getSelectionRange().addDependency(this)
      .onChange(Range.Aspect.RANGE, this::selectionChanged);

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
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel());
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlayPanel.add(overlay, BorderLayout.CENTER);
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    DetailedCpuUsage cpuUsage = getStage().getCpuUsage();
    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));
    lineChartPanel.add(lineChart, BorderLayout.CENTER);
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    CpuProfilerStage.CpuStageLegends legends = getStage().getLegends();
    final LegendComponent legend = new LegendComponent(legends);
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getCpuSeries())));
    legend.configure(legends.getOthersLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getOtherCpuSeries())));
    legend.configure(legends.getThreadsLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getThreadsCountSeries())));

    final JLabel stageHeader = new JLabel(getStage().getName());
    stageHeader.setBorder(MONITOR_LABEL_PADDING);
    stageHeader.setFont(AdtUiUtils.getFontProfilerTitle());
    stageHeader.setVerticalAlignment(SwingConstants.TOP);
    stageHeader.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(stageHeader, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));

    DurationDataRenderer<CpuCapture> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setLabelProvider(this::formatCaptureLabel)
        .setStroke(new BasicStroke(1))
        .setLabelColors(new Color(0x70000000, true), Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(getStage()::setAndSelectCapture)
        .build();


    lineChart.addCustomRenderer(traceRenderer);
    overlay.addDurationDataRenderer(traceRenderer);

    CpuThreadsModel model = myStage.getThreadStates();
    myThreads = new JBList(model);
    myThreads.addListSelectionListener((e) -> {
      // TODO: support selecting multiple threads simultaneously.
      int selectedIndex = myThreads.getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        if (myStage.getSelectedThread() != thread.getThreadId()) {
          myStage.setSelectedThread(thread.getThreadId());
          myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        }
      }
    });
    JScrollPane scrollingThreads = new MyScrollPane();
    scrollingThreads.setViewportView(myThreads);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    details.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    myTooltipView = new CpuStageTooltipView(myStage);
    TooltipComponent tooltip =
      new TooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(), myTooltipView.createComponent());
    // TODO: This needs to be refactored, because probably we don't handle mouse events
    //       properly when components are layered, currently mouse events should happen on the OverlayComponent.
    tooltip.registerListenersOn(overlay);
    tooltip.registerListenersOn(overlayPanel);
    details.add(tooltip, new TabularLayout.Constraint(1, 0, 2, 1));

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

    AxisComponent timeAxis = buildTimeAxis(profilers);
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));

    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myCaptureButton = new FlatButton();
    myCaptureButton.addActionListener(event -> capture());

    myCaptureViewLoading = getProfilersView().getIdeProfilerComponents().createLoadingPanel();
    myCaptureViewLoading.setLoadingText("Parsing capture...");

    updateCaptureState();

    myProfilingModesCombo = new FlatComboBox<>();
    JComboBoxView<CpuProfiler.CpuProfilingAppStartRequest.Mode, CpuProfilerAspect> profilingModes =
      new JComboBoxView<>(myProfilingModesCombo, stage.getAspect(), CpuProfilerAspect.PROFILING_MODE,
                          stage::getProfilingModes, stage::getProfilingMode, stage::setProfilingMode);
    profilingModes.bind();
    myProfilingModesCombo.setRenderer(new ProfilingModeCellRenderer());
  }

  private void selectionChanged() {
    Range range = getStage().getStudioProfilers().getTimeline().getSelectionRange();
    List<SeriesData<CpuCapture>> captures = getStage().getTraceDurations().getSeries().getDataSeries().getDataForXRange(range);
    for (SeriesData<CpuCapture> capture : captures) {
      Range c = new Range(capture.x, capture.x + capture.value.getDuration());
      if (!c.getIntersection(range).isEmpty()) {
        if (!capture.value.equals(getStage().getCapture())) {
          getStage().setCapture(capture.value);
        }
      }
    }
  }

  private static Logger getLog() {
    return Logger.getInstance(CpuProfilerStageView.class);
  }

  @VisibleForTesting
  static String formatTime(long micro) {
    // TODO unify with TimeAxisFormatter
    long mil = (micro / 1000) % 1000;
    long sec = (micro / (1000 * 1000)) % 60;
    long min = (micro / (1000 * 1000 * 60)) % 60;
    long hour = micro / (1000L * 1000L * 60L * 60L);

    return String.format("%02d:%02d:%02d.%03d", hour, min, sec, mil);
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(TOOLBAR_LAYOUT);

    toolbar.add(myProfilingModesCombo);
    toolbar.add(myCaptureButton);

    StudioProfilers profilers = getStage().getStudioProfilers();
    profilers.addDependency(this).onChange(ProfilerAspect.PROCESSES, () -> myCaptureButton.setEnabled(profilers.isProcessAlive()));
    myCaptureButton.setEnabled(profilers.isProcessAlive());

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  private String formatCaptureLabel(CpuCapture capture) {
    Range range = getStage().getStudioProfilers().getTimeline().getDataRange();

    long min = (long)(capture.getRange().getMin() - range.getMin());
    long max = (long)(capture.getRange().getMax() - range.getMin());
    return formatTime(min) + " - " + formatTime(max);
  }

  private void updateCaptureState() {
    myCaptureViewLoading.stopLoading();
    switch (myStage.getCaptureState()) {
      case IDLE:
        myCaptureButton.setEnabled(true);
        myCaptureButton.setText("");
        myCaptureButton.setToolTipText("Record a method trace");
        myCaptureButton.setIcon(ProfilerIcons.RECORD);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.RECORD));
        break;
      case CAPTURING:
        myCaptureButton.setEnabled(true);
        myCaptureButton.setText("");
        myCaptureButton.setToolTipText("Stop recording");
        myCaptureButton.setIcon(ProfilerIcons.STOP_RECORDING);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.STOP_RECORDING));
        break;
      case PARSING:
        myCaptureViewLoading.startLoading();
        mySplitter.setSecondComponent(myCaptureViewLoading.getComponent());
        break;
      case STARTING:
        myCaptureButton.setEnabled(false);
        myCaptureButton.setText("");
        myCaptureButton.setToolTipText("");
        break;
      case STOPPING:
        myCaptureButton.setEnabled(false);
        myCaptureButton.setText("Stopping record...");
        myCaptureButton.setToolTipText("");
    }
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      mySplitter.setSecondComponent(null);
      myCaptureView = null;
    }
    else {
      myCaptureView = new CpuCaptureView(this);
      mySplitter.setSecondComponent(myCaptureView.getComponent());
    }
  }

  private void updateCaptureElapsedTime() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      long elapsedTimeUs = myStage.getCaptureElapsedTimeUs();
      myCaptureButton.setText("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(elapsedTimeUs, elapsedTimeUs, true));
    }
  }

  private void capture() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      myStage.stopCapturing();

      FeatureTracker featureTracker = myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
      switch (myStage.getProfilingMode()) {
        case SAMPLED:
          featureTracker.trackTraceSampled();
          break;
        case INSTRUMENTED:
          featureTracker.trackTraceInstrumented();
          break;
        default:
          // Intentional no-op
          break;
      }
    }
    else {
      myStage.startCapturing();
    }
  }

  private void updateThreadSelection() {
    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = (CpuThreadsModel.RangedCpuThread)myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
      }
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

  private static class ProfilingModeCellRenderer extends ListCellRendererWrapper<CpuProfiler.CpuProfilingAppStartRequest.Mode> {
    @Override
    public void customize(JList list,
                          CpuProfiler.CpuProfilingAppStartRequest.Mode value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
      switch (value) {
        case SAMPLED:
          setText("Sampled");
          break;
        case INSTRUMENTED:
          setText("Instrumented");
          break;
        default:
          getLog().warn("Unexpected profiling mode received.");
      }
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

    public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list, UpdatableManager updatableManager) {
      myLabel = new JLabel();
      myLabel.setFont(AdtUiUtils.getFontDefault());
      Border rightSeparator = BorderFactory.createMatteBorder(0, 0, 0, 1, ProfilerColors.THREAD_LABEL_BORDER);
      Border marginLeft = new EmptyBorder(0, 10, 0, 0);
      myLabel.setBorder(new CompoundBorder(rightSeparator, marginLeft));
      myLabel.setOpaque(true);
      myUpdatableManager = updatableManager;
      myStateCharts = new HashMap<>();
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
      panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 15));
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
      // 1 is index of the selected color, 0 is of the non-selected
      // See more: {@link ProfilerColors#THREAD_STATES}
      stateChart.getColors().setColorIndex(isSelected ? 1 : 0);
      stateChart.setOpaque(true);

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
        overlay.setBackground(ProfilerColors.THREAD_HOVER_OVERLAY);
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
      StateChart<CpuProfilerStage.ThreadState> stateChart = new StateChart<>(model, ProfilerColors.THREAD_STATES);
      StateChartData data = new StateChartData(stateChart, model);
      stateChart.setHeightGap(0.40f);
      myStateCharts.put(tid, data);
      myUpdatableManager.register(model);
      return stateChart;
    }

    /**
     * Contains a state chart and its corresponding model.
     */
    private static class StateChartData {
      private StateChart<CpuProfilerStage.ThreadState> myChart;
      private StateChartModel<CpuProfilerStage.ThreadState> myModel;

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
