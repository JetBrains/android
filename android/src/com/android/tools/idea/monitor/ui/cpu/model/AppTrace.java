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

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.chart.hchart.Method;
import com.android.tools.adtui.chart.hchart.MethodUsage;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.monitor.ui.cpu.view.AppStatTreeNode;
import com.android.utils.SparseArray;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.Stack;

// Abstraction layer so GUI can process trace originating from ART or SimplePerf seamlessly.
public abstract class AppTrace {

  public enum Source {ART, SIMPLEPERF}

  public abstract Source getSource();
  public abstract String getSeparator();

  // Gives an opportunity to transform a raw trace into an HNode tree. Always called before |getThreadsGraph|.
  abstract public void parse() throws IOException;

  // Called before rendition.
  abstract public SparseArray<HNode<Method>> getThreadsGraph();

  private SparseArray<HNode<MethodUsage>> myTopDownStats;

  private SparseArray<HNode<MethodUsage>> myBottomUpStats;

  private SparseArray<JComponent> myTopdownTrees;

  private SparseArray<JComponent> myBottomUpTrees;

  public SparseArray<HNode<MethodUsage>> getTopdownStats() {
    if (myTopDownStats == null) {
      generateTopDownGraphs();
    }
    return myTopDownStats;
  }

  public SparseArray<HNode<MethodUsage>> getBottomupStats() {
    if (myBottomUpStats == null) {
      generateBottomUpGraphs();
    }
    return myBottomUpStats;
  }

  // BottomUp Graphs are reversed flamegraphs (a.k.a TopDownGraphs). This method generates all bottom up graphs
  // for all known top down graphs
  private void generateBottomUpGraphs() {
    myBottomUpStats = new SparseArray<>();
    SparseArray<HNode<MethodUsage>> topDownGraphs = getTopdownStats();
    for (int i = 0; i < topDownGraphs.size(); i++) {
      int threadPid = topDownGraphs.keyAt(i);
      HNode<MethodUsage> topDownGraph = topDownGraphs.get(threadPid);
      HNode<MethodUsage> bottomUpGraph = generateBottomUpGraph(topDownGraph);
      myBottomUpStats.put(threadPid, bottomUpGraph);
    }
  }

  // Reverse a topdown graph into a bottom up graph.
  private HNode<MethodUsage> generateBottomUpGraph(HNode<MethodUsage> topDownGraph) {
    HNode<MethodUsage> bottomUpGraph = new HNode<>();
    MethodUsage fakeMethodUsageRoot = new MethodUsage();
    fakeMethodUsageRoot.setNamespace("");
    fakeMethodUsageRoot.setName("");
    fakeMethodUsageRoot.setExclusivePercentage(1);
    bottomUpGraph.setData(fakeMethodUsageRoot);

    Stack<HNode<MethodUsage>> stack = new Stack<>();
    // Intentionaly removing the first child since it is just here to hold top level childs.
    topDownGraph = topDownGraph.getFirstChild();
    reverseTopDownGraph(topDownGraph, bottomUpGraph, stack);
    return bottomUpGraph;
  }

  private void reverseTopDownGraph(HNode<MethodUsage> topDownNode, HNode<MethodUsage> bottomUpGraph, Stack<HNode<MethodUsage>> stack) {
    stack.push(topDownNode);
    if (topDownNode.getData().getExclusivePercentage() > 0) {
      // Add node and all its ancestor to bottomup graph
      HNode<MethodUsage> tailNode = null;
      HNode<MethodUsage> headNode = null;
      for (int i = stack.size() - 1; i >= 0; i--) {
        HNode<MethodUsage> stackedNode = stack.get(i);
        HNode newNode = new HNode<>();
        if (headNode == null) {
          headNode = newNode;
        }
        // Copy stackedNode into new Node.
        newNode.setStart(stackedNode.getStart());
        newNode.setEnd(stackedNode.getEnd());
        newNode.setDepth(stack.size() - i);
        newNode.setData(stackedNode.getData());
        if (tailNode != null) {
          tailNode.addHNode(newNode);
        }
        tailNode = newNode;
      }

      // Try to see if this method call was already present in the bottomup. If this is the case we can merge it into the bottomup graph.
      boolean mergedInGraph = false;
      for(HNode<MethodUsage> child : bottomUpGraph.getChildren()) {
        if (child.getData().getName().equals(headNode.getData().getName()) &&
            child.getData().getNameSpace().equals(headNode.getData().getNameSpace())) {
          if (headNode.getChildren().size() > 0) {
            child.addHNode(headNode.getFirstChild());
          }
          mergedInGraph = true;
          break;
        }
      }
      if (!mergedInGraph) {
        bottomUpGraph.addHNode(headNode);
      }
    }
    for (HNode n : topDownNode.getChildren()) {
      reverseTopDownGraph(n, bottomUpGraph, stack);
    }
    stack.pop();
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
      usageGraphRoot.setData(new MethodUsage());
      long totalDuration = usageGraphRoot.duration();

      // Graph generation is a two passes algorithm:
      // 1/ Merge all the stackframes.
      // 2/ Generate all the node data and layout (start,end)
      mergeStackTraces(execGraph, usageGraphRoot);
      generateStats(usageGraphRoot, totalDuration);

      myTopDownStats.put(threadPid, usageGraphRoot);
    }
  }

  private void mergeStackTraces(HNode<Method> execNode, HNode<MethodUsage> usageNode) {
    for (HNode<Method> childExecNode : execNode.getChildren()) {
      HNode<MethodUsage> methodUsageNode = findOrCopyNodeInUsageGraph(childExecNode, usageNode);
      methodUsageNode.getData().increaseInclusiveDuration(childExecNode.duration());
    }

    // Recurse over all childs
    for (HNode<Method> childExecNode : execNode.getChildren()) {
      HNode<MethodUsage> methodUsageNode = findNodeInUsageGraph(childExecNode, usageNode);
      mergeStackTraces(childExecNode, methodUsageNode);
    }
  }

  private void generateStats(HNode<MethodUsage> usageNode, long totalDuration) {

    long childTotalRuntime = 0;
    for (HNode<MethodUsage> childExecNode : usageNode.getChildren()) {
      childTotalRuntime += childExecNode.getData().getInclusiveDuration();
    }

    long exlusiveDuration = usageNode.getData().getInclusiveDuration() - childTotalRuntime;
    usageNode.getData().setExclusiveDuration(exlusiveDuration);
    usageNode.getData().setExclusivePercentage(exlusiveDuration / (float)totalDuration);

    if (childTotalRuntime == 0) {// This is a leaf. Recursion stops here.
      return;
    }

    long cursor = usageNode.getStart();
    for (HNode<MethodUsage> childUsageNode : usageNode.getChildren()) {
      MethodUsage methodUsage = childUsageNode.getData();
      childUsageNode.setStart(cursor);
      cursor += methodUsage.getInclusiveDuration();
      childUsageNode.setEnd(cursor);
      methodUsage.setInclusivePercentage(childUsageNode.duration() / (float)totalDuration);
    }

    // Recurse over all childs
    for (HNode<MethodUsage> child : usageNode.getChildren()) {
      generateStats(child, totalDuration);
    }
  }

  // Returns true is Method and MethodUsage refer to the same logic app method.
  private boolean methodEqual(Method m, MethodUsage mu) {
    return m.getName().equals(mu.getName()) && m.getNameSpace().equals(mu.getNameSpace());
  }

  private HNode<MethodUsage> findNodeInUsageGraph(HNode<Method> node, HNode<MethodUsage> usageGraph) {
    // Search for the node in the graph.
    for (HNode<MethodUsage> prospect : usageGraph.getChildren()) {
      if (methodEqual(node.getData(), prospect.getData())) {
        return prospect; // Found
      }
    }
    return null;
  }

  private HNode<MethodUsage> findOrCopyNodeInUsageGraph(HNode<Method> node, HNode<MethodUsage> usageGraph) {
    HNode<MethodUsage> foundNode = findNodeInUsageGraph(node, usageGraph);
    if (foundNode != null) {
      return foundNode;
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
    // Intentionally discard first child
    graph = graph.getFirstChild();
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
                       append(generateNameForColumn(node.getMethodNamespace(), node.getMethodName(), getSeparator()));
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

  //
  private String generateNameForColumn(String namespace, String method, String separator) {
    if (method == null) {
      method = "";
    }

    if (namespace == null) {
      namespace = "";
    }
    if (namespace.isEmpty() || method.isEmpty()) {
      separator = "";
    }
    return namespace + separator + method;
  }

  // Lazy initialized BottomUp JTrees for bottomup statistic display.
  public SparseArray<JComponent> getBottomUpTrees() {
    if (myBottomUpTrees != null) {
      return myBottomUpTrees;
    }

    myBottomUpTrees = new SparseArray<JComponent>();
    SparseArray<HNode<MethodUsage>> threadStats = getBottomupStats();
    for (int i = 0; i < threadStats.size(); i++) {
      int threadPid = threadStats.keyAt(i);
      JComponent tree = generateBottomUpTree(threadStats.get(threadStats.keyAt(i)));
      myBottomUpTrees.put(threadPid, tree);
    }
    return myBottomUpTrees;
  }

  private JComponent generateBottomUpTree(HNode<MethodUsage> graph) {
    AppStatTreeNode top = new AppStatTreeNode();
    convertGraphToTree(graph, top);

    JTree tree = new JTree(top);
    JComponent bottomUpColumnTree = new ColumnTreeBuilder(tree)
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
                       append(generateNameForColumn(node.getMethodNamespace(), node.getMethodName(), getSeparator()));
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
    return bottomUpColumnTree;
  }


  private void convertGraphToTree(HNode<MethodUsage> graph, AppStatTreeNode treeNode) {
    MethodUsage top = graph.getData();

    if (top != null) {
      treeNode.setPercentageInclusive(top.getInclusivePercentage());
      treeNode.setPercentageExclusive(top.getExclusivePercentage());

      treeNode.setRuntimeExclusive(top.getExclusiveDuration());
      treeNode.setRuntimeInclusive(top.getInclusiveDuration());

      treeNode.setMethodName(top.getName());
      treeNode.setMethodNamespace(top.getNameSpace());
    }

    for (HNode<MethodUsage> n : graph.getChildren()) {
      AppStatTreeNode newNode = new AppStatTreeNode();
      treeNode.add(newNode);
      convertGraphToTree(n, newNode);
    }
  }
}
