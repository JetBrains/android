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
package com.android.tools.adtui.chart.hchart;

import com.android.tools.adtui.model.DefaultHNode;
import com.android.tools.adtui.model.HNode;
import org.junit.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DefaultHTreeChartReducerTest {
  private static final int FAKE_HEIGHT = 5;
  private static float EPS = 1e-6f;

  /**
   * The structure of the tree:
   *   0 0.5 0.7 0.9 1 2 2.1 2.2 2.4 3 4 5 6 7 7.9 8 9 10
   *   A+++++++++++++++++++++++++++++++++++++++++++++++++
   *   B++++++++++++++++++++++++++++++++++ C+++++++++++++
   *   D++++ E++++++     F+++++G++++           H++++
   */
  @Test
  public void testReducer() {
    List<Rectangle2D.Float> expectedRectangles = Arrays.asList(
      new Rectangle2D.Float(0, 0, 10, 5),
      new Rectangle2D.Float(0, 5, 5, 5),
      new Rectangle2D.Float(6, 5, 4, 5),
      new Rectangle2D.Float(0, 10, 0.9f, 5),
      new Rectangle2D.Float(2.1f, 10, 0.3f, 5),
      new Rectangle2D.Float(7.9f, 10, 0.1f, 5)
    );
    List<String> expectedNodes = Arrays.asList("A", "B", "C", "D", "F", "H");

    HTreeChartReducer<String> reducer = new DefaultHTreeChartReducer<>();
    List<HNode<String>> nodes = new ArrayList<>();
    List<Rectangle2D.Float> rectangles = new ArrayList<>();

    addNode("A", 0, 10, 0, nodes, rectangles);

    addNode("B", 0, 5, 1, nodes, rectangles);
    addNode("C", 6, 10, 1, nodes, rectangles);

    addNode("D", 0, 0.5f, 2, nodes, rectangles);
    addNode("E", 0.7f, 0.9f, 2, nodes, rectangles);
    addNode("F", 2.1f, 2.2f, 2, nodes, rectangles);
    addNode("G", 2.2f, 2.4f, 2, nodes, rectangles);
    addNode("H", 7.9f, 8.0f, 2, nodes, rectangles);

    reducer.reduce(rectangles, nodes);

    checkRectanglesEqual(expectedRectangles, rectangles);

    assertEquals(expectedNodes.size(), nodes.size());
    for (int i = 0; i < nodes.size(); ++i) {
      assertEquals(expectedNodes.get(i), nodes.get(i).getData());
    }
  }

  private static void addNode(String id, float minX, float maxX, int depth,
                       List<HNode<String>> nodes,
                       List<Rectangle2D.Float> rectangles) {

    // node.getStart() and node.getEnd() needs to be proportional to X coordinates of the corresponding rectangle.
    // So as a factor of it taken 1000 to get rid of the fractional part of a number.
    DefaultHNode<String> node = new DefaultHNode<>(id, (long)(1000 * minX), (long)(1000 * maxX));
    node.setDepth(depth);

    nodes.add(node);
    rectangles.add(new Rectangle2D.Float(minX, FAKE_HEIGHT * depth, maxX - minX, FAKE_HEIGHT));
  }

  private static void checkRectanglesEqual(List<Rectangle2D.Float> expected, List<Rectangle2D.Float> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); ++i) {
      assertEquals(expected.get(i).getX(), actual.get(i).getX(), EPS);
      assertEquals(expected.get(i).getY(), actual.get(i).getY(), EPS);
      assertEquals(expected.get(i).getWidth(), actual.get(i).getWidth(), EPS);
      assertEquals(expected.get(i).getHeight(), actual.get(i).getHeight(), EPS);
    }
  }
}