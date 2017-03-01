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
package com.android.tools.adtui.chart.linechart;

import org.junit.Test;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;

public class DefaultLineChartReducerTest {
  private static final float EPS = 1e-6f;
  // DefaultLineChartReducer reduces Path2D, so it means that points are in screen coordinates.
  // FAKE_HEIGHT is needed to convert from screen coordinates to cartesian coordinates.
  private static int FAKE_HEIGHT = 100;
  private final DefaultLineChartReducer myReducer = new DefaultLineChartReducer();
  private LineConfig config = new LineConfig(Color.RED);

  @Test
  public void testReduce() {
    float[][] given = {{0, 0}, {0.1f, 1}, {0.2f, 6}, {0.3f, 4}, {1, 2}, {1.1f, 5}};
    float[][] expected = {{0, 0}, {0.2f, 6}, {0.3f, 4}, {1, 2}, {1.1f, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testNegativeCoordinate() {
    float[][] given = {{1, 5}, {2f, -1f}, {2f, 0}, {1, 0}};
    float[][] expected = {{1, 5}, {2f, -1}, {2, 0}, {1, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);
    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceOnePointPerPixel() {
    float[][] given = {{1.2f, 2}, {2, 0}, {3, 5}};
    float[][] expected = {{1.2f, 2}, {2, 0}, {3, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceNoPointInPixel() {
    float[][] given = {{6, 0}, {7, 5}};
    float[][] expected = {{6, 0}, {7, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceWithSameY() {
    float[][] given = {{1, 4}, {1.2f, 4}, {1.3f, 4}, {1.5f, 4}, {1.6f, 4}, {1.7f, 4}};
    float[][] expected = {{1, 4}, {1.7f, 4}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceFirstAndLastPointsRemains() {
    float[][] given = {{1, 4}, {1.2f, 6}, {1.3f, 0}, {1.4f, 2}, {1.5f, 0}, {1.7f, 4}};
    float[][] expected = {{1, 4}, {1.2f, 6}, {1.5f, 0}, {1.7f, 4}};

    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceClosedPolyLine() {
    float[][] given = {{0, 1}, {0.5f, 1}, {1, 3}, {1.5f, 4}, {2, 3}, {2, 0}, {0, 0}};
    float[][] expected = {{0, 1}, {0.5f, 1}, {1, 3}, {1.5f, 4.0f}, {2, 3}, {2, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testClosedPolylineLastPointsRemains() {
    float[][] given = {{0, 4}, {0.5f, 5}, {1.1f, 0}, {1.2f, 0}, {1.3f, 5}, {1.4f, 5}, {1.9f, 5}, {1.9f, 0}, {0, 0}};
    float[][] expected = {{0, 4}, {0.5f, 5}, {1.1f, 0}, {1.3f, 5}, /*should peek up right most minimum*/ {1.9f, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceSteppedLine() {
    float[][] given = {{0, 5}, {0.1f, 5}, {0.1f, 4}, {0.2f, 4}, {0.2f, 6}, {0.3f, 6}, {0.3f, 4.1f}, {0.5f, 4.1f}, {0.5f, 4.9f}, {3, 4.9f}, {3, 4.8f}};
    float[][] expected = {{0, 5}, {0.2f, 5},  {0.2f, 4}, {0.2f, 6}, {0.5f, 6}, {0.5f, 4.9f}, {3, 4.9f}, {3, 4.8f}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);
    config.setStepped(true);
    float[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  private static void convertToScreenCoordinates(float[][] points) {
    for (int i = 0; i < points.length; ++i) {
      points[i][1] = FAKE_HEIGHT - points[i][1];
    }
  }

  private static void assertPointsEquals(float[][] expected, float[][] actual) {
    assertThat(actual.length).isEqualTo(expected.length);

    for (int i = 0; i < expected.length; ++i) {
      assertThat(actual[i].length).isEqualTo(2);
      assertThat(expected[i].length).isEqualTo(2);

      assertThat(actual[i][0]).isWithin(EPS).of(expected[i][0]);
      assertThat(actual[i][1]).isWithin(EPS).of(expected[i][1]);
    }
  }

  private static Path2D convertToPath(float[][] points) {
    Path2D.Float resultPath = new Path2D.Float();
    resultPath.moveTo(points[0][0], points[0][1]);
    for (int i = 1; i < points.length; ++i) {
      assert points[i].length == 2;
      resultPath.lineTo(points[i][0], points[i][1]);
    }
    return resultPath;
  }

  private static float[][] convertToArray(Path2D path) {
    PathIterator pathIterator = path.getPathIterator(null);
    float[] coords = new float[6];

    ArrayList<Point2D.Float> points = new ArrayList<>();
    while (!pathIterator.isDone()) {
      int type = pathIterator.currentSegment(coords);
      assert type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO;
      points.add(new Point2D.Float(coords[0], coords[1]));
      pathIterator.next();
    }
    float[][] result = new float[points.size()][2];
    for (int i = 0; i < points.size(); ++i) {
      result[i][0] = points.get(i).x;
      result[i][1] = points.get(i).y;
    }
    return result;
  }
}