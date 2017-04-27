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
   * main [0..70]
   *   -> A [0..20]
   *   -> B [21..30]
   *     -> C [21..25]
   *     -> C [25..30]
   *   -> A [35..40]
   *   -> C [45..70]
   *
   * And the flame chart tree should look like:
   * main [0..70]
   *   -> A [0..25]
   *   -> B [25..34]
   *     -> C [25..34]
   *   -> C [34..59]
   */
  @Test
  public void testFlameChart() {
    HNode<MethodModel> main = new HNode<>(new MethodModel("main"), 0, 70);
    main.addHNode(new HNode<>(new MethodModel("A"), 0, 20));
    main.addHNode(new HNode<>(new MethodModel("B"), 21, 30));
    main.addHNode(new HNode<>(new MethodModel("A"), 35, 40));
    main.addHNode(new HNode<>(new MethodModel("C"), 45, 70));
    main.getChildren().get(1).addHNode(new HNode<>(new MethodModel("C"), 21, 25));
    main.getChildren().get(1).addHNode(new HNode<>(new MethodModel("C"), 25, 30));

    HNode<MethodModel> flameChartNode = new CaptureModel.FlameChart(new Range(0, 10), main).getNode();
    // main [0..70]
    assertEquals(0, flameChartNode.getStart());
    assertEquals(70, flameChartNode.getEnd());
    assertEquals("main", flameChartNode.getData().getName());
    // A [0...25]
    HNode<MethodModel> nodeA = flameChartNode.getFirstChild();
    assertEquals(0, nodeA.getStart());
    assertEquals(25, nodeA.getEnd());
    assertEquals("A", nodeA.getData().getName());
    // B [25..34]
    HNode<MethodModel> nodeB = flameChartNode.getChildren().get(1);
    assertEquals(25, nodeB.getStart());
    assertEquals(34, nodeB.getEnd());
    assertEquals("B", nodeB.getData().getName());
    // C [25..34]
    HNode<MethodModel> nodeC = nodeB.getFirstChild();
    assertEquals(25, nodeC.getStart());
    assertEquals(34, nodeC.getEnd());
    assertEquals("C", nodeC.getData().getName());

    // C [34..59]
    assertEquals(34, flameChartNode.getChildren().get(2).getStart());
    assertEquals(59, flameChartNode.getChildren().get(2).getEnd());
    assertEquals("C", flameChartNode.getChildren().get(2).getData().getName());
  }
}