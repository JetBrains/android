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
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.ProfilerTimeline;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Function;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public class CpuCaptureView {

  @NotNull
  private final CpuCapture myCapture;
  private final HTreeChart<MethodModel> myCaptureTreeChart;
  private final JBTabbedPane myPanel;
  private final JTree myTopDownTree;
  private final JTree myBottomUpTree;
  private final CpuProfilerStageView myView;
  private final CpuTraceTreeSorter myTopDownTreeSorter;
  private final CpuTraceTreeSorter myBottomUpTreeSorter;
  private final Comparator<DefaultMutableTreeNode> myDefaultSortOrder;

  public CpuCaptureView(@NotNull CpuCapture capture, @NotNull CpuProfilerStageView view) {

    ProfilerTimeline timeline = view.getStage().getStudioProfilers().getTimeline();

    // Reverse the order as the default ordering is SortOrder.ASCENDING
    myDefaultSortOrder = Collections.reverseOrder(new DoubleValueNodeComparator(CpuTreeNode::getTotal));
    myCapture = capture;
    myView = view;

    myCaptureTreeChart = new HTreeChart<>(timeline.getSelectionRange());
    myCaptureTreeChart.setHRenderer(new SampledMethodUsageHRenderer());

    myTopDownTree = new JTree();
    myBottomUpTree = new JTree();
    myTopDownTreeSorter = new CpuTraceTreeSorter(myTopDownTree);
    myBottomUpTreeSorter = new CpuTraceTreeSorter(myBottomUpTree);

    myBottomUpTree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
        ((BottomUpTreeModel)myBottomUpTree.getModel()).expand(node);
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
      }
    });

    myPanel = new JBTabbedPane();
    myPanel.addTab("Top Down", createColumnTree(myTopDownTree, myTopDownTreeSorter));
    myPanel.addTab("Bottom Up", createColumnTree(myBottomUpTree, myBottomUpTreeSorter));
    myPanel.addTab("Chart", myCaptureTreeChart);

    updateThread();
  }

  @NotNull
  private JComponent createColumnTree(@NotNull JTree tree, @NotNull CpuTraceTreeSorter treeSorter) {
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
                   .setComparator(myDefaultSortOrder))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("%")
                   .setPreferredWidth(50)
                   .setRenderer(new DoubleValueCellRenderer(CpuTreeNode::getTotal, true))
                   .setComparator(myDefaultSortOrder))
      .setTreeSorter(treeSorter)
      .build();
  }

  public void updateThread() {
    int id = myView.getStage().getSelectedThread();
    // Updates the horizontal tree displayed in capture panel
    HNode<MethodModel> node = myCapture.getCaptureNode(id);
    myCaptureTreeChart.setHTree(node);
    // Updates the topdown column tree displayed in capture panel
    Range selectionRange = myView.getTimeline().getSelectionRange();
    TopDownTreeModel topDownModel = node == null ? null : new TopDownTreeModel(selectionRange, new TopDownNode(node));
    myTopDownTree.setModel(topDownModel);
    myTopDownTreeSorter.setModel(topDownModel, myDefaultSortOrder);
    expandTreeNodes(myTopDownTree);
    // Updates the bottom up tree
    BottomUpTreeModel bottomUpModel = node == null ? null : new BottomUpTreeModel(selectionRange, new BottomUpNode(node));
    myBottomUpTree.setModel(bottomUpModel);
    myBottomUpTreeSorter.setModel(bottomUpModel, myDefaultSortOrder);
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
            append(node.getPackage());
          }
          else {
            setIcon(PlatformIcons.METHOD_ICON);
            append(node.getMethodName() + "()");
            if (node.getPackage() != null) {
              append(" (" + node.getPackage() + ")", new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
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
