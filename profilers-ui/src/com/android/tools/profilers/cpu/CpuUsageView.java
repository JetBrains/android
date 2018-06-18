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

      myOverlayComponent.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && !e.isConsumed()) {
            myStage.getStudioProfilers().getTimeline().getSelectionRange().clear();
          }
        }
      });

      // Order is important
      add(createAxisPanel(), new TabularLayout.Constraint(0, 0));
      add(createLegendPanel(), new TabularLayout.Constraint(0, 0));
      add(myOverlayComponent, new TabularLayout.Constraint(0, 0));
      add(mySelectionComponent, new TabularLayout.Constraint(0, 0));
      add(createLineChartPanel(), new TabularLayout.Constraint(0, 0));
    }

    @NotNull
    private JComponent createAxisPanel() {
      final JPanel axisPanel = new JBPanel(new BorderLayout());
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

      return axisPanel;
    }

    @NotNull
    private JComponent createLineChartPanel() {
      final JPanel lineChartPanel = new JBPanel(new BorderLayout());

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

      myOverlayComponent.addDurationDataRenderer(traceRenderer);
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

      myOverlayComponent.addDurationDataRenderer(inProgressTraceRenderer);
      lineChart.addCustomRenderer(inProgressTraceRenderer);

      return lineChartPanel;
    }

    @NotNull
    private JComponent createLegendPanel() {
      final JPanel legendPanel = new JBPanel(new BorderLayout());
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

      return legendPanel;
    }
  }

  static final class ImportModeView extends CpuUsageView {

    ImportModeView(@NotNull CpuProfilerStage stage) {
      super(stage);

      // Order is important
      add(createTimeAxisGuide(), new TabularLayout.Constraint(0, 0));
      add(myOverlayComponent, new TabularLayout.Constraint(0, 0));
      add(mySelectionComponent, new TabularLayout.Constraint(0, 0));
      add(createTipPanel(), new TabularLayout.Constraint(0, 0));
      add(createOverlayPanel(), new TabularLayout.Constraint(0, 0));
    }

    @NotNull
    private static JComponent createTipPanel() {
      final JPanel panel = new JBPanel(new BorderLayout());
      panel.setOpaque(false);
      // noinspection UseJBColor
      panel.setBackground(new Color(0, 0, 0, 0));
      InstructionsPanel infoMessage = new InstructionsPanel.Builder(
        new TextInstruction(SwingUtilities2.getFontMetrics(panel, ProfilerFonts.H3_FONT), "Cpu usage details unavailable"))
        .setColors(JBColor.foreground(), null)
        .build();
      panel.add(infoMessage);
      return panel;
    }

    @NotNull
    private JComponent createTimeAxisGuide() {
      final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
      timeAxisGuide.setShowAxisLine(false);
      timeAxisGuide.setShowLabels(false);
      timeAxisGuide.setHideTickAtMin(true);
      timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          timeAxisGuide.setMarkerLengths(getHeight(), 0);
        }
      });
      return timeAxisGuide;
    }

    @NotNull
    private JComponent createOverlayPanel() {
      final JPanel overlayPanel = new JBPanel(new TabularLayout("*", "*"));

      overlayPanel.setOpaque(false);
      LineChart lineChart = new LineChart(new ArrayList<>());

      @SuppressWarnings("UseJBColor")
      DurationDataRenderer<CpuTraceInfo> traceRenderer =
        new DurationDataRenderer.Builder<>(myStage.getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
          .setDurationBg(CPU_CAPTURE_BACKGROUND)
          .setLabelProvider(this::formatCaptureLabel)
          .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
          .setClickHander(traceInfo -> myStage.setAndSelectCapture(traceInfo.getTraceId()))
          .build();
      myOverlayComponent.addDurationDataRenderer(traceRenderer);
      lineChart.addCustomRenderer(traceRenderer);
      overlayPanel.add(lineChart, new TabularLayout.Constraint(0, 0));
      return overlayPanel;
    }
  }
}
