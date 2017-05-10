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
import org.junit.Test;

import static org.junit.Assert.*;

public class FlameChartTest {

  /**
   * main [0..71]
   *   -> A [0..20]
   *   -> B [21..30]
   *     -> C [21..25]
   *     -> C [25..30]
   *   -> A [35..40]
   *   -> C [45..71]
   *
   * And the flame chart tree should look like:
   * main [0..71]
   *   -> C [0..26]
   *   -> A [26..51]
   *   -> B [51..60]
    *   -> C [51..60]
   */
  @Test
  public void testFlameChart() {
    HNode<MethodModel> main = new HNode<>(new MethodModel("main"), 0, 71);
    main.addHNode(new HNode<>(new MethodModel("A"), 0, 20));
    main.addHNode(new HNode<>(new MethodModel("B"), 21, 30));
    main.addHNode(new HNode<>(new MethodModel("A"), 35, 40));
    main.addHNode(new HNode<>(new MethodModel("C"), 45, 71));
    main.getChildren().get(1).addHNode(new HNode<>(new MethodModel("C"), 21, 25));
    main.getChildren().get(1).addHNode(new HNode<>(new MethodModel("C"), 25, 30));

    HNode<MethodModel> flameChartNode = new CaptureModel.FlameChart(new Range(0, 70), main).getNode();
    // main [0..71]
    assertEquals(0, flameChartNode.getStart());
    assertEquals(71, flameChartNode.getEnd());
    assertEquals("main", flameChartNode.getData().getName());
    // C [0...26]
    HNode<MethodModel> nodeC = flameChartNode.getFirstChild();
    assertEquals(0, nodeC.getStart());
    assertEquals(26, nodeC.getEnd());
    assertEquals("C", nodeC.getData().getName());
    // A [26..51]
    HNode<MethodModel> nodeA = flameChartNode.getChildren().get(1);
    assertEquals(26, nodeA.getStart());
    assertEquals(51, nodeA.getEnd());
    assertEquals("A", nodeA.getData().getName());
    // B [51..60]
    HNode<MethodModel> nodeB = flameChartNode.getChildren().get(2);
    assertEquals(51, nodeB.getStart());
    assertEquals(60, nodeB.getEnd());
    assertEquals("B", nodeB.getData().getName());

    // B -> C [51..60]
    assertEquals(51, nodeB.getFirstChild().getStart());
    assertEquals(60, nodeB.getFirstChild().getEnd());
    assertEquals("C", nodeB.getFirstChild().getData().getName());
  }

  /**
   * The input:
   * main -> [0..60]
   *   A -> [0..10]
   *   B -> [10..30]
   *   C -> [30..50]
   *   A -> [50..60]
   *
   * The output:
   * main -> [0..60]
   *   A -> [0..20]
   *   B -> [20..40]
   *   C -> [40..60]
   *
   */
  @Test
  public void testNodesWithEqualTotal() {
    HNode<MethodModel> main = new HNode<>(new MethodModel("main"), 0, 60);
    main.addHNode(new HNode<>(new MethodModel("A"), 0, 10));
    main.addHNode(new HNode<>(new MethodModel("B"), 10, 30));
    main.addHNode(new HNode<>(new MethodModel("C"), 30, 50));
    main.addHNode(new HNode<>(new MethodModel("A"), 50, 60));

    HNode<MethodModel> flameChartNode = new CaptureModel.FlameChart(new Range(0, 60), main).getNode();
    assertEquals(0, flameChartNode.getStart());
    assertEquals(60, flameChartNode.getEnd());
    assertEquals("main", flameChartNode.getData().getName());

    assertEquals(0, flameChartNode.getChildren().get(0).getStart());
    assertEquals(20, flameChartNode.getChildren().get(0).getEnd());
    assertEquals("A", flameChartNode.getChildren().get(0).getData().getName());

    assertEquals(20, flameChartNode.getChildren().get(1).getStart());
    assertEquals(40, flameChartNode.getChildren().get(1).getEnd());
    assertEquals("B", flameChartNode.getChildren().get(1).getData().getName());

    assertEquals(40, flameChartNode.getChildren().get(2).getStart());
    assertEquals(60, flameChartNode.getChildren().get(2).getEnd());
    assertEquals("C", flameChartNode.getChildren().get(2).getData().getName());
  }
}