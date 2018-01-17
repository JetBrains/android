/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BottomUpTreeModelTest {
  private BottomUpTreeModel myModel;
  private Range myRange;
  @Before
  public void setUp() {
    myRange = new Range(0, 40);
    myModel = new BottomUpTreeModel(myRange, new BottomUpNode(BottomUpNodeTest.createComplexTree()));
  }

  @Test
  public void aspectFiredAfterTreeModelChange() {
    AspectObserver observer = new AspectObserver();
    int[] treeModelChangeCount = new int[]{0};
    myModel.getAspect().addDependency(observer).onChange(CpuTreeModel.Aspect.TREE_MODEL, () -> ++treeModelChangeCount[0]);

    assertThat(treeModelChangeCount[0]).isEqualTo(0);
    myRange.set(0, 10);
    assertThat(treeModelChangeCount[0]).isEqualTo(1);
  }

  @Test
  public void aspectFiredAfterNodeExpand() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    AspectObserver observer = new AspectObserver();
    int[] treeModelChangeCount = new int[]{0};
    myModel.getAspect().addDependency(observer).onChange(CpuTreeModel.Aspect.TREE_MODEL, () -> ++treeModelChangeCount[0]);

    assertThat(treeModelChangeCount[0]).isEqualTo(0);
    myModel.expand(findNodeOnPath(root, "Root", "B"));
    assertThat(treeModelChangeCount[0]).isEqualTo(1);
  }

  @Test
  public void testExpand() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    checkTraverseOrder(root, " +Root +main - +A +main - +C - - +B +A - +main - - +C +main - - -");
    myModel.expand(findNodeOnPath(root, "Root", "B"));
    checkTraverseOrder(root, " +Root +main - +A +main - +C - - +B +A +C - +main - - +main - - +C +main - - -");
  }

  @Test
  public void nodeExpandedTwice() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    checkTraverseOrder(root, " +Root +main - +A +main - +C - - +B +A - +main - - +C +main - - -");
    myModel.expand(findNodeOnPath(root, "Root", "B"));
    checkTraverseOrder(root, " +Root +main - +A +main - +C - - +B +A +C - +main - - +main - - +C +main - - -");
    myModel.expand(findNodeOnPath(root, "Root", "B"));
    checkTraverseOrder(root, " +Root +main - +A +main - +C - - +B +A +C - +main - - +main - - +C +main - - -");
  }

  @Test
  public void childrenBuiltOnExpandEvenWhenNodeIsInvisible() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();

    myRange.set(35, 40);
    checkTraverseOrder(root, " +Root +main - +B +main - - -");

    BottomUpNode toCheckNode = findBottomUpNodeOnPath((BottomUpNode)root.getUserObject(), "Root", "B", "A");

    assertEquals(0, toCheckNode.getChildren().size());
    myModel.expand(findNodeOnPath(root, "root", "B"));
    assertEquals(2, toCheckNode.getChildren().size());
  }

  private static void checkTraverseOrder(DefaultMutableTreeNode node, String expectedOrder) {
    StringBuilder orderBuilder = new StringBuilder();
    traverse(node, orderBuilder);
    assertEquals(expectedOrder, orderBuilder.toString());
  }

  private static void traverse(@NotNull DefaultMutableTreeNode node, StringBuilder orderBuilder) {
    orderBuilder.append(" +").append(((BottomUpNode)node.getUserObject()).getId());
    for (int i = 0; i < node.getChildCount(); ++i) {
      traverse((DefaultMutableTreeNode)node.getChildAt(i), orderBuilder);
    }
    orderBuilder.append(" -");
  }

  private static BottomUpNode findBottomUpNodeOnPath(@NotNull BottomUpNode node, String ...path) {
    for (int pathIndex = 1; pathIndex < path.length; ++pathIndex) {
      boolean found = false;
      for (BottomUpNode child: node.getChildren()) {
        if (child.getId().equals(path[pathIndex])) {
          node = child;
          found = true;
          break;
        }
      }
      assertTrue(found);
    }
    return node;
  }

  private static DefaultMutableTreeNode findNodeOnPath(@NotNull DefaultMutableTreeNode node, String... path) {
    for (int pathIndex = 1; pathIndex < path.length; ++pathIndex) {
      boolean found = false;
      for (int i = 0; i < node.getChildCount(); ++i) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
        if (((BottomUpNode)child.getUserObject()).getId().equals(path[pathIndex])) {
          node = child;
          found = true;
          break;
        }
      }
      assertTrue(found);
    }
    return node;
  }
}
