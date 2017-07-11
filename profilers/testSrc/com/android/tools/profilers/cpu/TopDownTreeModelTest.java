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

import com.android.tools.adtui.model.Range;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TopDownTreeModelTest {
  @Test
  public void testTreeUpdate() throws Exception {
    CaptureNode tree = TopDownNodeTest.createTree();
    TopDownNode topDown = new TopDownNode(tree);

    Range range = new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    CpuTreeModel model = new TopDownTreeModel(range, topDown);

    TreeNode root = (TreeNode)model.getRoot();
    assertEquals("A", getId(root));
    assertEquals(ImmutableSet.of("B", "C"), getChildrenIds(root));
    assertEquals(ImmutableSet.of("D", "E", "G"), getChildrenIds(getChild(root, "B")));
    assertEquals(ImmutableSet.of("F"), getChildrenIds(getChild(root, "C")));

    // Test the total values
    assertEquals(    30, getTotal(root, "A"), 0);
    assertEquals( 8 + 7, getTotal(root, "A", "B"), 0);
    assertEquals(     2, getTotal(root, "A", "B", "D"), 0);
    assertEquals( 2 + 3, getTotal(root, "A", "B", "E"), 0);
    assertEquals(     4, getTotal(root, "A", "B", "G"), 0);
    assertEquals(     6, getTotal(root, "A", "C"), 0);
    assertEquals(     2, getTotal(root, "A", "C", "F"), 0);

    // Test the children total values
    assertEquals(    21, getChildrenTotal(root, "A"), 0);
    assertEquals(    11, getChildrenTotal(root, "A", "B"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "D"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "E"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "G"), 0);
    assertEquals(     2, getChildrenTotal(root, "A", "C"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "C", "F"), 0);

    // Chop the tree to 0 - 10
    range.set(0, 10);
    root = (TreeNode)model.getRoot();
    assertEquals("A", getId(root));
    assertEquals(ImmutableSet.of("B"), getChildrenIds(root));
    assertEquals(ImmutableSet.of("D", "E"), getChildrenIds(getChild(root, "B")));

    // Test the total values
    assertEquals(    10, getTotal(root, "A"), 0);
    assertEquals(     8, getTotal(root, "A", "B"), 0);
    assertEquals(     2, getTotal(root, "A", "B", "D"), 0);
    assertEquals(     2, getTotal(root, "A", "B", "E"), 0);

    // Test the children total values
    assertEquals(     8, getChildrenTotal(root, "A"), 0);
    assertEquals(     4, getChildrenTotal(root, "A", "B"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "D"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "E"), 0);

    // And now to 18, 25
    range.set(8, 25);
    root = (TreeNode)model.getRoot();
    assertEquals("A", getId(root));
    assertEquals(ImmutableSet.of("B", "C"), getChildrenIds(root));
    assertEquals(ImmutableSet.of("E"), getChildrenIds(getChild(root, "B")));
    assertEquals(ImmutableSet.of("F"), getChildrenIds(getChild(root, "C")));

    // Test the total values
    assertEquals(    17, getTotal(root, "A"), 0);
    assertEquals( 1 + 3, getTotal(root, "A", "B"), 0);
    assertEquals( 1 + 3, getTotal(root, "A", "B", "E"), 0);
    assertEquals(     6, getTotal(root, "A", "C"), 0);
    assertEquals(     2, getTotal(root, "A", "C", "F"), 0);

    // Test the children total values
    assertEquals(    10, getChildrenTotal(root, "A"), 0);
    assertEquals(     4, getChildrenTotal(root, "A", "B"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "B", "E"), 0);
    assertEquals(     2, getChildrenTotal(root, "A", "C"), 0);
    assertEquals(     0, getChildrenTotal(root, "A", "C", "F"), 0);
  }

  private static double getTotal(TreeNode node, String id, String... ids) {
    node = getNode(node, id, ids);
    return getTotal(node);
  }

  private static double getChildrenTotal(TreeNode node, String id, String... ids) {
    node = getNode(node, id, ids);
    return getChildrenTotal(node);
  }

  private static TreeNode getNode(TreeNode node, String id, String[] ids) {
    assertEquals(id, getId(node));
    for (String s : ids) {
      node = getChild(node, s);
    }
    return node;
  }

  private static TreeNode getChild(TreeNode node, String id) {
    for (int i = 0; i < node.getChildCount(); i++) {
      TreeNode child = node.getChildAt(i);
      if (getId(child).equals(id)) {
        return child;
      }
    }
    return null;
  }

  private static double getTotal(TreeNode node) {
    return ((TopDownNode)(((DefaultMutableTreeNode)node).getUserObject())).getTotal();
  }

  private static double getChildrenTotal(TreeNode node) {
    return ((TopDownNode)(((DefaultMutableTreeNode)node).getUserObject())).getChildrenTotal();
  }

  private static String getId(TreeNode node) {
    return ((TopDownNode)(((DefaultMutableTreeNode)node).getUserObject())).getId();
  }

  private static Set<String> getChildrenIds(TreeNode node) {
    Set<String> set = new HashSet<>();
    for (int i = 0; i < node.getChildCount(); i++) {
      set.add(getId(node.getChildAt(i)));
    }
    return set;
  }
}