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
package com.android.tools.adtui.common.datareducer;

import com.android.tools.adtui.chart.linechart.LineConfig;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;

public class LineChartReducerTest {
  private static final double EPS = 1e-6;
  // LineChartReducer reduces Path2D, so it means that points are in screen coordinates.
  // FAKE_HEIGHT is needed to convert from screen coordinates to cartesian coordinates.
  private static int FAKE_HEIGHT = 100;
  private final LineChartReducer myReducer = new LineChartReducer();
  private LineConfig config = new LineConfig(Color.RED);


  @Test
  public void testReduce() {
    double[][] given = {{0, 0}, {0.1, 1}, {0.2, 6}, {0.3, 4}, {1, 2}, {1.1, 5}};
    double[][] expected = {{0, 0}, {0.1, 1}, {0.2, 6}, {1, 2}, {1.1, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceOnePointPerPixel() {
    double[][] given = {{1.2, 2}, {2, 0}, {3, 5}};
    double[][] expected = {{1.2, 2}, {2, 0}, {3, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceNoPointInPixel() {
    double[][] given = {{6, 0}, {7, 5}};
    double[][] expected = {{6, 0}, {7, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceWithSameY() {
    double[][] given = {{1, 4}, {1.2, 4}, {1.3, 4}, {1.5, 4}, {1.6, 4}, {1.7, 4}};
    double[][] expected = {{1, 4}, {1.2, 4}, {1.7, 4}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceFirstAndLastPointsRemains() {
    double[][] given = {{1, 4}, {1.2, 6}, {1.3, 0}, {1.4, 2}, {1.5, 0}, {1.7, 4}};
    double[][] expected = {{1, 4}, {1.2, 6}, {1.5, 0}, {1.7, 4}};

    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testReduceClosedPolyLine() {
    double[][] given = {{0, 1}, {0.5, 1}, {1, 3}, {1.5, 4}, {2, 3}, {2, 0}, {0, 0}};
    double[][] expected = {{0, 1}, {0.5, 1}, {1, 3}, {1.5, 4.0}, {2, 3}, {2, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  @Test
  public void testClosedPolylineLastPointsRemains() {

    double[][] given = {{0, 4}, {0.5, 5}, {1.1, 0}, {1.2, 0}, {1.3, 5}, {1.4, 5}, {1.9, 5}, {1.9, 0}, {0, 0}};
    double[][] expected = {{0, 4}, {0.5, 5}, {1.3, 5}, /*should peek up right most minimum*/ {1.9, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    double[][] result = convertToArray(myReducer.reduce(convertToPath(given), config));
    assertPointsEquals(expected, result);
  }

  private static void convertToScreenCoordinates(double points[][]) {
    for (int i = 0; i < points.length; ++i) {
      points[i][1] = FAKE_HEIGHT - points[i][1];
    }
  }

  private static void assertPointsEquals(double[][] expected, double[][] actual) {
    assertThat(actual.length).isEqualTo(expected.length);

    for (int i = 0; i < expected.length; ++i) {
      assertThat(actual[i].length).isEqualTo(2);
      assertThat(expected[i].length).isEqualTo(2);

      assertThat(actual[i][0]).isWithin(EPS).of(expected[i][0]);
      assertThat(actual[i][1]).isWithin(EPS).of(expected[i][1]);
    }
  }

  private static Path2D convertToPath(double[][] points) {
    Path2D.Double resultPath = new Path2D.Double();
    resultPath.moveTo(points[0][0], points[0][1]);
    for (int i = 1; i < points.length; ++i) {
      assert points[i].length == 2;
      resultPath.lineTo(points[i][0], points[i][1]);
    }
    return resultPath;
  }

  private static double[][] convertToArray(Path2D path) {
    PathIterator pathIterator = path.getPathIterator(null);
    double[] coords = new double[6];

    ArrayList<Point2D.Double> points = new ArrayList<>();
    while (!pathIterator.isDone()) {
      int type = pathIterator.currentSegment(coords);
      assert type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO;
      points.add(new Point2D.Double(coords[0], coords[1]));
      pathIterator.next();
    }
    double[][] result = new double[points.size()][2];
    for (int i = 0; i < points.size(); ++i) {
      result[i][0] = points.get(i).x;
      result[i][1] = points.get(i).y;
    }
    return result;
  }
}