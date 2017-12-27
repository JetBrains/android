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
package com.android.tools.adtui.chart.statechart;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class DefaultStateChartReducerTest {
  private enum ColorState {
    YELLOW,
    RED,
    BLACK
  }

  private final StateChartReducer<ColorState> myReducer = new DefaultStateChartReducer<>();

  @Test
  public void testReduce() {
    float[][] given = {{0, 0, 5.1f, 10}, {5.1f, 0, 5.3f, 10}, {5.3f, 0, 5.9f, 10}, {5.9f, 0, 6.1f, 10}};
    ColorState[] givenValues = {ColorState.BLACK, ColorState.RED, ColorState.YELLOW, ColorState.BLACK};

    float[][] expected = {{0, 0, 5.1f, 10}, {5.1f, 0, 5.9f, 10}, {5.9f, 0, 6.1f, 10}};
    ColorState[] expectedValues = {ColorState.BLACK, ColorState.YELLOW, ColorState.BLACK};

    check(given, givenValues, expected, expectedValues);
  }

  /**
   * The {@link StateChart} may contain more than one series. See: {@link StateChart#addSeries}.
   * This test ensures that works correct if it contains several series.
   */
  @Test
  public void testReduceSeveralSeries() {
    float[][] given = {{0, 0, 5.1f, 10}, {0, 11, 7, 16}, {0, 17, 8.1f, 20}, {8.1f, 17, 8.9f, 20}};
    ColorState[] givenValues = {ColorState.BLACK, ColorState.RED, ColorState.YELLOW, ColorState.BLACK};

    float[][] expected = {{0, 0, 5.1f, 10}, {0, 11, 7, 16}, {0, 17, 8.1f, 20}, {8.1f, 17, 8.9f, 20}};
    ColorState[] expectedValues = {ColorState.BLACK, ColorState.RED, ColorState.YELLOW, ColorState.BLACK};

    check(given, givenValues, expected, expectedValues);
  }

  @Test
  public void testReduceMostOccurred() {
    float[][] given = {{0, 0, 1.1f, 10}, {1.1f, 0, 1.2f, 10}, {1.2f, 0, 1.6f, 10}, {1.6f, 0, 1.91f, 10}};
    ColorState[] givenValues = {ColorState.BLACK, ColorState.RED, ColorState.YELLOW, ColorState.RED};

    float[][] expected = {{0, 0, 1.1f, 10}, {1.1f, 0, 1.91f, 10}};
    ColorState[] expectedValues = {ColorState.BLACK, ColorState.RED};

    check(given, givenValues, expected, expectedValues);
  }

  /**
   * Tests {@link DefaultStateChartReducer} whether it returns {@code expected} rectangles and {@code expectValues} values,
   * if it is arguments are {@code given} as rectangles and {@code givenValues} as values.
   * @param given given in format {xMin, yMin, xMax, yMax}.
   * @param expected given in format {xMin, yMin, xMax, yMax}.
   */
  private void check(float[][] given, ColorState[] givenValues, float[][] expected, ColorState[] expectedValues) {
    assert given.length == givenValues.length;
    assert expected.length == expectedValues.length;

    List<Shape> rectangles = convertToRectangles(given);
    List<ColorState> values = new ArrayList<>(Arrays.asList(givenValues));
    myReducer.reduce(rectangles, values);

    assertRectanglesEquals(convertToRectangles(expected), rectangles);
    assertThat(values.toArray(new ColorState[0])).isEqualTo(expectedValues);
  }

  @NotNull
  private static List<Shape> convertToRectangles(@NotNull float[][] coordinates) {
    List<Shape> results = new ArrayList<>();

    for (float[] rect: coordinates) {
      assert rect.length == 4;
      float xMin = rect[0], yMin = rect[1];
      float xMax = rect[2], yMax = rect[3];
      results.add(new RoundRectangle2D.Float(xMin, yMin, xMax - xMin, yMax - yMin, 0, 0));
    }
    return results;
  }

  private static void assertRectanglesEquals(@NotNull List<Shape> expected, @NotNull List<Shape> actual) {
    assertThat(actual.size()).isEqualTo(expected.size());

    for (int i = 0; i < expected.size(); ++i) {
      Rectangle2D expectedRect = expected.get(i).getBounds2D();
      Rectangle2D actualRect = actual.get(i).getBounds2D();
      assertThat(expectedRect).isEqualTo(actualRect);
    }
  }
}


