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
package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.adtui.chart.hchart.HNode;
import com.android.tools.adtui.chart.hchart.Method;
import com.android.tools.adtui.chart.hchart.MethodUsage;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.monitor.ui.cpu.view.AppStatTreeNode;
import com.android.utils.SparseArray;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

// Abstraction layer so GUI can process trace originating from ART or SimplePerf seamlessly.
public abstract class AppTrace {

  enum Source {ART, SIMPLEPERF};

  abstract public Source getSource();

  // Gives an opportunity to transform a raw trace into an HNode tree. Always called before |getThreadsGraph|.
  abstract public void parse() throws IOException;

  // Called before rendition.
  abstract public SparseArray<HNode<Method>> getThreadsGraph();

  private SparseArray<HNode<MethodUsage>> myTopDownStats = null;

  private SparseArray<JComponent> myTopdownTrees = null;

  public SparseArray<HNode<MethodUsage>> getTopdownStats() {
    if (myTopDownStats == null) {
      generateTopDownGraphs();
    }
    return myTopDownStats;
  }

  // TopDown graph are flamegraphs: The execution stacks merged together (on a per-thread basis).
  // TopDown graphs are used in flamegraphs but also in top-down and bottom-up statistics display.
  private void generateTopDownGraphs() {
    myTopDownStats = new SparseArray<>();
    SparseArray<HNode<Method>> threadGraphs = getThreadsGraph();
    for (int i = 0; i < threadGraphs.size(); i++) {
      int threadPid = threadGraphs.keyAt(i);
      HNode<Method> execGraph = threadGraphs.get(threadPid);

      HNode<MethodUsage> usageGraphRoot = new HNode<>();
      usageGraphRoot.setStart(0);
      usageGraphRoot.setEnd(execGraph.duration());
      long totalDuration = usageGraphRoot.duration();
      generateTopDownGraphsForThread(execGraph, usageGraphRoot, totalDuration);
      myTopDownStats.put(threadPid, usageGraphRoot);
    }
  }

  // Merge all stackframes from |execNode| tree together and store the result in a |parentUsageNode|.
  // totalDuration is needed to calculate percentage statistics associated with each node.
  private void generateTopDownGraphsForThread(HNode<Method> execNode, HNode<MethodUsage> parentUsageNode, long totalDuration) {
    HNode<MethodUsage> usageNode = findOrCopyNodeInUsageGraph(execNode, parentUsageNode);
    MethodUsage usageNodeMu = usageNode.getData();

    // 1. Set usageNode exclusive time.
    long childTotalRuntime = 0;
    for (HNode childExecNode : execNode.getChildren()) {
      childTotalRuntime += childExecNode.duration();
    }
    if (childTotalRuntime == 0) { // This is a leaf. Recursion stops here.
      usageNodeMu.setExclusiveDuration(execNode.duration());
      usageNodeMu.setExclusivePercentage(execNode.duration()/(float)totalDuration);
      return;
    }
    usageNodeMu.setExclusiveDuration(execNode.duration() - childTotalRuntime);
    usageNodeMu.setExclusivePercentage((execNode.duration() - childTotalRuntime) / (float)totalDuration);


    // 2. Create all usage child myNodes and set their inclusive time.
    for (HNode<Method> childExecNode : execNode.getChildren()) {
      // Find child in the usageGraph
      HNode<MethodUsage> methodUsageNode = findOrCopyNodeInUsageGraph(childExecNode, usageNode);
      methodUsageNode.getData().increaseInclusiveDuration(childExecNode.duration());
    }


    // 3. Calculate each child inclusive percentage, set start and end values.
    long cursor = usageNode.getStart();
    for (HNode<MethodUsage> childUsageNode : usageNode.getChildren()) {
      MethodUsage methodUsage = childUsageNode.getData();
      childUsageNode.setStart(cursor);
      cursor = cursor + methodUsage.getInclusiveDuration();
      childUsageNode.setEnd(cursor);
      methodUsage.setInclusivePercentage(childUsageNode.duration() / (float)totalDuration);
    }

    // Recurse over all childs
    for (HNode<Method> child : execNode.getChildren()) {
      generateTopDownGraphsForThread(child, usageNode, totalDuration);
    }
  }

  // Returns true is Method and MethodUsage refer to the same logic app method.
  private boolean methodEqual(Method m, MethodUsage mu) {
    return m.getName().equals(mu.getName()) && m.getNameSpace().equals(mu.getNameSpace());
  }

  private HNode<MethodUsage> findOrCopyNodeInUsageGraph(HNode<Method> node, HNode<MethodUsage> usageGraph) {
    // Search for the node in the graph.
    for (HNode<MethodUsage> prospect : usageGraph.getChildren()) {
      if (methodEqual(node.getData(), prospect.getData())) {
        return prospect; // Found
      }
    }

    // Node was not found. We need to create it and add it to the usageGraph before returning it.
    HNode<MethodUsage> newNode = new HNode<>();
    MethodUsage mu = new MethodUsage();
    if (node.getData() != null) {
      mu.setNamespace(node.getData().getNameSpace());
      mu.setName(node.getData().getName());
    }
    newNode.setData(mu);
    newNode.setDepth(node.getDepth());
    usageGraph.addHNode(newNode);
    return newNode;
  }

  // Lazy initialized TopDown JTrees for top-down statistic display.
  public SparseArray<JComponent> getTopDownTrees() {
    if (myTopdownTrees != null) {
      return myTopdownTrees;
    }

    myTopdownTrees = new SparseArray<JComponent>();
    SparseArray<HNode<MethodUsage>> threadStats = getTopdownStats();
    for (int i = 0; i < threadStats.size(); i++) {
      int threadPid = threadStats.keyAt(i);
      JComponent tree = generateTopdownTree(threadStats.get(threadStats.keyAt(i)));
      myTopdownTrees.put(threadPid, tree);
    }
    return myTopdownTrees;
  }

  // Convert a HNode tree into an AppStat tree (better suited for rendition in a ColumnTree).
  private JComponent generateTopdownTree(HNode<MethodUsage> graph) {

    AppStatTreeNode top = new AppStatTreeNode();
    convertGraphToTree(graph, top);

    JTree tree = new JTree(top);
    JComponent topdownColumnTree = new ColumnTreeBuilder(tree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Method")
                   .setRenderer(new ColoredTreeCellRenderer() {
                     @Override
                     public void customizeCellRenderer(@NotNull JTree tree,
                                                       Object value,
                                                       boolean selected,
                                                       boolean expanded,
                                                       boolean leaf,
                                                       int row,
                                                       boolean hasFocus) {
                       AppStatTreeNode node = (AppStatTreeNode)value;
                       append(node.getMethodNamespace() + "." + node.getMethodName());
                     }
                   }))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Inclusive stats")
                   .setRenderer(new ColoredTreeCellRenderer() {
                     @Override
                     public void customizeCellRenderer(@NotNull JTree tree,
                                                       Object value,
                                                       boolean selected,
                                                       boolean expanded,
                                                       boolean leaf,
                                                       int row,
                                                       boolean hasFocus) {
                       AppStatTreeNode node = (AppStatTreeNode)value;
                       append(String.format("%2.1f%%", node.getPercentageInclusive() * 100));
                     }
                   }))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Exclusive stats")
                   .setRenderer(new ColoredTreeCellRenderer() {
                     @Override
                     public void customizeCellRenderer(@NotNull JTree tree,
                                                       Object value,
                                                       boolean selected,
                                                       boolean expanded,
                                                       boolean leaf,
                                                       int row,
                                                       boolean hasFocus) {
                       AppStatTreeNode node = (AppStatTreeNode)value;
                       append(String.format("%2.1f%%", node.getPercentageExclusive() * 100));
                     }
                   }))
      .build();
    return topdownColumnTree;
  }

  private void convertGraphToTree(HNode<MethodUsage> graph, AppStatTreeNode top) {

    MethodUsage mTop;
    while ((mTop = graph.getData()) == null || mTop.getName() == null) {
      graph = graph.getFirstChild();
    }

    top.setPercentageInclusive(mTop.getInclusivePercentage());
    top.setPercentageExclusive(mTop.getExclusivePercentage());

    top.setRuntimeExclusive(mTop.getExclusiveDuration());
    top.setRuntimeInclusive(mTop.getInclusiveDuration());

    top.setMethodName(mTop.getName());
    top.setMethodNamespace(mTop.getNameSpace());

    for (HNode<MethodUsage> n : graph.getChildren()) {
      AppStatTreeNode newNode = new AppStatTreeNode();
      top.add(newNode);
      convertGraphToTree(n, newNode);
    }
  }
}
