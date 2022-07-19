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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.Utils;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.intellij.testFramework.ApplicationRule;
import java.util.Arrays;
import java.util.Collections;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

public class FlameChartTest {

  @ClassRule
  public static final ApplicationRule rule = new ApplicationRule();

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
    CaptureNode main = newNode("main", 0, 71);
    main.addChild(newNode("A", 0, 20));
    main.addChild(newNode("B", 21, 30));
    main.addChild(newNode("A", 35, 40));
    main.addChild(newNode("C", 45, 71));
    main.getChildren().get(1).addChild(newNode("C", 21, 25));
    main.getChildren().get(1).addChild(newNode("C", 25, 30));

    Range selection = new Range(0, 71);
    CpuCapture capture = Mockito.mock(CpuCapture.class);
    Mockito.when(capture.getRange()).thenReturn(selection);

    CaptureDetails.FlameChart flameChart = new CaptureDetails.FlameChart(ClockType.GLOBAL, selection,
                                                                         Collections.singletonList(main), capture,
                                                                         this::runOnUi);
    CaptureNode flameChartNode = flameChart.getNode();
    main = flameChartNode.getFirstChild();
    // main [0..71]
    assertEquals(0, main.getStart());
    assertEquals(71, main.getEnd());
    assertEquals("main", main.getData().getName());
    // C [0...26]
    CaptureNode nodeC = main.getChildAt(0);
    assertEquals(0, nodeC.getStart());
    assertEquals(26, nodeC.getEnd());
    assertEquals("C", nodeC.getData().getName());
    // A [26..51]
    CaptureNode nodeA = main.getChildAt(1);
    assertEquals(26, nodeA.getStart());
    assertEquals(51, nodeA.getEnd());
    assertEquals("A", nodeA.getData().getName());
    // B [51..60]
    CaptureNode nodeB = main.getChildAt(2);
    assertEquals(51, nodeB.getStart());
    assertEquals(60, nodeB.getEnd());
    assertEquals("B", nodeB.getData().getName());

    // B -> C [51..60]
    assertEquals(51, nodeB.getChildAt(0).getStart());
    assertEquals(60, nodeB.getChildAt(0).getEnd());
    assertEquals("C", nodeB.getChildAt(0).getData().getName());
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
    CaptureNode main = newNode("main", 0, 60);
    main.addChild(newNode("A", 0, 10));
    main.addChild(newNode("B", 10, 30));
    main.addChild(newNode("C", 30, 50));
    main.addChild(newNode("A", 50, 60));

    Range selection = new Range(0, 60);
    CpuCapture capture = Mockito.mock(CpuCapture.class);
    Mockito.when(capture.getRange()).thenReturn(selection);

    CaptureDetails.FlameChart flameChart = new CaptureDetails.FlameChart(ClockType.GLOBAL, selection,
                                                                         Collections.singletonList(main), capture,
                                                                         this::runOnUi);
    CaptureNode flameChartNode = flameChart.getNode();

    main = flameChartNode.getFirstChild();
    assertEquals(0, main.getStart());
    assertEquals(60, main.getEnd());
    assertEquals("main", main.getData().getName());

    assertEquals(0, main.getChildAt(0).getStart());
    assertEquals(20, main.getChildAt(0).getEnd());
    assertEquals("A", main.getChildAt(0).getData().getName());

    assertEquals(20, main.getChildAt(1).getStart());
    assertEquals(40, main.getChildAt(1).getEnd());
    assertEquals("B", main.getChildAt(1).getData().getName());

    assertEquals(40, main.getChildAt(2).getStart());
    assertEquals(60, main.getChildAt(2).getEnd());
    assertEquals("C", main.getChildAt(2).getData().getName());
  }

  @Test
  public void changingTheSelectionTheNodeShouldBeRecalculated() {
    CaptureNode main = newNode("main", 0, 100);
    main.addChild(newNode("A", 0, 10));
    main.addChild(newNode("B", 20, 25));
    main.addChild(newNode("C", 50, 100));

    Range selection = new Range(0, 100);
    CpuCapture capture = Mockito.mock(CpuCapture.class);
    Mockito.when(capture.getRange()).thenReturn(selection);

    CaptureDetails.FlameChart flameChart = new CaptureDetails.FlameChart(ClockType.GLOBAL, selection,
                                                                         Collections.singletonList(main), capture,
                                                                         this::runOnUi);

    CaptureNode root = flameChart.getNode();
    assertEquals(100, root.getDuration());

    AspectObserver observer = new AspectObserver();

    Boolean[] nodeChanged = new Boolean[]{false};
    flameChart.getAspect().addDependency(observer).onChange(CaptureDetails.FlameChart.Aspect.NODE, () -> nodeChanged[0] = true);

    // Range 19..25
    nodeChanged[0] = false;
    selection.set(19, 25);
    assertTrue(nodeChanged[0]);

    root = flameChart.getNode();
    assertEquals(19, root.getStart());
    assertEquals(25, root.getEnd());
    assertEquals(1, root.getChildCount());

    // main 19..25
    main = root.getFirstChild();
    assertEquals("main", main.getData().getFullName());
    assertEquals(19, main.getStart());
    assertEquals(25, main.getEnd());
    assertEquals(1, main.getChildCount());
    // should "B" 19..24

    assertEquals("B", main.getFirstChild().getData().getFullName());
    assertEquals(19, main.getFirstChild().getStart());
    assertEquals(24, main.getFirstChild().getEnd());

    // Range 9..60
    nodeChanged[0] = false;
    selection.set(9, 60);
    assertTrue(nodeChanged[0]);

    root = flameChart.getNode();
    main = root.getFirstChild();
    // main 9..60
    assertEquals(9, main.getStart());
    assertEquals(60, main.getEnd());
    assertEquals(3, main.getChildCount());
    // C - 9..19
    assertEquals("C", main.getFirstChild().getData().getFullName());
    assertEquals(9, main.getFirstChild().getStart());
    assertEquals(19, main.getFirstChild().getEnd());
    // B - 19..24
    assertEquals("B", main.getChildAt(1).getData().getFullName());
    assertEquals(19, main.getChildAt(1).getStart());
    assertEquals(24, main.getChildAt(1).getEnd());
    // A - 24..25
    assertEquals("A", main.getChildAt(2).getData().getFullName());
    assertEquals(24, main.getChildAt(2).getStart());
    assertEquals(25, main.getChildAt(2).getEnd());
  }

  @Test
  public void selectionOutsideChildrenRange() {
    CaptureNode thread1 = newNode("thread1", 0, 100);
    CaptureNode thread2 = newNode("thread2", 0, 100);

    Range selection = new Range(0, 100);
    CpuCapture capture = Mockito.mock(CpuCapture.class);
    Mockito.when(capture.getRange()).thenReturn(selection);

    // Create a multi-node flame chart.
    CaptureDetails.FlameChart flameChart = new CaptureDetails.FlameChart(ClockType.GLOBAL, selection,
                                                                         Arrays.asList(thread1, thread2), capture,
                                                                         this::runOnUi);

    // Selecting outside all of the children's range should effectively return no intersection for the flame chart.
    selection.set(110, 120);
    assertNull(flameChart.getNode());
  }

  @NotNull
  private static CaptureNode newNode(String method, long start, long end) {
    CaptureNode node = new CaptureNode(new SingleNameModel(method));
    node.setStartGlobal(start);
    node.setEndGlobal(end);

    node.setStartThread(start);
    node.setEndThread(end);
    return node;
  }

  private Unit runOnUi(Runnable work) {
    Utils.runOnUi(work);
    return Unit.INSTANCE;
  }
}