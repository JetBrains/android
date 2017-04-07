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

import com.android.tools.adtui.model.HNode;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

public class TopDownNodeTest {

  @Test
  public void testTreeMerger() throws Exception {
    HNode<MethodModel> root = createTree();

    // Once merged for top down view, the tree should become:
    // A
    // +- B
    // |  +-D
    // |  +-E
    // |  +-G
    // +- C
    //    +-F

    TopDownNode topDown = new TopDownNode(root);
    Assert.assertEquals(":A:", topDown.getId());
    Assert.assertEquals(2, topDown.getChildren().size());
    Assert.assertEquals(":B:", topDown.getChildren().get(0).getId());
    Assert.assertEquals(3, topDown.getChildren().get(0).getChildren().size());
    Assert.assertEquals(":D:", topDown.getChildren().get(0).getChildren().get(0).getId());
    Assert.assertEquals(":E:", topDown.getChildren().get(0).getChildren().get(1).getId());
    Assert.assertEquals(":G:", topDown.getChildren().get(0).getChildren().get(2).getId());
    Assert.assertEquals(":C:", topDown.getChildren().get(1).getId());
    Assert.assertEquals(1, topDown.getChildren().get(1).getChildren().size());
    Assert.assertEquals(":F:", topDown.getChildren().get(1).getChildren().get(0).getId());
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
  static HNode<MethodModel> createTree() {
    HNode<MethodModel> root = new HNode<>(new MethodModel("A"), 0, 30);

    HNode<MethodModel> node = new HNode<>(new MethodModel("B"), 1, 9);
    node.addHNode(new HNode<>(new MethodModel("D"), 3, 5));
    node.addHNode(new HNode<>(new MethodModel("E"), 7, 9));
    root.addHNode(node);

    node = new HNode<>(new MethodModel("C"), 13, 19);
    node.addHNode(new HNode<>(new MethodModel("F"), 13, 15));
    root.addHNode(node);

    node = new HNode<>(new MethodModel("B"), 22, 29);
    node.addHNode(new HNode<>(new MethodModel("E"), 22, 25));
    node.addHNode(new HNode<>(new MethodModel("G"), 25, 29));
    root.addHNode(node);
    return root;
  }
}
