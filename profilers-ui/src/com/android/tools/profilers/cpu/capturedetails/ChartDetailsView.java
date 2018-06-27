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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.RangeTimeScrollBar;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.hchart.HTreeChartVerticalScrollBar;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.intellij.ui.DoubleClickListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

/**
 * A base class for {@link CallChartDetailsView} and {@link FlameChartDetailsView} details views.
 */
abstract class ChartDetailsView extends CaptureDetailsView {

  private static HTreeChart<CaptureNode> setUpChart(@NotNull CaptureModel.Details.Type type,
                                                    @NotNull Range globalRange,
                                                    @NotNull Range range,
                                                    @Nullable CaptureNode node,
                                                    @NotNull CpuProfilerStageView stageView) {
    HTreeChart.Orientation orientation;
    if (type == CaptureModel.Details.Type.CALL_CHART) {
      orientation = HTreeChart.Orientation.TOP_DOWN;
    }
    else {
      orientation = HTreeChart.Orientation.BOTTOM_UP;
    }

    HTreeChart<CaptureNode> chart = new HTreeChart.Builder<>(node, range, new CaptureNodeHRenderer(type))
      .setGlobalXRange(globalRange)
      .setOrientation(orientation)
      .setRootVisible(false)
      .build();

    if (node != null) {
      if (node.getData() instanceof AtraceNodeModel && type == CaptureModel.Details.Type.CALL_CHART) {
        chart.addMouseMotionListener(new CpuTraceEventTooltipView(chart, stageView));
      }
      else {
        chart.addMouseMotionListener(new CpuChartTooltipView(chart, stageView));
      }
    }

    if (stageView.getStage().getCapture() != null && stageView.getStage().getCapture().getType() != CpuProfiler.CpuProfilerType.ATRACE) {
      CodeNavigator navigator = stageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator();
      CodeNavigationHandler handler = new CodeNavigationHandler(chart, navigator);
      chart.addMouseListener(handler);
      stageView.getIdeComponents().createContextMenuInstaller().installNavigationContextMenu(chart, navigator, handler::getCodeLocation);
    }
    return chart;
  }

  /**
   * Produces a {@link CodeLocation} corresponding to a {@link CaptureNodeModel}. Returns null if the model is not navigatable.
   */
  @Nullable
  protected static CodeLocation modelToCodeLocation(CaptureNodeModel model) {
    if (model instanceof CppFunctionModel) {
      CppFunctionModel nativeFunction = (CppFunctionModel)model;
      return new CodeLocation.Builder(nativeFunction.getClassOrNamespace())
        .setMethodName(nativeFunction.getName())
        .setMethodParameters(nativeFunction.getParameters())
        .setNativeCode(true)
        .build();
    }
    else if (model instanceof JavaMethodModel) {
      JavaMethodModel javaMethod = (JavaMethodModel)model;
      return new CodeLocation.Builder(javaMethod.getClassName())
        .setMethodName(javaMethod.getName())
        .setMethodSignature(javaMethod.getSignature())
        .setNativeCode(false)
        .build();
    }
    // Code is not navigatable.
    return null;
  }

  static final class CallChartDetailsView extends ChartDetailsView {
    /**
     * Component that contains everything, e.g chart, axis, scrollbar.
     */
    @NotNull private final JPanel myPanel;

    /**
     * The visual representation of the {@link #myCallChart}.
     */
    @NotNull private final HTreeChart<CaptureNode> myChart;

    /**
     * The call chart details that needs to be rendered.
     */
    @NotNull private final CaptureModel.CallChart myCallChart;

    private AspectObserver myObserver;

    CallChartDetailsView(@NotNull CpuProfilerStageView stageView,
                          @NotNull CaptureModel.CallChart callChart) {
      myCallChart = callChart;
      // Call Chart model always correlates to the entire capture. CallChartView shows the data corresponding to the selected range in
      // timeline. Users can navigate to other part within the capture by interacting with the call chart UI. When it happens, the timeline
      // selection should be automatically updated.
      Range selectionRange = stageView.getTimeline().getSelectionRange();
      assert stageView.getStage().getCapture() != null;
      Range captureRange = stageView.getStage().getCapture().getRange();
      myChart = setUpChart(CaptureModel.Details.Type.CALL_CHART, captureRange, selectionRange,
                           myCallChart.getNode(), stageView);

      if (myCallChart.getNode() == null) {
        myPanel = getNoDataForThread();
        return;
      }

      // We use selectionRange here instead of nodeRange, because nodeRange synchronises with selectionRange and vice versa.
      // In other words, there is a constant ratio between them. And the horizontal scrollbar represents selection range within
      // capture range.
      RangeTimeScrollBar horizontalScrollBar = new RangeTimeScrollBar(captureRange, selectionRange, TimeUnit.MICROSECONDS);
      horizontalScrollBar.setPreferredSize(new Dimension(horizontalScrollBar.getPreferredSize().width, 10));

      AxisComponent axis = createAxis(selectionRange, stageView.getTimeline().getDataRange());

      JPanel contentPanel = new JPanel(new TabularLayout("*,Fit", "*,Fit"));
      contentPanel.add(axis, new TabularLayout.Constraint(0, 0));
      contentPanel.add(myChart, new TabularLayout.Constraint(0, 0));
      contentPanel.add(new HTreeChartVerticalScrollBar<>(myChart), new TabularLayout.Constraint(0, 1));
      contentPanel.add(horizontalScrollBar, new TabularLayout.Constraint(1, 0, 1, 2));

      myPanel = new JPanel(new CardLayout());
      myPanel.add(contentPanel, CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      myObserver = new AspectObserver();

      myCallChart.getRange().addDependency(myObserver).onChange(Range.Aspect.RANGE, this::callChartRangeChanged);

      callChartRangeChanged();
    }

    private void callChartRangeChanged() {
      CaptureNode node = myCallChart.getNode();
      assert node != null;
      Range intersection = myCallChart.getRange().getIntersection(new Range(node.getStart(), node.getEnd()));
      switchCardLayout(myPanel, intersection.isEmpty() || intersection.getLength() == 0);
    }

    private static AxisComponent createAxis(@NotNull Range range, @NotNull Range globalRange) {
      AxisComponentModel axisModel =
        new ResizingAxisComponentModel.Builder(range, new TimeAxisFormatter(1, 10, 1)).setGlobalRange(globalRange).build();
      AxisComponent axis = new AxisComponent(axisModel, AxisComponent.AxisOrientation.BOTTOM);
      axis.setShowAxisLine(false);
      axis.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
      axis.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          axis.setMarkerLengths(axis.getHeight(), 0);
          axis.repaint();
        }
      });
      return axis;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }

  static final class FlameChartDetailsView extends ChartDetailsView {
    /**
     * Component that contains everything, e.g chart, axis, scrollbar.
     */
    @NotNull private final JPanel myPanel;

    /**
     * The visual representation of the {@link #myFlameChart}.
     */
    @NotNull private final HTreeChart<CaptureNode> myChart;

    /**
     * The flame chart details that needs to be rendered.
     */
    @NotNull private final CaptureModel.FlameChart myFlameChart;

    /**
     * The range that is visible to the user. When the user zooms in/out or pans this range will be changed.
     */
    @NotNull private final Range myMasterRange;

    @NotNull private final AspectObserver myObserver;

    FlameChartDetailsView(CpuProfilerStageView stageView, @NotNull CaptureModel.FlameChart flameChart) {
      // Flame Chart model always correlates to the selected range on the timeline, not necessarily the entire capture. Users cannot
      // navigate to other part within the capture by interacting with the flame chart UI (they can do so only from timeline UI).
      // Users can zoom-in and then view only part of the flame chart. Since a part of flame chart may not correspond to a continuous
      // sub-range on timeline, the timeline selection should not be updated while users are interacting with flame chart UI. Therefore,
      // we create new Range object (myMasterRange) to represent the range visible to the user. We cannot just pass flameChart.getRange().
      myFlameChart = flameChart;
      myMasterRange = new Range(flameChart.getRange());
      myChart = setUpChart(CaptureModel.Details.Type.FLAME_CHART, flameChart.getRange(), myMasterRange, myFlameChart.getNode(), stageView);
      myObserver = new AspectObserver();

      if (myFlameChart.getNode() == null) {
        myPanel = getNoDataForThread();
        return;
      }

      RangeTimeScrollBar horizontalScrollBar = new RangeTimeScrollBar(flameChart.getRange(), myMasterRange, TimeUnit.MICROSECONDS);
      horizontalScrollBar.setPreferredSize(new Dimension(horizontalScrollBar.getPreferredSize().width, 10));

      JPanel contentPanel = new JPanel(new TabularLayout("*,Fit", "*,Fit"));
      contentPanel.add(myChart, new TabularLayout.Constraint(0, 0));
      contentPanel.add(new HTreeChartVerticalScrollBar<>(myChart), new TabularLayout.Constraint(0, 1));
      contentPanel.add(horizontalScrollBar, new TabularLayout.Constraint(1, 0, 1, 2));

      myPanel = new JPanel(new CardLayout());
      myPanel.add(contentPanel, CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      myFlameChart.getAspect().addDependency(myObserver).onChange(CaptureModel.FlameChart.Aspect.NODE, this::nodeChanged);
      nodeChanged();
    }

    private void nodeChanged() {
      switchCardLayout(myPanel, myFlameChart.getNode() == null);
      myChart.setHTree(myFlameChart.getNode());
      myMasterRange.set(myFlameChart.getRange());
    }

    @NotNull
    @Override
    JComponent getComponent() {
      return myPanel;
    }
  }

  private static class CodeNavigationHandler extends MouseAdapter {
    @NotNull private final HTreeChart<CaptureNode> myChart;
    private Point myLastPopupPoint;

    CodeNavigationHandler(@NotNull HTreeChart<CaptureNode> chart, @NotNull CodeNavigator navigator) {
      myChart = chart;
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent event) {
          setLastPopupPoint(event);
          CodeLocation codeLocation = getCodeLocation();
          if (codeLocation != null && navigator.isNavigatable(codeLocation)) {
            navigator.navigate(codeLocation);
          }
          return false;
        }
      }.installOn(chart);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        setLastPopupPoint(e);
      }
    }

    private void setLastPopupPoint(MouseEvent e) {
      myLastPopupPoint = e.getPoint();
    }

    @Nullable
    private CodeLocation getCodeLocation() {
      CaptureNode n = myChart.getNodeAt(myLastPopupPoint);
      if (n == null) {
        return null;
      }
      return modelToCodeLocation(n.getData());
    }
  }
}
