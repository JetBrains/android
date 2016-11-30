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
package com.android.tools.adtui.model;


import org.junit.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class HNodeTest {

  @Test
  public void testSingleNode() throws Exception {
    HNode<String> root = new HNode<>("Root", 0, 30);
    assertTrue(root.getChildren().isEmpty());
    assertNull(root.getFirstChild());
    assertNull(root.getLastChild());
    assertEquals(0, root.getStart());
    assertEquals(30, root.getEnd());
  }

  @Test
  public void testDefaultConstructor() throws Exception {
    HNode<String> root = new HNode<>();
    assertNull(root.getData());
    assertEquals(0, root.getStart());
    assertEquals(0, root.getEnd());
  }

  @Test
  public void testStartEnd() throws Exception {
    HNode<String> root = new HNode<>();

    assertEquals(0, root.getStart());
    root.setStart(5);
    assertEquals(5, root.getStart());

    assertEquals(0, root.getEnd());
    root.setEnd(30);
    assertEquals(30, root.getEnd());

    // Duration should be end - start
    assertEquals(25, root.duration());
  }

  @Test
  public void testMultiNode() {
    // Creates a tree with the following shape:
    //        A
    //    B --+-- C
    //          D-+-E
    HNode<String> nodeA = new HNode<>();
    HNode<String> nodeB = new HNode<>();
    HNode<String> nodeC = new HNode<>();

    nodeA.addHNode(nodeB);
    nodeA.addHNode(nodeC);
    assertThat(nodeA.getChildren(), hasSize(2));

    HNode<String> nodeD = new HNode<>();
    HNode<String> nodeE = new HNode<>();

    nodeC.addHNode(nodeD);
    nodeC.addHNode(nodeE);
    assertThat(nodeC.getChildren(), hasSize(2));

    assertSame(nodeC, nodeA.getLastChild());
    assertThat(nodeA.getLastChild().getChildren(), hasSize(2));
    assertSame(nodeD, nodeA.getLastChild().getFirstChild());
  }

  @Test
  public void testData() {
    HNode<String> root = new HNode<>();
    assertNull(root.getData());
    root.setData("Root");
    assertEquals("Root", root.getData());
  }

  @Test
  public void testDepth() {
    HNode<String> root = new HNode<>();
    root.setDepth(3);
    assertEquals(3, root.getDepth());
    root.setDepth(5);
    assertEquals(5, root.getDepth());
  }
}
