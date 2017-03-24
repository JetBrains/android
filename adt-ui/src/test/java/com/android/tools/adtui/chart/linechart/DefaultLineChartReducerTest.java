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

import com.android.tools.adtui.model.SeriesData;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class DefaultLineChartReducerTest {
  private static final float EPS = 1e-6f;
  // DefaultLineChartReducer reduces Path2D, so it means that points are in screen coordinates.
  // FAKE_HEIGHT is needed to convert from screen coordinates to cartesian coordinates.
  private static int FAKE_HEIGHT = 100;

  private DefaultLineChartReducer myReducer;
  private LineConfig myConfig;

  @Before
  public void setUp() {
    myReducer = new DefaultLineChartReducer();
    myConfig = new LineConfig(Color.RED);
  }

  @Test
  public void reduceData() {
    List<SeriesData<Long>> data = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 10L))
      .add(new SeriesData<>(1, 10L))
      .add(new SeriesData<>(2, 13L))
      .add(new SeriesData<>(3, 13L))
      .add(new SeriesData<>(4, 13L))
      .add(new SeriesData<>(5, 13L))
      .add(new SeriesData<>(6, 5L))
      .add(new SeriesData<>(7, 5L)).build();
    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 10L))
      .add(new SeriesData<>(1, 10L))
      .add(new SeriesData<>(2, 13L))
      .add(new SeriesData<>(5, 13L))
      .add(new SeriesData<>(6, 5L))
      .add(new SeriesData<>(7, 5L)).build();
    List<SeriesData<Long>> result = myReducer.reduceData(data, myConfig);
    assertSeriesEquals(expected, result);
  }

  @Test
  public void reduceDataForSteppedLine() {
    List<SeriesData<Long>> data = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 10L))
      .add(new SeriesData<>(1, 10L))
      .add(new SeriesData<>(2, 13L))
      .add(new SeriesData<>(3, 13L))
      .add(new SeriesData<>(4, 13L))
      .add(new SeriesData<>(5, 13L))
      .add(new SeriesData<>(6, 5L))
      .add(new SeriesData<>(7, 5L)).build();

    List<SeriesData<Long>> expected = new ImmutableList.Builder<SeriesData<Long>>()
      .add(new SeriesData<>(0, 10L))
      .add(new SeriesData<>(2, 13L))
      .add(new SeriesData<>(6, 5L))
      .add(new SeriesData<>(7, 5L)).build();

    myConfig.setStepped(true);
    List<SeriesData<Long>> result = myReducer.reduceData(data, myConfig);
    assertSeriesEquals(expected, result);
  }

  @Test
  public void simpleReducePath() {
    float[][] given = {{0, 0}, {0.1f, 1}, {0.2f, 6}, {0.3f, 4}, {1, 2}, {1.1f, 5}};
    float[][] expected = {{0, 0}, {0.2f, 6}, {0.3f, 4}, {1, 2}, {1.1f, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathForNegativeValues() {
    float[][] given = {{1, 5}, {2f, -1f}, {2f, 0}, {1, 0}};
    float[][] expected = {{1, 5}, {2f, -1}, {2, 0}, {1, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);
    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathWhenOnePointPerPixel() {
    float[][] given = {{1.2f, 2}, {2, 0}, {3, 5}};
    float[][] expected = {{1.2f, 2}, {2, 0}, {3, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathWhenNoPointInPixel() {
    float[][] given = {{6, 0}, {7, 5}};
    float[][] expected = {{6, 0}, {7, 5}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathWhenSameY() {
    float[][] given = {{1, 4}, {1.2f, 4}, {1.3f, 4}, {1.5f, 4}, {1.6f, 4}, {1.7f, 4}};
    float[][] expected = {{1, 4}, {1.7f, 4}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathFirstAndLastPointsShouldRemain() {
    float[][] given = {{1, 4}, {1.2f, 6}, {1.3f, 0}, {1.4f, 2}, {1.5f, 0}, {1.7f, 4}};
    float[][] expected = {{1, 4}, {1.2f, 6}, {1.5f, 0}, {1.7f, 4}};

    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathWhenClosedPolyLine() {
    float[][] given = {{0, 1}, {0.5f, 1}, {1, 3}, {1.5f, 4}, {2, 3}, {2, 0}, {0, 0}};
    float[][] expected = {{0, 1}, {0.5f, 1}, {1, 3}, {1.5f, 4.0f}, {2, 3}, {2, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathClosedPolylineLastPointsShouldRemain() {
    float[][] given = {{0, 4}, {0.5f, 5}, {1.1f, 0}, {1.2f, 0}, {1.3f, 5}, {1.4f, 5}, {1.9f, 5}, {1.9f, 0}, {0, 0}};
    float[][] expected = {{0, 4}, {0.5f, 5}, {1.1f, 0}, {1.3f, 5}, /*should peek up right most minimum*/ {1.9f, 0}, {0, 0}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);

    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
    assertPointsEquals(expected, result);
  }

  @Test
  public void reducePathWhenSteppedLine() {
    float[][] given = {{0, 5}, {0.1f, 5}, {0.1f, 4}, {0.2f, 4}, {0.2f, 6}, {0.3f, 6}, {0.3f, 4.1f}, {0.5f, 4.1f}, {0.5f, 4.9f}, {3, 4.9f}, {3, 4.8f}};
    float[][] expected = {{0, 5}, {0.2f, 5},  {0.2f, 4}, {0.2f, 6}, {0.5f, 6}, {0.5f, 4.9f}, {3, 4.9f}, {3, 4.8f}};
    convertToScreenCoordinates(given);
    convertToScreenCoordinates(expected);
    myConfig.setStepped(true);
    float[][] result = convertToArray(myReducer.reducePath(convertToPath(given), myConfig));
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

  private static void assertSeriesEquals(List<SeriesData<Long>> expected, List<SeriesData<Long>> actual) {
    assertThat(actual.size()).isEqualTo(expected.size());
    for (int i = 0; i < expected.size(); ++i) {
      assertThat(actual.get(i).x).isEqualTo(expected.get(i).x);
      assertThat(actual.get(i).value).isEqualTo(expected.get(i).value);
    }
  }
}