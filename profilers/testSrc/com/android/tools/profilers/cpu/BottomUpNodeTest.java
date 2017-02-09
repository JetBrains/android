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

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BottomUpNodeTest {
  private final static double EPS = 1e-5;

  private final List<ExpectedNode> myExpectedNodes = Arrays.asList(
    new ExpectedNode("Root", 40.0, 30.0),
    new ExpectedNode(":main", 40.0, 30.0),
    new ExpectedNode(":A", 20.0, 10.0),
    new ExpectedNode(":main", 15.0, 5.0),
    new ExpectedNode(":C", 5.0, 5.0),
    new ExpectedNode(":main", 5.0, 5.0),
    new ExpectedNode(":B", 15.0, 0.0),
    new ExpectedNode(":A", 10.0, 0.0),
    new ExpectedNode(":main", 5.0, 0.0),
    new ExpectedNode(":C", 5.0, 0.0),
    new ExpectedNode(":main", 5.0, 0.0),
    new ExpectedNode(":main", 5.0, 0.0),
    new ExpectedNode(":C", 10.0, 5.0),
    new ExpectedNode(":main", 10.0, 5.0)
  );

  private List<BottomUpNode> myTraverseOrder;

  @Before
  public void setUp() {
    myTraverseOrder = new ArrayList<>();
  }

  @Test
  public void testBottomUpNode() {
    Range viewRange = new Range(0, 40);
    BottomUpNode bottomUpNode = new BottomUpNode(createTree());
    traverse(bottomUpNode);

    assertEquals(myExpectedNodes.size(), myTraverseOrder.size());
    for (int i = 0; i < myExpectedNodes.size(); ++i) {
      BottomUpNode node = myTraverseOrder.get(i);
      node.update(viewRange);
      assertEquals(myExpectedNodes.get(i).myId, node.getId());
      assertEquals(myExpectedNodes.get(i).myTotal, node.getTotal(), EPS);
      assertEquals(myExpectedNodes.get(i).myChildrenTotal, node.getChildrenTotal(), EPS);
    }
  }

  private void traverse(BottomUpNode node) {
    node.buildChildren();
    myTraverseOrder.add(node);
    for (BottomUpNode child : node.getChildren()) {
      traverse(child);
    }
  }

  @NotNull
  public static HNode<MethodModel> createTree() {
    HNode<MethodModel> root = new HNode<>(new MethodModel("main"), 0, 40);
    HNode<MethodModel> childA = new HNode<>(new MethodModel("A"), 0, 15);
    HNode<MethodModel> childC = new HNode<>(new MethodModel("C"), 20, 30);
    HNode<MethodModel> childB = new HNode<>(new MethodModel("B"), 35, 40);

    root.addHNode(childA);
    root.addHNode(childC);
    root.addHNode(childB);
    childA.addHNode(new HNode<>(new MethodModel("B"), 5, 10));
    childC.addHNode(new HNode<>(new MethodModel("A"), 20, 25));
    childC.getChildren().get(0).addHNode(new HNode<>(new MethodModel("B"), 20, 25));
    return root;
  }

  private static class ExpectedNode {
    private String myId;
    private double myTotal;
    private double myChildrenTotal;

    private ExpectedNode(String id, double total, double childrenTotal) {
      myId = id;
      myTotal = total;
      myChildrenTotal = childrenTotal;
    }
  }
}