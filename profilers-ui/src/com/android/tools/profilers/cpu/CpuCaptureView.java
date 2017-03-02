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

import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.HNode;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.common.CodeLocation;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTabbedPane;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

class CpuCaptureView {
  private static final Map<String, CpuProfilerStage.CaptureDetails.Type> TABS = ImmutableMap.of(
    "Top Down", CpuProfilerStage.CaptureDetails.Type.TOP_DOWN,
    "Bottom Up", CpuProfilerStage.CaptureDetails.Type.BOTTOM_UP,
    "Chart", CpuProfilerStage.CaptureDetails.Type.CHART);

  private static final Comparator<DefaultMutableTreeNode> DEFAULT_SORT_ORDER =
    Collections.reverseOrder(new DoubleValueNodeComparator(CpuTreeNode::getTotal));

  @NotNull
  private final CpuProfilerStageView myView;

  @NotNull
  private final JBTabbedPane myPanel;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CpuProfilerStage.CaptureDetails, CaptureDetailsView> myBinder;

  CpuCaptureView(@NotNull CpuProfilerStageView view) {
    myView = view;

    myPanel = new JBTabbedPane();
    for (String label : TABS.keySet()) {
      myPanel.add(label, new JPanel());
    }
    myPanel.getModel().addChangeListener(event -> setCaptureDetailToTab());

    myBinder = new ViewBinder<>();
    myBinder.bind(CpuProfilerStage.TopDown.class, TopDownView::new);
    myBinder.bind(CpuProfilerStage.BottomUp.class, BottomUpView::new);
    myBinder.bind(CpuProfilerStage.TreeChart.class, TreeChartView::new);

    updateView();
  }

  void updateView() {
    for (int i = 0; i < myPanel.getTabCount(); ++i) {
      myPanel.setComponentAt(i, new JPanel());
    }

    CpuProfilerStage.CaptureDetails details = myView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    int current = myPanel.getSelectedIndex();
    for (int i = 0; i < myPanel.getTabCount(); i++) {
      String title = myPanel.getTitleAt(i);
      if (current != i && TABS.get(title).equals(details.getType())) {
        myPanel.setSelectedIndex(i);
        current = i;
        break;
      }
    }
    myPanel.setComponentAt(current, myBinder.build(myView, details).getComponent());
  }

  void setCaptureDetailToTab() {
    int index = myPanel.getSelectedIndex();
    myView.getStage().setCaptureDetails(TABS.get(myPanel.getTitleAt(index)));
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
      .setBackground(ProfilerColors.MONITOR_BACKGROUND)
      .build();
  }

  @Nullable
  private static CodeLocation getCodeLocation(@NotNull JTree tree) {
    if (tree.getSelectionPath() == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getSelectionPath().getLastPathComponent();
    CpuTreeNode cpuNode = (CpuTreeNode)node.getUserObject();
    return new CodeLocation(cpuNode.getClassName(), null, cpuNode.getMethodName(), cpuNode.getSignature(),
                            CodeLocation.INVALID_LINE_NUMBER);
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

  private static class TopDownView extends CaptureDetailsView {
    private TopDownView(@NotNull CpuProfilerStageView view, @NotNull CpuProfilerStage.TopDown topDown) {
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
    private BottomUpView(@NotNull CpuProfilerStageView view, @NotNull CpuProfilerStage.BottomUp bottomUp) {
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
    private TreeChartView(@NotNull CpuProfilerStageView view, @NotNull CpuProfilerStage.TreeChart treeChart) {
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
          return new CodeLocation(method.getClassName(), null, method.getName(), method.getSignature(), CodeLocation.INVALID_LINE_NUMBER);
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
