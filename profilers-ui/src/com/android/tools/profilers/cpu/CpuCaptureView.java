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


import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.HNode;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.TabsPanel;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

class CpuCaptureView {
  private static final Map<String, CaptureModel.Details.Type> TABS = ImmutableMap.of(
    "Top Down", CaptureModel.Details.Type.TOP_DOWN,
    "Bottom Up", CaptureModel.Details.Type.BOTTOM_UP,
    "Chart", CaptureModel.Details.Type.CHART);

  private static final Map<CaptureModel.Details.Type, Consumer<FeatureTracker>> CAPTURE_TRACKERS = ImmutableMap.of(
    CaptureModel.Details.Type.TOP_DOWN, FeatureTracker::trackSelectCaptureTopDown,
    CaptureModel.Details.Type.BOTTOM_UP, FeatureTracker::trackSelectCaptureBottomUp,
    CaptureModel.Details.Type.CHART, FeatureTracker::trackSelectCaptureFlameChart);

  private static final Comparator<DefaultMutableTreeNode> DEFAULT_SORT_ORDER =
    Collections.reverseOrder(new DoubleValueNodeComparator(CpuTreeNode::getTotal));

  @NotNull
  private final CpuProfilerStageView myView;

  private final JPanel myPanel;

  private final TabsPanel myTabsPanel;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CaptureModel.Details, CaptureDetailsView> myBinder;

  CpuCaptureView(@NotNull CpuProfilerStageView view) {
    myView = view;
    myPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    myTabsPanel = view.getIdeComponents().createTabsPanel();

    for (String label : TABS.keySet()) {
      myTabsPanel.addTab(label, new JPanel(new BorderLayout()));
    }
    myTabsPanel.setOnSelectionChange(this::setCaptureDetailToTab);

    JComboBox<ClockType> clockTypeCombo = new ComboBox<>();
    JComboBoxView clockTypes =
      new JComboBoxView<>(clockTypeCombo, view.getStage().getAspect(), CpuProfilerAspect.CLOCK_TYPE,
                          view.getStage()::getClockTypes, view.getStage()::getClockType, view.getStage()::setClockType);
    clockTypes.bind();
    clockTypeCombo.setRenderer(new ClockTypeCellRenderer());

    myPanel.add(clockTypeCombo, new TabularLayout.Constraint(0, 1));
    myPanel.add(myTabsPanel.getComponent(), new TabularLayout.Constraint(0, 0, 2, 2));

    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureModel.TopDown.class, TopDownView::new);
    myBinder.bind(CaptureModel.BottomUp.class, BottomUpView::new);
    myBinder.bind(CaptureModel.TreeChart.class, TreeChartView::new);

    updateView();
  }

  void updateView() {
    // Clear the content of all the tabs
    for (Component tab : myTabsPanel.getTabsComponents()) {
      // In the constructor, we make sure to use JPanel as root components of the tabs.
      assert tab instanceof JPanel;
      ((JPanel)tab).removeAll();
    }

    CaptureModel.Details details = myView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    // Update the current selected tab
    if (myTabsPanel.getSelectedTab() == null || !TABS.get(myTabsPanel.getSelectedTab()).equals(details.getType())) {
      for (String tab : TABS.keySet()) {
        if (TABS.get(tab).equals(details.getType())) {
          myTabsPanel.selectTab(tab);
        }
      }
    }
    // Update selected tab content. As we need to update the content of the tabs dynamically,
    // we use a JPanel (set on the constructor) to wrap the content of each tab's content.
    // This is required because JBTabsImpl doesn't behave consistently when setting tab's component dynamically.
    JComponent selectedTab = myTabsPanel.getSelectedTabComponent();
    assert selectedTab instanceof JPanel;
    selectedTab.add(myBinder.build(myView, details).getComponent(), BorderLayout.CENTER);
  }

  void setCaptureDetailToTab() {
    CaptureModel.Details.Type type = TABS.get(myTabsPanel.getSelectedTab());
    myView.getStage().setCaptureDetails(type);

    // TODO: Move this logic into setCaptureDetails later. Right now, if we do it, we track the
    // event several times instead of just once after taking a capture. setCaptureDetails should
    // probably have a guard condition.
    FeatureTracker tracker = myView.getStage().getStudioProfilers().getIdeServices().getFeatureTracker();
    CAPTURE_TRACKERS.getOrDefault(type, featureTracker -> {}).accept(tracker);
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
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MethodNameRenderer())
                   .setComparator(new NameValueNodeComparator()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Self (μs)")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getSelf, false))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getSelf)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getSelf, true))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getSelf)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Children (μs)")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getChildrenTotal, false))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getChildrenTotal)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getChildrenTotal, true))
                   .setComparator(new DoubleValueNodeComparator(CpuTreeNode::getChildrenTotal)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Total (μs)")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.RIGHT)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getTotal, false))
                   .setComparator(DEFAULT_SORT_ORDER))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getTotal, true))
                   .setComparator(DEFAULT_SORT_ORDER))
      .setTreeSorter(sorter)
      .setBackground(ProfilerColors.DEFAULT_BACKGROUND)
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

  private abstract static class CaptureDetailsView {
    protected JComponent myComponent;

    @NotNull
    protected JComponent getComponent() {
      return myComponent;
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

  private static class TopDownView extends CaptureDetailsView {
    private TopDownView(@NotNull CpuProfilerStageView view, @NotNull CaptureModel.TopDown topDown) {
      TopDownTreeModel model = topDown.getModel();
      if (model == null) {
        myComponent = new JLabel("No data available");
        return;
      }

      JTree tree = new Tree();
      myComponent = setUpCpuTree(tree, model, view);
      expandTreeNodes(tree);
    }
  }

  private static class BottomUpView extends CaptureDetailsView {
    private BottomUpView(@NotNull CpuProfilerStageView view, @NotNull CaptureModel.BottomUp bottomUp) {
      BottomUpTreeModel model = bottomUp.getModel();
      if (model == null) {
        myComponent = new JLabel("No data available");
        return;
      }

      JTree tree = new Tree();
      myComponent = setUpCpuTree(tree, model, view);
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
        }
      });
    }
  }

  private static class TreeChartView extends CaptureDetailsView {
    private TreeChartView(@NotNull CpuProfilerStageView view, @NotNull CaptureModel.TreeChart treeChart) {
      HTreeChart<MethodModel> chart = new HTreeChart<>(treeChart.getRange());
      chart.setHRenderer(new SampledMethodUsageHRenderer());
      chart.setHTree(treeChart.getNode());
      myComponent = chart;

      view.getIdeComponents()
        .installNavigationContextMenu(chart, view.getStage().getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
          HNode<MethodModel> node = chart.getHoveredNode();
          if (node == null || node.getData() == null) {
            return null;
          }
          MethodModel method = node.getData();
          return new CodeLocation.Builder(method.getClassName()).setMethodSignature(method.getName(), method.getSignature()).build();
        });
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

    DoubleValueCellRenderer(Function<CpuTreeNode, Double> getter, boolean percentage) {
      myGetter = getter;
      myPercentage = percentage;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      setTextAlign(SwingConstants.RIGHT);
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
        if (node != null) {
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
      }
      else {
        append(value.toString());
      }
    }
  }
}
