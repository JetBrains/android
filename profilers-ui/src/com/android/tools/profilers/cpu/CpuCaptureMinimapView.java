/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.profilers.ProfilerFonts.H3_FONT;
import static com.android.tools.profilers.ProfilerFonts.SMALL_FONT;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * View for navigating track groups in the {@link CpuCaptureStageView}. Contains CPU Usage chart and enables range-selection.
 */
public class CpuCaptureMinimapView {
  private static final int RANGE_SELECTION_DRAG_BAR_HEIGHT = 16;

  @NotNull private final JPanel myPanel;
  @NotNull private final JPanel myInnerPanel;

  public CpuCaptureMinimapView(@NotNull CpuCaptureMinimapModel model) {
    myInnerPanel = new JPanel(new TabularLayout("*", "*"));
    myInnerPanel.setBackground(StudioColorsKt.getPrimaryContentBackground());
    myInnerPanel.setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 1, 0, 1));

    JLabel chartLabel = new JLabel();
    chartLabel.setText("CPU Usage");
    chartLabel.setFont(SMALL_FONT);
    chartLabel.setVerticalAlignment(SwingConstants.TOP);
    chartLabel.setBorder(new JBEmptyBorder(4, 4, 4, 4));

    // Order is important
    RangeSelectionComponent rangeSelectionComponent = createRangeSelectionComponent(model.getRangeSelectionModel());
    myInnerPanel.add(createOverlayComponent(rangeSelectionComponent), new TabularLayout.Constraint(0, 0));
    myInnerPanel.add(rangeSelectionComponent, new TabularLayout.Constraint(0, 0));
    myInnerPanel.add(createAxis(model.getCaptureRange()), new TabularLayout.Constraint(0, 0));
    myInnerPanel.add(createLineChart(model.getCpuUsage()), new TabularLayout.Constraint(0, 0));
    myInnerPanel.add(chartLabel, new TabularLayout.Constraint(0, 0));
    if (model.getCpuUsage().getCpuSeries().getSeries().isEmpty()) {
      JLabel cpuDataNotAvailableLabel = new JLabel();
      cpuDataNotAvailableLabel.setText("No CPU usage data available for this imported trace");
      cpuDataNotAvailableLabel.setFont(H3_FONT);
      cpuDataNotAvailableLabel.setVerticalAlignment(SwingConstants.CENTER);
      cpuDataNotAvailableLabel.setHorizontalAlignment(SwingConstants.CENTER);
      myInnerPanel.add(cpuDataNotAvailableLabel, new TabularLayout.Constraint(0, 0));
    }

    myPanel = new JPanel(new TabularLayout("*", "60px"));
    myPanel.setBorder(JBUI.Borders.empty(0, 8));
    myPanel.setBackground(StudioColorsKt.getPrimaryPanelBackground());
    myPanel.add(myInnerPanel, new TabularLayout.Constraint(0, 0));
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void registerRangeTooltipComponent(@NotNull RangeTooltipComponent tooltipComponent) {
    tooltipComponent.registerListenersOn(myInnerPanel);
  }

  public void addMouseListener(@NotNull MouseListener listener) {
    myInnerPanel.addMouseListener(listener);
  }

  private JComponent createOverlayComponent(@NotNull JComponent component) {
    OverlayComponent overlayComponent = new OverlayComponent(component);
    DelegateMouseEventHandler.delegateTo(myInnerPanel).installListenerOn(overlayComponent).installMotionListenerOn(overlayComponent);
    return overlayComponent;
  }

  private static RangeSelectionComponent createRangeSelectionComponent(@NotNull RangeSelectionModel model) {
    RangeSelectionComponent rangeSelectionComponent = new RangeSelectionComponent(model, true);
    rangeSelectionComponent.setCursorSetter(AdtUiUtils::setTooltipCursor);
    rangeSelectionComponent.setDragBarHeight(RANGE_SELECTION_DRAG_BAR_HEIGHT);
    return rangeSelectionComponent;
  }

  private static JComponent createLineChart(@NotNull CpuUsage cpuUsage) {
    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true));
    lineChart.setFillEndGap(true);
    return lineChart;
  }

  private static JComponent createAxis(@NotNull Range cpuUsageRange) {
    AxisComponent timeAxis = new AxisComponent(
      new ClampedAxisComponentModel.Builder(cpuUsageRange, TimeAxisFormatter.DEFAULT).build(), AxisComponent.AxisOrientation.TOP, true);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    // Hide the axis line so it doesn't stack with panel border.
    timeAxis.setShowAxisLine(false);
    return timeAxis;
  }
}
