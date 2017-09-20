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
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TopDownNodeTest {

  @Test
  public void testTreeMerger() throws Exception {
    CaptureNode root = createTree();

    // Once merged for top down view, the tree should become:
    // A
    // +- B
    // |  +-D
    // |  +-E
    // |  +-G
    // +- C
    //    +-F

    TopDownNode topDown = new TopDownNode(root);
    assertEquals("A", topDown.getId());
    assertEquals(2, topDown.getChildren().size());
    assertEquals("B", topDown.getChildren().get(0).getId());
    assertEquals(3, topDown.getChildren().get(0).getChildren().size());
    assertEquals("D", topDown.getChildren().get(0).getChildren().get(0).getId());
    assertEquals("E", topDown.getChildren().get(0).getChildren().get(1).getId());
    assertEquals("G", topDown.getChildren().get(0).getChildren().get(2).getId());
    assertEquals("C", topDown.getChildren().get(1).getId());
    assertEquals(1, topDown.getChildren().get(1).getChildren().size());
    assertEquals("F", topDown.getChildren().get(1).getChildren().get(0).getId());
  }

  @Test
  public void testTreeMergeWithFilter() {
    CaptureNode root = createTree();
    // Once merged for top down view, the tree should become:
    //  A
    //   +- B
    //   |  +-D
    //   |  +-E
    //   +- C
    //   |  +-F
    //   +- B (unmatch)
    //      +-E (unmatch)
    //      +-G (unmatch)

    // set node "A->B" unmatch.
    root.getChildren().get(2).setFilterType(CaptureNode.FilterType.UNMATCH);
    // set node "A->B->E" and "A->B->G" unmatch.
    root.getChildren().get(2).getChildren().forEach(n -> n.setFilterType(CaptureNode.FilterType.UNMATCH));

    TopDownNode topDown = new TopDownNode(root);

    // A
    assertEquals("A", topDown.getId());
    checkChildrenIds(topDown, "B", "C", "B");
    checkChildrenUnmatchStatus(topDown, false, false, true);

    // A -> B
    checkChildrenIds(topDown.getChildren().get(0), "D", "E");
    checkChildrenUnmatchStatus(topDown.getChildren().get(0), false, false);
    // A -> C
    checkChildrenIds(topDown.getChildren().get(1), "F");
    checkChildrenUnmatchStatus(topDown.getChildren().get(1), false);
    // A -> B (unmatch)
    checkChildrenIds(topDown.getChildren().get(2), "E", "G");
    checkChildrenUnmatchStatus(topDown.getChildren().get(2), true, true);
  }

  @Test
  public void testTreeTime() {
    CaptureNode root = newNode("A", 0, 10);
    root.addChild(newNode("D", 3, 5));
    root.addChild(newNode("E", 7, 9));

    TopDownNode topDown = new TopDownNode(root);
    topDown.update(new Range(root.getStart(), root.getEnd()));
    for (TopDownNode child : topDown.getChildren()) {
      child.update(new Range(root.getStart(), root.getEnd()));
    }

    assertEquals(10, topDown.getTotal(), 0);
    assertEquals(6, topDown.getSelf(), 0);

    topDown.reset();
    assertEquals(0, topDown.getTotal(), 0);
  }

  @Test
  public void testTreeData() {
    MethodModel rootModel = new MethodModel("A", "com.package", "", ".");
    TopDownNode topDown = new TopDownNode(newNode(rootModel, 0, 10));

    assertEquals("com.package", topDown.getClassName());
    assertEquals("A", topDown.getMethodName());

    // Cover the case of null data
    topDown = new TopDownNode(new CaptureNode());
    assertEquals("", topDown.getClassName());
    assertEquals("", topDown.getMethodName());
  }

  /**
   * Creates a test to be used for testing. The shape of the tree is as follows:
   *              0123456789012345678901234567890
   *   A          |-----------------------------|
   *   +- B        |-------|
   *   |  +-D        |-|
   *   |  +-E            |-|
   *   +- C                    |-----|
   *   |  +-F                  |-|
   *   +- B                             |------|
   *      +-E                           |--|
   *      +-G                              |---|
   */
  @NotNull
  static CaptureNode createTree() {
    CaptureNode root = newNode("A", 0, 30);

    CaptureNode node = newNode("B", 1, 9);
    node.addChild(newNode("D", 3, 5));
    node.addChild(newNode("E", 7, 9));
    root.addChild(node);

    node = newNode("C", 13, 19);
    node.addChild(newNode("F", 13, 15));
    root.addChild(node);

    node = newNode("B", 22, 29);
    node.addChild(newNode("E", 22, 25));
    node.addChild(newNode("G", 25, 29));
    root.addChild(node);
    return root;
  }

  static CaptureNode newNode(String method, long start, long end) {
    return newNode(new MethodModel(method), start, end);
  }

  static CaptureNode newNode(MethodModel method, long start, long end) {
    CaptureNode node = new CaptureNode();
    node.setMethodModel(method);
    node.setStartGlobal(start);
    node.setEndGlobal(end);
    return node;
  }

  private static void checkChildrenIds(TopDownNode node, String ...ids) {
    assertEquals(ids.length, node.getChildren().size());
    for (int i = 0; i < ids.length; ++i) {
      assertEquals(ids[i], node.getChildren().get(i).getId());
    }
  }

  private static void checkChildrenUnmatchStatus(TopDownNode node, boolean ...unmatched) {
    assertEquals(unmatched.length, node.getChildren().size());
    for (int i = 0; i < unmatched.length; ++i) {
      assertEquals(unmatched[i], node.getChildren().get(i).isUnmatched());
    }
  }
}
