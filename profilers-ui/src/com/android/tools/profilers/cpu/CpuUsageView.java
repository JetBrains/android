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

import static com.android.tools.profilers.ProfilerColors.CPU_CAPTURE_BACKGROUND;
import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.ui.components.JBPanel;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public class CpuUsageView extends JBPanel<CpuUsageView> {
  @NotNull protected final CpuProfilerStage myStage;
  @NotNull protected final OverlayComponent myOverlayComponent;

  public CpuUsageView(@NotNull CpuProfilerStage stage) {
    super(new TabularLayout("*", "*"));
    myStage = stage;

    myOverlayComponent = new OverlayComponent(new JPanel());
    // |CpuUsageView| does not receive any mouse events, because all mouse events are consumed by |OverlayComponent|
    // We're dispatching them manually, so that |CpuProfilerStageView| could register CPU context menus or other mouse events
    // directly into |CpuUsageView| instead of |OverlayComponent|.
    DelegateMouseEventHandler.delegateTo(this)
                             .installListenerOn(myOverlayComponent)
                             .installMotionListenerOn(myOverlayComponent);

    setBorder(MONITOR_BORDER);
    setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    // Order is important
    add(createAxisPanel(), new TabularLayout.Constraint(0, 0));
    add(createLegendPanel(), new TabularLayout.Constraint(0, 0));
    add(myOverlayComponent, new TabularLayout.Constraint(0, 0));
    add(createLineChartPanel(), new TabularLayout.Constraint(0, 0));
  }

  @NotNull
  private JComponent createAxisPanel() {
    final JPanel axisPanel = new JBPanel<>(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(myStage.getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT, true);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setOnlyShowUnitAtMax(false);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(myStage.getThreadCountAxis(), AxisComponent.AxisOrientation.LEFT, true);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setOnlyShowUnitAtMax(true);
    rightAxis.setHideTickAtMin(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    return axisPanel;
  }

  @NotNull
  private JComponent createLineChartPanel() {
    final JPanel lineChartPanel = new JBPanel<>(new BorderLayout());

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
        .setIconMapper(info -> info.getDurationUs() != Long.MAX_VALUE ? StudioIcons.Profiler.Toolbar.CAPTURE_CLOCK : null)
        .setLabelProvider(info -> info.getDurationUs() == Long.MAX_VALUE ? "In progress" : "")
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .setBackgroundClickable(true)
        .setClickHandler(traceInfo -> {
          if (traceInfo.getDurationUs() != Long.MAX_VALUE) {
            myStage.setAndSelectCapture(traceInfo.getTraceId());
          }
        })
        .build();

    traceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    myOverlayComponent.addDurationDataRenderer(traceRenderer);
    lineChart.addCustomRenderer(traceRenderer);

    return lineChartPanel;
  }

  @NotNull
  private JComponent createLegendPanel() {
    final JPanel legendPanel = new JBPanel<>(new BorderLayout());
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
