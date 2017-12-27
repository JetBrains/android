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

import static com.google.common.truth.Truth.assertThat;

public class DefaultHNodeTest {

  @Test
  public void testSingleNode() throws Exception {
    DefaultHNode<String> root = new DefaultHNode<>("Root", 0, 30);
    assertThat(root.getChildren()).isEmpty();
    assertThat(root.getFirstChild()).isNull();
    assertThat(root.getLastChild()).isNull();
    assertThat(root.getStart()).isEqualTo(0);
    assertThat(root.getEnd()).isEqualTo(30);
  }

  @Test
  public void testDefaultConstructor() throws Exception {
    DefaultHNode<String> root = new DefaultHNode<>();
    assertThat(root.getData()).isNull();
    assertThat(root.getStart()).isEqualTo(0);
    assertThat(root.getEnd()).isEqualTo(0);
  }

  @Test
  public void testStartEnd() throws Exception {
    DefaultHNode<String> root = new DefaultHNode<>();

    assertThat(root.getStart()).isEqualTo(0);
    root.setStart(5);
    assertThat(root.getStart()).isEqualTo(5);

    assertThat(root.getEnd()).isEqualTo(0);
    root.setEnd(30);
    assertThat(root.getEnd()).isEqualTo(30);

    // Duration should be end - start
    assertThat(root.duration()).isEqualTo(25);
  }

  @Test
  public void testMultiNode() {
    // Creates a tree with the following shape:
    //        A
    //    B --+-- C
    //          D-+-E
    DefaultHNode<String> nodeA = new DefaultHNode<>();
    DefaultHNode<String> nodeB = new DefaultHNode<>();
    DefaultHNode<String> nodeC = new DefaultHNode<>();

    nodeA.addChild(nodeB);
    nodeA.addChild(nodeC);
    assertThat(nodeA.getChildren()).hasSize(2);

    DefaultHNode<String> nodeD = new DefaultHNode<>();
    DefaultHNode<String> nodeE = new DefaultHNode<>();

    nodeC.addChild(nodeD);
    nodeC.addChild(nodeE);
    assertThat(nodeC.getChildren()).hasSize(2);

    assertThat(nodeC).isSameAs(nodeA.getLastChild());
    assertThat(nodeA.getLastChild().getChildCount()).isEqualTo(2);
    assertThat(nodeD).isSameAs(nodeA.getLastChild().getFirstChild());
  }

  @Test
  public void testData() {
    DefaultHNode<String> root = new DefaultHNode<>();
    assertThat(root.getData()).isNull();
    root.setData("Root");
    assertThat(root.getData()).isEqualTo("Root");
  }

  @Test
  public void testDepth() {
    DefaultHNode<String> root = new DefaultHNode<>();
    root.setDepth(3);
    assertThat(root.getDepth()).isEqualTo(3);
    root.setDepth(5);
    assertThat(root.getDepth()).isEqualTo(5);
  }
}
