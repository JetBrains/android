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

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.RangedTree;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.HNode;
import com.android.tools.profilers.ProfilerTimeline;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.function.Function;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public class CpuCaptureView {

  @NotNull
  private final CpuCapture myCapture;
  private final HTreeChart<MethodModel> myCaptureTreeChart;
  private final JBTabbedPane myPanel;
  private final JTree myTree;
  private final RangedTree myRangedTree;
  private final CpuProfilerStageView myView;

  public CpuCaptureView(@NotNull CpuCapture capture, @NotNull CpuProfilerStageView view) {

    ProfilerTimeline timeline = view.getStage().getStudioProfilers().getTimeline();

    myCapture = capture;
    myView = view;
    myCaptureTreeChart = new HTreeChart<>();
    myCaptureTreeChart.setHRenderer(new SampledMethodUsageHRenderer());
    myCaptureTreeChart.setXRange(timeline.getSelectionRange());

    myTree = new JTree();
    myRangedTree = new RangedTree(timeline.getSelectionRange());
    JComponent columnTree = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("Name")
          .setPreferredWidth(900)
          .setHeaderAlignment(SwingConstants.LEFT)
          .setRenderer(new MethodNameRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("Self (μs)")
          .setPreferredWidth(100)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getSelf, false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("%")
          .setPreferredWidth(50)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getSelf, true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("Children (μs)")
          .setPreferredWidth(100)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getChildrenTotal, false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("%")
          .setPreferredWidth(50)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getChildrenTotal, true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("Total (μs)")
          .setPreferredWidth(100)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getTotal, false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
          .setName("%")
          .setPreferredWidth(50)
          .setRenderer(new DoubleValueCellRenderer(TopDownNode::getTotal, true)))
      .build();

    myPanel = new JBTabbedPane();
    myPanel.addTab("TopDown", columnTree);
    myPanel.addTab("Chart", myCaptureTreeChart);

    updateThread();
  }

  public void updateThread() {
    int id = myView.getStage().getSelectedThread();
    // Updates the horizontal tree displayed in capture panel
    HNode<MethodModel> node = myCapture.getCaptureNode(id);
    myCaptureTreeChart.setHTree(node);
    // Updates the topdown column tree displayed in capture panel
    TopDownTreeModel model = node == null ? null : new TopDownTreeModel(new TopDownNode(node));
    myRangedTree.setModel(model);
    myTree.setModel(model);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  public CpuCapture getCapture() {
    return myCapture;
  }

  public void register(Choreographer choreographer) {
    choreographer.register(myCaptureTreeChart);
    choreographer.register(myRangedTree);
  }

  public void unregister(Choreographer choreographer) {
    choreographer.unregister(myCaptureTreeChart);
    choreographer.unregister(myRangedTree);
  }

  private static TopDownNode getNode(Object value) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    return (TopDownNode)node.getUserObject();
  }

  private static class DoubleValueCellRenderer extends ColoredTreeCellRenderer {
    private final Function<TopDownNode, Double> myGetter;
    private final boolean myPercentage;

    DoubleValueCellRenderer(Function<TopDownNode, Double> getter, boolean percentage) {
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
      TopDownNode node = getNode(value);
      if (node != null)  {
        double v = myGetter.apply(node);
        if (myPercentage) {
          TopDownNode root = getNode(tree.getModel().getRoot());
          append(String.format("%.2f%%", v / root.getTotal() * 100));
        } else {
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
          ((DefaultMutableTreeNode)value).getUserObject() instanceof TopDownNode) {
        TopDownNode node = (TopDownNode)((DefaultMutableTreeNode)value).getUserObject();
        if (node != null) {
          if (node.getMethodName().isEmpty()) {
            setIcon(AllIcons.Debugger.ThreadSuspended);
            append(node.getPackage());
          } else {
            setIcon(PlatformIcons.METHOD_ICON);
            append(node.getMethodName() + "()");
            if (node.getPackage() != null) {
              append(" (" + node.getPackage() + ")", new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
            }
          }
        }
      } else {
        append(value.toString());
      }
    }
  }
}
