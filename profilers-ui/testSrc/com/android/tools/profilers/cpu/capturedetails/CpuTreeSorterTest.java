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

import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.Utils;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.intellij.testFramework.ApplicationRule;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class CpuTreeSorterTest {

  private CpuTraceTreeSorter myTreeSorter;

  private JTree myTree;

  @ClassRule
  public static final ApplicationRule rule = new ApplicationRule();

  /**
   * Compares two topdown nodes by comparing their method names lexicographically.
   */
  private final Comparator<CpuTreeNode> myComparator = (o1, o2) -> {
    assertNotNull(o1);
    assertTrue(o1 instanceof CpuTreeNode);
    assertNotNull(o2);
    assertTrue(o2 instanceof CpuTreeNode);
    CpuTreeNode topDown1 = (CpuTreeNode)o1;
    CpuTreeNode topDown2 = (CpuTreeNode)o2;
    return topDown1.getBase().getMethodModel().getName()
      .compareTo(topDown2.getBase().getMethodModel().getName());
  };

  @Before
  public void setUp() {
    myTree = new JTree();
  }

  @Test
  public void sortedTree() {
    CaptureNode root = newNode("A", 0, 0);
    root.addChild(newNode("B", 0, 0));
    root.addChild(newNode("C", 0, 0));

    CpuTreeModel model = createTreeModel(root);
    myTree.setModel(model);
    myTreeSorter = new CpuTraceTreeSorter(myTree, model, myComparator);

    // It's expected that the root children remains ordered lexicographically
    compareTreeModel(model, "A", "B", "C");
  }

  @Test
  public void unmatchedNodesAlwaysComesAfterOthers() {
    CaptureNode root = newNode("Root", 0, 0);
    root.addChild(newNode("A1", 0, 0));
    root.addChild(newNode("C1", 0, 0));
    root.addChild(newNode("B1", 0, 0));
    root.addChild(newNode("C2", 0, 0, CaptureNode.FilterType.UNMATCH));
    root.addChild(newNode("A2", 0, 0, CaptureNode.FilterType.UNMATCH));
    root.addChild(newNode("B2", 0, 0, CaptureNode.FilterType.UNMATCH));

    CpuTreeModel model = createTreeModel(root);
    myTreeSorter= new CpuTraceTreeSorter(myTree, model, myComparator);

    compareTreeModel(model, "Root", "A1", "B1", "C1", "A2", "B2", "C2");

    myTreeSorter = new CpuTraceTreeSorter(myTree, model, myComparator.reversed());
    compareTreeModel(model, "Root", "C1", "B1", "A1", "C2", "B2", "A2");
  }

  @Test
  public void unsortedTree() {
    CaptureNode root = newNode("A", 0, 0);
    root.addChild(newNode("C", 0, 0));
    root.addChild(newNode("B", 0, 0));

    CpuTreeModel model = createTreeModel(root);
    myTree.setModel(model);
    myTreeSorter = new CpuTraceTreeSorter(myTree, model, myComparator);

    // It's expected that the root children become ordered lexicographically after setting the model in the tree sorter
    compareTreeModel(model, "A", "B", "C");
  }

  @Test
  public void parentIsNotOrdered() {
    // Create a tree model, with method names sorted lexicographically
    CaptureNode root = newNode("Z", 0, 0);
    root.addChild(newNode("B", 0, 0));
    root.addChild(newNode("C", 0, 0));

    CpuTreeModel model = createTreeModel(root);
    myTree.setModel(model);
    myTreeSorter = new CpuTraceTreeSorter(myTree, model, myComparator);

    // It's expected that the the root children remains ordered lexicographically.
    // The root itself, besides being greater than its children, still comes first.
    // Only siblings are considered during sort, not the entire model.
    compareTreeModel(model, "Z", "B", "C");
  }

  private static CpuTreeModel createTreeModel(CaptureNode tree) {
    Range range = new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    return new CpuTreeModel<Aggregate.TopDown>(ClockType.GLOBAL, range, Aggregate.TopDown.rootAt(tree),
                                               work -> {
                                                 Utils.runOnUi(work);
                                                 return Unit.INSTANCE;
                                               });
  }

  /**
   * Compares the nodes of a model with a list of expected strings.
   * The strings should be given in pre-order traversal order.
   */
  private static void compareTreeModel(CpuTreeModel model, String... expected) {
    List<String> nodes = new ArrayList<>();
    preOrderTraversal(model.getRoot(), nodes);
    int i = 0;
    for (String expectedNode : expected) {
      assertEquals(expectedNode, nodes.get(i++));
    }
  }

  private static void preOrderTraversal(TreeNode node, List<String> nodes) {
    String methodName = ((CpuTreeNode)node).getBase().getMethodModel().getName();
    nodes.add(methodName);
    for (int i = 0; i < node.getChildCount(); i++) {
      preOrderTraversal(node.getChildAt(i), nodes);
    }
  }

  private static CaptureNode newNode(String method, long start, long end) {
    return newNode(method, start, end, CaptureNode.FilterType.MATCH);
  }

  @NotNull
  private static CaptureNode newNode(String method, long start, long end, CaptureNode.FilterType filterType) {
    CaptureNode node = new CaptureNode(new SingleNameModel(method));
    node.setStartGlobal(start);
    node.setEndGlobal(start);
    node.setFilterType(filterType);
    node.setStartThread(start);
    node.setEndThread(end);
    return node;
  }
}
