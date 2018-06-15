/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.MouseEventHandler;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

import static com.android.tools.profilers.ProfilerColors.CPU_CAPTURE_BACKGROUND;
import static com.android.tools.profilers.ProfilerLayout.*;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;

abstract class CpuUsageView extends JBPanel {
  @NotNull protected final CpuProfilerStage myStage;
  @NotNull protected final SelectionComponent mySelectionComponent;
  @NotNull protected final OverlayComponent myOverlayComponent;

  private CpuUsageView(@NotNull CpuProfilerStage stage) {
    super(new TabularLayout("*", "*"));
    myStage = stage;
    // We only show the sparkline if we are over the cpu usage chart. The cpu usage
    // chart is under the overlay component so using the events captured from the overlay
    // component tell us if we are over the right area.
    mySelectionComponent = new SelectionComponent(myStage.getSelectionModel(), myStage.getStudioProfilers().getTimeline().getViewRange());
    mySelectionComponent.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);

    myOverlayComponent = new OverlayComponent(mySelectionComponent);

    MouseEventHandler handler = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        // |CpuUsageView| does not receive any mouse events, because all mouse events are consumed by |OverlayComponent|
        // We're dispatching them manually, so that |CpuProfilerStageView| could register CPU context menus or other mouse events
        // directly into |CpuUsageView| instead of |OverlayComponent|.
        dispatchEvent(SwingUtilities.convertMouseEvent(myOverlayComponent, event, CpuUsageView.this));
      }
    };
    myOverlayComponent.addMouseListener(handler);
    myOverlayComponent.addMouseMotionListener(handler);

    setBorder(MONITOR_BORDER);
    setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
  }

  /**
   * @return true if the blue seek component from {@link RangeTooltipComponent} should be visible when mouse is over {@link CpuUsageView}.
   * @see {@link RangeTooltipComponent#myShowSeekComponent}
   */
  boolean showTooltipSeekComponent() {
    return mySelectionComponent.getMode() != SelectionComponent.Mode.MOVE;
  }

  protected String formatCaptureLabel(@NotNull CpuTraceInfo info) {
    Range range = myStage.getStudioProfilers().getTimeline().getDataRange();
    long min = (long)(info.getRange().getMin() - range.getMin());
    long max = (long)(info.getRange().getMax() - range.getMin());
    return String.format("%s - %s", TimeFormatter.getFullClockString(min), TimeFormatter.getFullClockString(max));
  }

  static final class NormalModeView extends CpuUsageView {

    NormalModeView(@NotNull CpuProfilerStage stage) {
      super(stage);

      final JPanel axisPanel = new JBPanel(new BorderLayout());
      configureAxisPanel(axisPanel);

      final JPanel legendPanel = new JBPanel(new BorderLayout());
      configureLegendPanel(legendPanel);

      final JPanel overlayPanel = new JBPanel(new BorderLayout());
      configureOverlayPanel(overlayPanel, myOverlayComponent);

      final JPanel lineChartPanel = new JBPanel(new BorderLayout());
      configureLineChart(lineChartPanel, myOverlayComponent);

      // Panel that represents the cpu utilization.
      // Order is important
      add(axisPanel, new TabularLayout.Constraint(0, 0));
      add(legendPanel, new TabularLayout.Constraint(0, 0));
      add(overlayPanel, new TabularLayout.Constraint(0, 0));
      add(mySelectionComponent, new TabularLayout.Constraint(0, 0));
      add(lineChartPanel, new TabularLayout.Constraint(0, 0));
    }

    private void configureAxisPanel(@NotNull JPanel axisPanel) {
      axisPanel.setOpaque(false);
      final AxisComponent leftAxis = new AxisComponent(myStage.getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT);
      leftAxis.setShowAxisLine(false);
      leftAxis.setShowMax(true);
      leftAxis.setShowUnitAtMax(true);
      leftAxis.setHideTickAtMin(true);
      leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
      leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
      axisPanel.add(leftAxis, BorderLayout.WEST);

      final AxisComponent rightAxis = new AxisComponent(myStage.getThreadCountAxis(), AxisComponent.AxisOrientation.LEFT);
      rightAxis.setShowAxisLine(false);
      rightAxis.setShowMax(true);
      rightAxis.setShowUnitAtMax(true);
      rightAxis.setHideTickAtMin(true);
      rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
      rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
      axisPanel.add(rightAxis, BorderLayout.EAST);
    }

    private void configureOverlayPanel(@NotNull JPanel overlayPanel, @NotNull OverlayComponent overlay) {
      MouseListener usageListener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuUsageTooltip(myStage));
      overlay.addMouseListener(usageListener);
      overlayPanel.addMouseListener(usageListener);
      overlayPanel.setOpaque(false);
      overlayPanel.add(overlay, BorderLayout.CENTER);

      // Double-clicking the chart should remove a capture selection if one exists.
      MouseAdapter doubleClick = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && !e.isConsumed()) {
            myStage.getStudioProfilers().getTimeline().getSelectionRange().clear();
          }
        }
      };
      overlay.addMouseListener(doubleClick);
      overlayPanel.addMouseListener(doubleClick);
    }

    private void configureLineChart(@NotNull JPanel lineChartPanel, @NotNull OverlayComponent overlay) {
      lineChartPanel.setOpaque(false);

      DetailedCpuUsage cpuUsage = myStage.getCpuUsage();
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

      @SuppressWarnings("UseJBColor")
      DurationDataRenderer<CpuTraceInfo> traceRenderer =
        new DurationDataRenderer.Builder<>(myStage.getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
          .setDurationBg(CPU_CAPTURE_BACKGROUND)
          .setLabelProvider(this::formatCaptureLabel)
          .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
          .setClickHander(traceInfo -> myStage.setAndSelectCapture(traceInfo.getTraceId()))
          .build();

      traceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
        .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
      traceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
        .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
      traceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
        .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

      overlay.addDurationDataRenderer(traceRenderer);
      lineChart.addCustomRenderer(traceRenderer);

      @SuppressWarnings("UseJBColor")
      DurationDataRenderer<DefaultDurationData> inProgressTraceRenderer =
        new DurationDataRenderer.Builder<>(myStage.getInProgressTraceDuration(), ProfilerColors.CPU_CAPTURE_EVENT)
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
    }

    private void configureLegendPanel(@NotNull JPanel legendPanel) {
      CpuProfilerStage.CpuStageLegends legends = myStage.getLegends();
      LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
      legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
      legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE_CAPTURED));
      legend.configure(legends.getOthersLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_OTHER_USAGE_CAPTURED));
      legend.configure(legends.getThreadsLegend(),
                       new LegendConfig(LegendConfig.IconType.DASHED_LINE, ProfilerColors.THREADS_COUNT_CAPTURED));

      final JLabel label = new JLabel(myStage.getName());
      label.setBorder(MONITOR_LABEL_PADDING);
      label.setVerticalAlignment(SwingConstants.TOP);
      label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

      legendPanel.setOpaque(false);
      legendPanel.add(label, BorderLayout.WEST);
      legendPanel.add(legend, BorderLayout.EAST);
    }
  }

  static final class ImportModeView extends CpuUsageView {

    ImportModeView(@NotNull CpuProfilerStage stage) {
      super(stage);

      final JPanel tipPanel = new JBPanel(new BorderLayout());
      configureTipPanel(tipPanel);

      final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
      configureAxisPanel(timeAxisGuide, this);

      final JPanel overlayPanel = new JBPanel(new TabularLayout("*", "*"));
      configureOverlayPanel(overlayPanel, myOverlayComponent);

      // Order is important
      add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
      add(myOverlayComponent, new TabularLayout.Constraint(0, 0));
      add(mySelectionComponent, new TabularLayout.Constraint(0, 0));
      add(tipPanel, new TabularLayout.Constraint(0, 0));
      add(overlayPanel, new TabularLayout.Constraint(0, 0));
    }

    @SuppressWarnings("UseJBColor")
    private static void configureTipPanel(@NotNull JPanel panel) {
      panel.setOpaque(false);
      panel.setBackground(new Color(0, 0, 0, 0));
      InstructionsPanel infoMessage = new InstructionsPanel.Builder(
        new TextInstruction(SwingUtilities2.getFontMetrics(panel, ProfilerFonts.H3_FONT), "Cpu usage details unavailable"))
        .setColors(JBColor.foreground(), null)
        .build();
      panel.add(infoMessage);
    }

    private static void configureAxisPanel(@NotNull AxisComponent timeAxisGuide, @NotNull JPanel monitorPanel) {
      timeAxisGuide.setShowAxisLine(false);
      timeAxisGuide.setShowLabels(false);
      timeAxisGuide.setHideTickAtMin(true);
      timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
      monitorPanel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          timeAxisGuide.setMarkerLengths(monitorPanel.getHeight(), 0);
        }
      });
    }

    @SuppressWarnings("UseJBColor")
    private void configureOverlayPanel(@NotNull JPanel overlay, @NotNull OverlayComponent overlayComponent) {
      overlay.setOpaque(false);
      LineChart lineChart = new LineChart(new ArrayList<>());
      DurationDataRenderer<CpuTraceInfo> traceRenderer =
        new DurationDataRenderer.Builder<>(myStage.getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
          .setDurationBg(CPU_CAPTURE_BACKGROUND)
          .setLabelProvider(this::formatCaptureLabel)
          .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
          .setClickHander(traceInfo -> myStage.setAndSelectCapture(traceInfo.getTraceId()))
          .build();
      overlayComponent.addDurationDataRenderer(traceRenderer);
      lineChart.addCustomRenderer(traceRenderer);
      overlay.add(lineChart, new TabularLayout.Constraint(0, 0));
    }
  }
}
