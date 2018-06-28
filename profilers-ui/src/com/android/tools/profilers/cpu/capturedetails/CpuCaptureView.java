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
import com.android.tools.adtui.FilterComponent;
import com.android.tools.adtui.RangeTimeScrollBar;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.hchart.HTreeChartVerticalScrollBar;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.*;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuCaptureView {
  // Note the order of the values in the map defines the order of the tabs in UI.
  private static final Map<CaptureModel.Details.Type, String> DEFAULT_TAB_NAMES = ImmutableMap.of(
    CaptureModel.Details.Type.CALL_CHART, "Call Chart",
    CaptureModel.Details.Type.FLAME_CHART, "Flame Chart",
    CaptureModel.Details.Type.TOP_DOWN, "Top Down",
    CaptureModel.Details.Type.BOTTOM_UP, "Bottom Up");

  // For Atrace captures names from this map will be used in place of default tab names.
  private static final Map<CaptureModel.Details.Type, String> ATRACE_TAB_NAMES = ImmutableMap.of(
    CaptureModel.Details.Type.CALL_CHART, "Trace Events");

  // Some of the tab names may be replaced. This list defines the currently active tab names as
  private final Map<CaptureModel.Details.Type, String> myTabs = new LinkedHashMap<>(DEFAULT_TAB_NAMES);

  private static final Map<CaptureModel.Details.Type, Consumer<FeatureTracker>> CAPTURE_TRACKERS = ImmutableMap.of(
    CaptureModel.Details.Type.TOP_DOWN, FeatureTracker::trackSelectCaptureTopDown,
    CaptureModel.Details.Type.BOTTOM_UP, FeatureTracker::trackSelectCaptureBottomUp,
    CaptureModel.Details.Type.CALL_CHART, FeatureTracker::trackSelectCaptureCallChart,
    CaptureModel.Details.Type.FLAME_CHART, FeatureTracker::trackSelectCaptureFlameChart
  );

  @NotNull
  private final CpuProfilerStageView myView;

  private final JPanel myPanel;

  private final CommonTabbedPane myTabsPanel;

  @NotNull
  private FilterComponent myFilterComponent;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners.
  // Previously, we were creating a CaptureDetailsView temporarily and grabbing its UI
  // component only. However, in the case of subclass TreeChartView that contains an
  // AspectObserver, which fires events. If that gets cleaned up early, our UI loses some
  // useful events.
  @SuppressWarnings("FieldCanBeLocal")
  @Nullable
  private CaptureDetailsView myDetailsView;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CaptureModel.Details, CaptureDetailsView> myBinder;

  public CpuCaptureView(@NotNull CpuProfilerStageView view) {
    myView = view;
    myTabsPanel = new CommonTabbedPane();
    JComboBox<ClockType> clockTypeCombo = new ComboBox<>();
    JComboBoxView clockTypes =
      new JComboBoxView<>(clockTypeCombo, view.getStage().getAspect(), CpuProfilerAspect.CLOCK_TYPE,
                          view.getStage()::getClockTypes, view.getStage()::getClockType, view.getStage()::setClockType);
    clockTypes.bind();
    clockTypeCombo.setRenderer(new ClockTypeCellRenderer());
    CpuCapture capture = myView.getStage().getCapture();
    clockTypeCombo.setEnabled(capture != null && capture.isDualClock());

    if (capture != null && capture.getType() == CpuProfiler.CpuProfilerType.ATRACE) {
      myTabs.putAll(ATRACE_TAB_NAMES);
    }
    for (String label : myTabs.values()) {
      myTabsPanel.addTab(label, new JPanel(new BorderLayout()));
    }
    myTabsPanel.addChangeListener(this::setCaptureDetailToTab);
    myTabsPanel.setOpaque(false);
    // TOOLBAR_HEIGHT - 1, so the bottom border of the parent is visible.
    myPanel = new JPanel(new TabularLayout("*,Fit-", (TOOLBAR_HEIGHT - 1) + "px,*"));
    JPanel toolbar = new JPanel(createToolbarLayout());
    toolbar.add(clockTypeCombo);
    toolbar.add(myView.getSelectionTimeLabel());
    myFilterComponent = new FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS);
    if (view.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureFilterEnabled()) {
      CommonToggleButton filterButton = FilterComponent.createFilterToggleButton();
      toolbar.add(new FlatSeparator());
      toolbar.add(filterButton);

      myFilterComponent.addOnFilterChange((pattern, model) -> myView.getStage().setCaptureFilter(pattern, model));
      myFilterComponent.setVisible(false);
      myFilterComponent.setBorder(DEFAULT_BOTTOM_BORDER);
      FilterComponent.configureKeyBindingAndFocusBehaviors(myPanel, myFilterComponent, filterButton);
    }

    myPanel.add(toolbar, new TabularLayout.Constraint(0, 1));
    myPanel.add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));

    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureModel.TopDown.class, TreeDetailsView.TopDownDetailsView::new);
    myBinder.bind(CaptureModel.BottomUp.class, TreeDetailsView.BottomUpDetailsView::new);
    myBinder.bind(CaptureModel.CallChart.class, CallChartView::new);
    myBinder.bind(CaptureModel.FlameChart.class, FlameChartView::new);
    updateView();
  }

  public void updateView() {
    // Clear the content of all the tabs
    for (Component tab : myTabsPanel.getComponents()) {
      // In the constructor, we make sure to use JPanel as root components of the tabs.
      assert tab instanceof JPanel;
      ((JPanel)tab).removeAll();
    }
    JComponent filterComponent = myFilterComponent;
    boolean searchHasFocus = filterComponent.isAncestorOf(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    if (filterComponent.getParent() != null) {
      filterComponent.getParent().remove(filterComponent);
    }

    CaptureModel.Details details = myView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    // Update the current selected tab
    String detailsTypeString = myTabs.get(details.getType());
    int currentTabIndex = myTabsPanel.getSelectedIndex();
    if (currentTabIndex < 0 || !myTabsPanel.getTitleAt(currentTabIndex).equals(detailsTypeString)) {
      for (int i = 0; i < myTabsPanel.getTabCount(); ++i) {
        if (myTabsPanel.getTitleAt(i).equals(detailsTypeString)) {
          myTabsPanel.setSelectedIndex(i);
          break;
        }
      }
    }

    // Update selected tab content. As we need to update the content of the tabs dynamically,
    // we use a JPanel (set on the constructor) to wrap the content of each tab's content.
    // This is required because JBTabsImpl doesn't behave consistently when setting tab's component dynamically.
    JPanel selectedTab = (JPanel)myTabsPanel.getSelectedComponent();
    myDetailsView = myBinder.build(myView, details);
    selectedTab.add(filterComponent, BorderLayout.NORTH);
    selectedTab.add(myDetailsView.getComponent(), BorderLayout.CENTER);
    // We're replacing the content by removing and adding a new component.
    // JComponent#removeAll doc says that we should revalidate if it is already visible.
    selectedTab.revalidate();

    // the searchComponent gets re-added to the selected tab component after filtering changes, so reset the focus here.
    if (searchHasFocus) {
      myFilterComponent.requestFocusInWindow();
    }
  }

  private void setCaptureDetailToTab(ChangeEvent event) {
    CaptureModel.Details.Type type = null;
    if (myTabsPanel.getSelectedIndex() >= 0) {
      String tabTitle = myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex());
      for (Map.Entry<CaptureModel.Details.Type, String> entry : myTabs.entrySet()) {
        if (tabTitle.equals(entry.getValue())) {
          type = entry.getKey();
        }
      }
    }
    myView.getStage().setCaptureDetails(type);

    // TODO: Move this logic into setCaptureDetails later. Right now, if we do it, we track the
    // event several times instead of just once after taking a capture. setCaptureDetails should
    // probably have a guard condition.
    FeatureTracker tracker = myView.getStage().getStudioProfilers().getIdeServices().getFeatureTracker();
    CAPTURE_TRACKERS.getOrDefault(type, featureTracker -> {
    }).accept(tracker);
  }

  private static Logger getLog() {
    return Logger.getInstance(CpuCaptureView.class);
  }

  public JComponent getComponent() {
    return myPanel;
  }


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
      TreeChartNavigationHandler handler = new TreeChartNavigationHandler(chart, navigator);
      chart.addMouseListener(handler);
      stageView.getIdeComponents().createContextMenuInstaller().installNavigationContextMenu(chart, navigator, handler::getCodeLocation);
    }
    return chart;
  }

  private static class ClockTypeCellRenderer extends ListCellRendererWrapper<ClockType> {
    @Override
    public void customize(JList list,
                          ClockType value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
      switch (value) {
        case GLOBAL:
          setText("Wall Clock Time");
          break;
        case THREAD:
          setText("Thread Time");
          break;
        default:
          getLog().warn("Unexpected clock type received.");
      }
    }
  }

  static class CallChartView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;
    @NotNull private final CaptureModel.CallChart myCallChart;
    @NotNull private final HTreeChart<CaptureNode> myChart;

    private AspectObserver myObserver;

    private CallChartView(@NotNull CpuProfilerStageView stageView,
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

  static class FlameChartView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;
    @NotNull private final HTreeChart<CaptureNode> myChart;
    @NotNull private final AspectObserver myObserver;
    @NotNull private final CaptureModel.FlameChart myFlameChart;

    /**
     * The range that is visible to the user. When the user zooms in/out or pans this range will be changed.
     */
    @NotNull private final Range myMasterRange;

    public FlameChartView(CpuProfilerStageView stageView, @NotNull CaptureModel.FlameChart flameChart) {
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

  private static class TreeChartNavigationHandler extends MouseAdapter {
    @NotNull private final HTreeChart<CaptureNode> myChart;
    private Point myLastPopupPoint;

    TreeChartNavigationHandler(@NotNull HTreeChart<CaptureNode> chart, @NotNull CodeNavigator navigator) {
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

  /**
   * Produces a {@link CodeLocation} corresponding to a {@link CaptureNodeModel}. Returns null if the model is not navigatable.
   */
  @Nullable
  private static CodeLocation modelToCodeLocation(CaptureNodeModel model) {
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

}
