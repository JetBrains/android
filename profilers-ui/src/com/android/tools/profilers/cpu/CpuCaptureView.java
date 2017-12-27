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

import com.android.tools.adtui.FlatTabbedPane;
import com.android.tools.adtui.RangeScrollBarUI;
import com.android.tools.adtui.RangeTimeScrollBar;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.hchart.HTreeChartVerticalScrollBar;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TABLE_COLUMN_HEADER_BORDER;
import static com.android.tools.profilers.ProfilerLayout.TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER;
import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

class CpuCaptureView {
  // Note the order of the values in the map defines the order of the tabs in UI.
  private static final Map<CaptureModel.Details.Type, String> TABS = ImmutableMap.of(
    CaptureModel.Details.Type.CALL_CHART, "Call Chart",
    CaptureModel.Details.Type.FLAME_CHART, "Flame Chart",
    CaptureModel.Details.Type.TOP_DOWN, "Top Down",
    CaptureModel.Details.Type.BOTTOM_UP, "Bottom Up");

  private static final Map<CaptureModel.Details.Type, Consumer<FeatureTracker>> CAPTURE_TRACKERS = ImmutableMap.of(
    CaptureModel.Details.Type.TOP_DOWN, FeatureTracker::trackSelectCaptureTopDown,
    CaptureModel.Details.Type.BOTTOM_UP, FeatureTracker::trackSelectCaptureBottomUp,
    CaptureModel.Details.Type.CALL_CHART, FeatureTracker::trackSelectCaptureCallChart,
    CaptureModel.Details.Type.FLAME_CHART, FeatureTracker::trackSelectCaptureFlameChart
  );

  private static final Comparator<DefaultMutableTreeNode> DEFAULT_SORT_ORDER =
    Collections.reverseOrder(new DoubleValueNodeComparator(CpuTreeNode::getTotal));

  @NotNull
  private final CpuProfilerStageView myView;

  private final JPanel myPanel;

  private final JTabbedPane myTabsPanel;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners.
  // Previously, we were creating a CaptureDetailsView temporarily and grabbing its UI
  // component only. However, in the case of subclass TreeChartView that contains an
  // AspectObserver, which fires events. If that gets cleaned up early, our UI loses some
  // useful events.
  @SuppressWarnings("FieldCanBeLocal")
  @Nullable
  private CpuCaptureView.CaptureDetailsView myDetailsView;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CaptureModel.Details, CaptureDetailsView> myBinder;

  CpuCaptureView(@NotNull CpuProfilerStageView view) {
    myView = view;
    myPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    myTabsPanel = new FlatTabbedPane();

    for (String label : TABS.values()) {
      myTabsPanel.addTab(label, new JPanel(new BorderLayout()));
    }
    myTabsPanel.addChangeListener(this::setCaptureDetailToTab);

    JComboBox<ClockType> clockTypeCombo = new ComboBox<>();
    JComboBoxView clockTypes =
      new JComboBoxView<>(clockTypeCombo, view.getStage().getAspect(), CpuProfilerAspect.CLOCK_TYPE,
                          view.getStage()::getClockTypes, view.getStage()::getClockType, view.getStage()::setClockType);
    clockTypes.bind();
    clockTypeCombo.setRenderer(new ClockTypeCellRenderer());
    myTabsPanel.setOpaque(false);

    myPanel.add(clockTypeCombo, new TabularLayout.Constraint(0, 1));
    myPanel.add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));

    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureModel.TopDown.class, TopDownView::new);
    myBinder.bind(CaptureModel.BottomUp.class, BottomUpView::new);
    myBinder.bind(CaptureModel.CallChart.class, CallChartView::new);
    myBinder.bind(CaptureModel.FlameChart.class, FlameChartView::new);
    updateView();
  }

  void updateView() {
    // Clear the content of all the tabs
    for (Component tab : myTabsPanel.getComponents()) {
      // In the constructor, we make sure to use JPanel as root components of the tabs.
      assert tab instanceof JPanel;
      ((JPanel)tab).removeAll();
    }

    CaptureModel.Details details = myView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    // Update the current selected tab
    String detailsTypeString = TABS.get(details.getType());
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
    Component selectedTab = myTabsPanel.getSelectedComponent();
    assert selectedTab instanceof JPanel;
    myDetailsView = myBinder.build(myView, details);
    ((JPanel)selectedTab).add(myDetailsView.getComponent(), BorderLayout.CENTER);
    // We're replacing the content by removing and adding a new component.
    // JComponent#removeAll doc says that we should revalidate if it is already visible.
    selectedTab.revalidate();
  }

  private void setCaptureDetailToTab(ChangeEvent event) {
    CaptureModel.Details.Type type = null;
    if (myTabsPanel.getSelectedIndex() >= 0) {
      String tabTitle = myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex());
      for (Map.Entry<CaptureModel.Details.Type, String> entry : TABS.entrySet()) {
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

  @NotNull
  private static JComponent setUpCpuTree(@NotNull JTree tree, @NotNull CpuTreeModel model, @NotNull CpuProfilerStageView stageView) {
    tree.setModel(model);
    CpuTraceTreeSorter sorter = new CpuTraceTreeSorter(tree);
    sorter.setModel(model, DEFAULT_SORT_ORDER);

    stageView.getIdeComponents()
      .installNavigationContextMenu(tree, stageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator(),
                                    () -> getCodeLocation(tree));

    return new ColumnTreeBuilder(tree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Name")
                   .setPreferredWidth(900)
                   .setHeaderBorder(TABLE_COLUMN_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MethodNameRenderer())
                   .setComparator(new NameValueNodeComparator()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Self (μs)")
                   .setPreferredWidth(100)
                   .setHeaderBorder(TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getSelf, false, SwingConstants.RIGHT))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getSelf)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setHeaderBorder(TABLE_COLUMN_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getSelf, true, SwingConstants.LEFT))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getSelf)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Children (μs)")
                   .setPreferredWidth(100)
                   .setHeaderBorder(TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getChildrenTotal, false, SwingConstants.RIGHT))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getChildrenTotal)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setHeaderBorder(TABLE_COLUMN_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getChildrenTotal, true, SwingConstants.LEFT))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getChildrenTotal)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Total (μs)")
                   .setPreferredWidth(100)
                   .setHeaderBorder(TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getTotal, false, SwingConstants.RIGHT))
                   .setComparator(DEFAULT_SORT_ORDER))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setHeaderBorder(TABLE_COLUMN_HEADER_BORDER)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getTotal, true, SwingConstants.LEFT))
                   .setComparator(DEFAULT_SORT_ORDER))
      .setTreeSorter(sorter)
      .setBorder(DEFAULT_TOP_BORDER)
      .setBackground(ProfilerColors.DEFAULT_BACKGROUND)
      .setShowVerticalLines(true)
      .build();
  }

  @Nullable
  private static CodeLocation getCodeLocation(@NotNull JTree tree) {
    if (tree.getSelectionPath() == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getSelectionPath().getLastPathComponent();
    CpuTreeNode cpuNode = (CpuTreeNode)node.getUserObject();
    return new CodeLocation.Builder(cpuNode.getClassName()).setMethodSignature(cpuNode.getMethodName(), cpuNode.getSignature()).build();
  }

  /**
   * Expands a few nodes in order to improve the visual feedback of the list.
   */
  private static void expandTreeNodes(JTree tree) {
    int maxRowsToExpand = 8; // TODO: adjust this value if necessary.
    int i = 0;
    while (i < tree.getRowCount() && i < maxRowsToExpand) {
      tree.expandRow(i++);
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private static CpuTreeNode getNode(Object value) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    return (CpuTreeNode)node.getUserObject();
  }

  private static HTreeChart<MethodModel> setUpChart(@NotNull Range range,
                                                    @Nullable HNode<MethodModel> node,
                                                    @NotNull HTreeChart.Orientation orientation,
                                                    @NotNull CpuProfilerStageView stageView) {
    HTreeChart<MethodModel> chart = new HTreeChart<>(range, orientation);
    chart.setHRenderer(new SampledMethodUsageHRenderer());
    chart.setHTree(node);
    TreeChartNavigationHandler handler = new TreeChartNavigationHandler(chart);
    chart.addMouseListener(handler);
    stageView.getIdeComponents()
      .installNavigationContextMenu(chart, stageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator(),
                                    handler::getCodeLocation);
    return chart;
  }

  private static abstract class CaptureDetailsView {
    protected static final String CARD_EMPTY_INFO = "Empty content";
    protected static final String CARD_CONTENT = "Content";

    @NotNull
    abstract JComponent getComponent();

    protected static void switchCardLayout(@NotNull JPanel panel, boolean isEmpty) {
      CardLayout cardLayout = (CardLayout)panel.getLayout();
      cardLayout.show(panel, isEmpty ? CARD_EMPTY_INFO : CARD_CONTENT);
    }

    protected static JPanel getNoDataForThread() {
      String message = "No data available for the selected thread.";
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new InfoMessagePanel(message, "", null), BorderLayout.CENTER);
      return panel;
    }

    protected static JComponent getNoDataForRange() {
      String message = "No data available for the selected time frame.";
      return new InfoMessagePanel(message, "", null);
    }
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

  static class TopDownView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;

    private TopDownView(@NotNull CpuProfilerStageView view, @NotNull CaptureModel.TopDown topDown) {
      TopDownTreeModel model = topDown.getModel();
      if (model == null) {
        myPanel = getNoDataForThread();
        return;
      }

      myPanel = new JPanel(new CardLayout());
      // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
      final JTree tree = new JTree();
      int defaultFontHeight = tree.getFontMetrics(tree.getFont()).getHeight();
      tree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
      tree.setBorder(ProfilerLayout.TABLE_ROW_BORDER);
      myPanel.add(setUpCpuTree(tree, model, view), CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      expandTreeNodes(tree);

      model.addTreeModelListener(new TreeModelAdapter() {
        @Override
        protected void process(TreeModelEvent event, EventType type) {
          switchCardLayout(myPanel, model.isEmpty());
        }
      });
      switchCardLayout(myPanel, model.isEmpty());
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }

  static class BottomUpView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;

    private BottomUpView(@NotNull CpuProfilerStageView view, @NotNull CaptureModel.BottomUp bottomUp) {
      BottomUpTreeModel model = bottomUp.getModel();

      if (model == null) {
        myPanel = getNoDataForThread();
        return;
      }

      myPanel = new JPanel(new CardLayout());
      // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
      final JTree tree = new JTree();
      int defaultFontHeight = tree.getFontMetrics(tree.getFont()).getHeight();
      tree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
      tree.setBorder(ProfilerLayout.TABLE_ROW_BORDER);
      myPanel.add(setUpCpuTree(tree, model, view), CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      tree.setRootVisible(false);
      tree.addTreeWillExpandListener(new TreeWillExpandListener() {
        @Override
        public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
          ((BottomUpTreeModel)tree.getModel()).expand(node);
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        }
      });

      model.addTreeModelListener(new TreeModelAdapter() {
        @Override
        protected void process(TreeModelEvent event, EventType type) {
          // When the root loses all of its children it can't be expanded and when they're added it is still collapsed.
          // As a result, nothing will be visible as the root itself isn't visible. So, expand it if it's the case.
          if (type == EventType.NodesInserted && event.getTreePath().getPathCount() == 1) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
            Object[] inserted = event.getChildren();
            if (inserted != null && inserted.length == root.getChildCount()) {
              tree.expandPath(new TreePath(root));
            }
          }
          switchCardLayout(myPanel, model.isEmpty());
        }
      });
      switchCardLayout(myPanel, model.isEmpty());
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }

  static class CallChartView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;
    @NotNull private final CaptureModel.CallChart myCallChart;
    @NotNull private final HTreeChart<MethodModel> myChart;

    private AspectObserver myObserver;

    private CallChartView(@NotNull CpuProfilerStageView stageView,
                          @NotNull CaptureModel.CallChart callChart) {
      myCallChart = callChart;
      myChart = setUpChart(myCallChart.getRange(), myCallChart.getNode(), HTreeChart.Orientation.TOP_DOWN, stageView);
      if (myCallChart.getNode() == null) {
        myPanel = getNoDataForThread();
        return;
      }

      Range selectionRange = stageView.getTimeline().getSelectionRange();
      // We use selectionRange here instead of nodeRange, because nodeRange synchronises with selectionRange and vice versa.
      // In other words, there is a constant ratio between them. And the horizontal scrollbar represents selection range within
      // capture range.
      Range captureRange = stageView.getStage().getCapture().getRange();
      RangeTimeScrollBar horizontalScrollBar = new RangeTimeScrollBar(captureRange, selectionRange, TimeUnit.MICROSECONDS);
      horizontalScrollBar.setPreferredSize(new Dimension(horizontalScrollBar.getPreferredSize().width, 10));

      JPanel contentPanel = new JPanel(new BorderLayout());

      contentPanel.add(myChart, BorderLayout.CENTER);
      contentPanel.add(new HTreeChartVerticalScrollBar<>(myChart), BorderLayout.EAST);
      contentPanel.add(horizontalScrollBar, BorderLayout.SOUTH);

      myPanel = new JPanel(new CardLayout());
      myPanel.add(contentPanel, CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      myObserver = new AspectObserver();
      myCallChart.getRange().addDependency(myObserver).onChange(Range.Aspect.RANGE, this::callChartRangeChanged);
      callChartRangeChanged();
    }

    private void callChartRangeChanged() {
      HNode<MethodModel> node = myCallChart.getNode();
      assert node != null;
      Range intersection = myCallChart.getRange().getIntersection(new Range(node.getStart(), node.getEnd()));
      switchCardLayout(myPanel, intersection.isEmpty() || intersection.getLength() == 0);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }

  static class FlameChartView extends CaptureDetailsView {
    @NotNull private final JPanel myPanel;
    @NotNull private final HTreeChart<MethodModel> myChart;
    @NotNull private final AspectObserver myObserver;
    @NotNull private final CaptureModel.FlameChart myFlameChart;

    /**
     * The range that is visible to the user. When the user zooms in/out or pans this range will be changed.
     */
    @NotNull private final Range myMasterRange;

    public FlameChartView(CpuProfilerStageView stageView, @NotNull CaptureModel.FlameChart flameChart) {
      myFlameChart = flameChart;
      myMasterRange = new Range(flameChart.getRange());

      myChart = setUpChart(myMasterRange, myFlameChart.getNode(), HTreeChart.Orientation.BOTTOM_UP, stageView);

      RangeTimeScrollBar horizontalScrollBar = new RangeTimeScrollBar(flameChart.getRange(), myMasterRange, TimeUnit.MICROSECONDS);
      horizontalScrollBar.setPreferredSize(new Dimension(horizontalScrollBar.getPreferredSize().width, 10));

      JPanel contentPanel = new JPanel(new BorderLayout());
      contentPanel.add(myChart, BorderLayout.CENTER);
      contentPanel.add(new HTreeChartVerticalScrollBar<>(myChart), BorderLayout.EAST);
      contentPanel.add(horizontalScrollBar, BorderLayout.SOUTH);

      myPanel = new JPanel(new CardLayout());
      myPanel.add(contentPanel, CARD_CONTENT);
      myPanel.add(getNoDataForRange(), CARD_EMPTY_INFO);

      myObserver = new AspectObserver();
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
    @NotNull private final HTreeChart<MethodModel> myChart;
    private Point myLastPopupPoint;

    TreeChartNavigationHandler(@NotNull HTreeChart<MethodModel> chart) {
      myChart = chart;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      handlePopup(e);
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
        myLastPopupPoint = e.getPoint();
      }
    }

    @Nullable
    private CodeLocation getCodeLocation() {
      HNode<MethodModel> n = myChart.getNodeAt(myLastPopupPoint);
      if (n == null || n.getData() == null) {
        return null;
      }
      MethodModel method = n.getData();
      return new CodeLocation.Builder(method.getClassName()).setMethodSignature(method.getName(), method.getSignature()).build();
    }
  }

  private static class NameValueNodeComparator implements Comparator<DefaultMutableTreeNode> {
    @Override
    public int compare(DefaultMutableTreeNode o1, DefaultMutableTreeNode o2) {
      return ((CpuTreeNode)o1.getUserObject()).getMethodName().compareTo(((CpuTreeNode)o2.getUserObject()).getMethodName());
    }
  }

  private static class DoubleValueNodeComparator implements Comparator<DefaultMutableTreeNode> {
    private final Function<CpuTreeNode, Double> myGetter;

    DoubleValueNodeComparator(Function<CpuTreeNode, Double> getter) {
      myGetter = getter;
    }

    @Override
    public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
      CpuTreeNode o1 = ((CpuTreeNode)a.getUserObject());
      CpuTreeNode o2 = ((CpuTreeNode)b.getUserObject());
      return Double.compare(myGetter.apply(o1), myGetter.apply(o2));
    }
  }

  private static class DoubleValueCellRenderer extends ColoredTreeCellRenderer {
    private final Function<CpuTreeNode, Double> myGetter;
    private final boolean myPercentage;
    private final int myAlignment;

    public DoubleValueCellRenderer(Function<CpuTreeNode, Double> getter, boolean percentage, int alignment) {
      myGetter = getter;
      myPercentage = percentage;
      myAlignment = alignment;
      setTextAlign(alignment);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      CpuTreeNode node = getNode(value);
      if (node != null) {
        double v = myGetter.apply(node);
        if (myPercentage) {
          CpuTreeNode root = getNode(tree.getModel().getRoot());
          append(String.format("%.2f%%", v / root.getTotal() * 100));
        }
        else {
          append(String.format("%,.0f", v));
        }
      }
      else {
        // TODO: We should improve the visual feedback when no data is available.
        append(value.toString());
      }
      if (myAlignment == SwingConstants.LEFT) {
        setIpad(ProfilerLayout.TABLE_COLUMN_CELL_INSETS);
      }
      else {
        setIpad(ProfilerLayout.TABLE_COLUMN_RIGHT_ALIGNED_CELL_INSETS);
      }
    }
  }

  private static class MethodNameRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode &&
          ((DefaultMutableTreeNode)value).getUserObject() instanceof CpuTreeNode) {
        CpuTreeNode node = (CpuTreeNode)((DefaultMutableTreeNode)value).getUserObject();
        if (node.getMethodName().isEmpty()) {
          setIcon(AllIcons.Debugger.ThreadSuspended);
          append(node.getClassName());
        }
        else {
          setIcon(PlatformIcons.METHOD_ICON);
          append(node.getMethodName() + "()");
          if (node.getClassName() != null) {
            append(" (" + node.getClassName() + ")", new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
          }
        }
      }
      else {
        append(value.toString());
      }
    }
  }
}
