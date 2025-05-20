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
package com.android.tools.idea.common.scene;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;
import junit.framework.TestCase;

/**
 * Test ScenePicker
 */
public class ScenePickerImplTest extends TestCase {
  double expectedDistance = 0;
  double error = 0;
  private Consumer<ScenePicker.HitResult> myHitConsumer;

  private void findHits(ScenePickerImpl scenePickerImpl, int x, int y) {
    scenePickerImpl.find(x, y).forEach((hit) -> myHitConsumer.accept(hit));
  }

  public void testLine() {
    testLine(0, 10);
    testLine(5, 5);
    testLine(10, 0);
  }

  private void testLine(int range, int w) {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    scenePickerImpl.addLine(1, range, 10, 10, 100, 100, w);
    scenePickerImpl.addLine(1, range, 2100, 2100, 1100, 1100, w);
    boolean[] found = new boolean[1];
    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      assertEquals(expectedDistance, hit.distance(), error);
      found[0] = true;
    };
    expectedDistance = 0;
    findHits(scenePickerImpl, 10, 10);
    assertTrue(found[0]);
    found[0] = false;
    findHits(scenePickerImpl, 50, 50);
    assertTrue(found[0]);

    found[0] = false;
    findHits(scenePickerImpl, 99, 99);
    assertTrue(found[0]);

    found[0] = false;
    findHits(scenePickerImpl, 100, 100);
    assertTrue(found[0]);
    found[0] = false;
    expectedDistance = Math.max(Math.hypot(7, 7) - w, 0);

    findHits(scenePickerImpl, 3, 3);
    assertTrue(found[0]);

    found[0] = false;
    findHits(scenePickerImpl, 0, 0);
    assertFalse(found[0]);
  }

  public void testRect() {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    scenePickerImpl.addRect(1, 10, 10, 10, 100, 100);
    boolean[] found = new boolean[1];
    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      assertEquals(expectedDistance, hit.distance(), error);
      found[0] = true;
    };
    expectedDistance = 0;

    // inside rectangle
    for (int i = 0; i < 100; i++) {
      int x = i % 10;
      int y = i / 10;
      x = x * 9 + 11;
      y = y * 9 + 11;

      found[0] = false;
      findHits(scenePickerImpl, x, y);
      assertTrue(x + "," + y, found[0]);
    }

    // along edge
    Shape s = new Rectangle2D.Double(10, 10, 90, 90);
    double[] point = new double[2];
    for (PathIterator pi = s.getPathIterator(null); !pi.isDone(); pi.next()) {
      pi.currentSegment(point);
      found[0] = false;
      findHits(scenePickerImpl, (int)point[0], (int)point[1]);
      assertTrue((int)point[0] + "," + (int)point[1], found[0]);
    }
    found[0] = false;
    findHits(scenePickerImpl, 50, 50);
    assertTrue(found[0]);

    found[0] = false;
    findHits(scenePickerImpl, 99, 99);
    assertTrue(found[0]);

    found[0] = false;
    findHits(scenePickerImpl, 99, 99);
    assertTrue(found[0]);

    found[0] = false;
    expectedDistance = (102 - 100);
    findHits(scenePickerImpl, 102, 99);
    assertTrue(found[0]);

    found[0] = false;
    expectedDistance = 0;
    findHits(scenePickerImpl, 100, 100);
    assertTrue(found[0]);

    found[0] = false;
    expectedDistance = Math.hypot(6, 6);
    findHits(scenePickerImpl, 4, 4);
    assertTrue(found[0]);

    found[0] = false;
    expectedDistance = Math.hypot(10, 10);
    findHits(scenePickerImpl, 0, 0);
    assertFalse(found[0]);

    scenePickerImpl.reset();
    scenePickerImpl.addRect(1, 10, 11, 11, 10, 10);
    error = 5;
    for (int y = 0; y < 20; y += 1) {
      for (int x = 0; x < 20; x += 1) {

        expectedDistance = Math.hypot(x - 10, y - 10);
        expectedDistance = Math.min(expectedDistance, Math.hypot(x - 11, y - 10));
        expectedDistance = Math.min(expectedDistance, Math.hypot(x - 10, y - 11));
        expectedDistance = Math.min(expectedDistance, Math.hypot(x - 11, y - 11));

        found[0] = false;
        findHits(scenePickerImpl, x, y);
        if (found[0]) {
          assertTrue(x + "," + y + " dist " + expectedDistance, !(expectedDistance > 12));
        }
        else {
          assertTrue(x + "," + y + " dist " + expectedDistance, !(expectedDistance < 10));
        }
      }
    }
  }

  public void testCurveTo() {
    testCurveTo(4, 0);
    testCurveTo(2, 2);
    testCurveTo(0, 4);
  }

  private void testCurveTo(int range, int w)
  {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    int x1 = 10;
    int y1 = 10;
    int x2 = 40;
    int y2 = 10;
    int x3 = 60;
    int y3 = 100;
    int x4 = 100;
    int y4 = 100;
    error = 3;
    scenePickerImpl.addCurveTo(1, range, x1, y1, x2, y2, x3, y3, x4, y4, w);
    boolean[] found = new boolean[1];

    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      // distance to spline is not accurate
      found[0] = true;
    };
    expectedDistance = 0;
    GeneralPath s = new GeneralPath();
    s.moveTo(x1, y1);
    s.curveTo(x2, y2, x3, y3, x4, y4);
    double[] point = new double[6];
    for (PathIterator pi = new FlatteningPathIterator(s.getPathIterator(null), .01); !pi.isDone(); pi.next()) {

      pi.currentSegment(point);
      found[0] = false;
      findHits(scenePickerImpl, (int)point[0], (int)point[1]);
      assertTrue((int)point[0] + "," + (int)point[1], found[0]);
    }

    s = new GeneralPath();
    s.moveTo(x1, y1);
    s.curveTo(x2, y2, x3, y3, x4, y4);
    AffineTransform at = new AffineTransform();
    at.translate(50, 20);
    s.transform(at);

    for (PathIterator pi = new FlatteningPathIterator(s.getPathIterator(null), .01); !pi.isDone(); pi.next()) {

      pi.currentSegment(point);
      found[0] = false;
      findHits(scenePickerImpl, (int)point[0], (int)point[1]);
      assertFalse((int)point[0] + "," + (int)point[1], found[0]);
    }
  }

  public void testCircle() {
    boolean[] found = new boolean[1];

    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      assertEquals(expectedDistance, hit.distance(), error);
      found[0] = true;
    };

    int x1 = 10;
    int y1 = 10;
    int maxRadius = 10;

    for (int r = 0; r <= maxRadius; r++) {
      for (int range = 0; range <= maxRadius - r; range++) {
        for (int x = x1 - maxRadius; x <= x1 + maxRadius; x++) {
          for (int y = y1 - maxRadius; y <= y1 + maxRadius; y++) {
            scenePickerImpl.reset();
            scenePickerImpl.addCircle(1, range, x1, y1, r);
            found[0] = false;
            expectedDistance = Math.max(Math.hypot(x - x1, y - y1) - r, 0);

            findHits(scenePickerImpl, x, y);

            assertEquals(x + "," + y + "," + r + "," + range, found[0], expectedDistance <= range);
          }
        }
      }
    }
  }

  public void testAddingMany() {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    error = 2;

    for (int i = 0; i < 1000; i++) {
      scenePickerImpl.addCircle(1, 3, 2 * (i % 100), 2 * (i / 100), 0);
    }

    boolean[] found = new boolean[1];
    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      assertEquals(expectedDistance, hit.distance(), error);
      found[0] = true;
    };

    for (int i = 0; i < 1000; i++) {
      expectedDistance = Math.hypot(1, 1);
      found[0] = false;
      findHits(scenePickerImpl, 2 * (i % 100), 2 * (i / 100));
      assertTrue(found[0]);
    }
  }

  public void testEllipseCode() {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl() {
      {
        int x1 = 10;
        int y1 = 10;
        int x2 = 40;
        int y2 = 10;
        int x3 = 60;
        int y3 = 100;
        int x4 = 100;
        int y4 = 100;
        Rectangle rect = new Rectangle(x1 - 1, y1 - 1, x4 - x1 + 2, y4 - y1 + 2);
        CurveToSelectionEngine c = new CurveToSelectionEngine();
        c.add(null, 10, x1, y1, x2, y2, x3, y3, x4, y4, 0);
        for (double i = 0; i < 1; i += .03) {
          double x = c.evalX(i);
          double y = c.evalY(i);
          assertTrue(rect.contains(x, y));
        }
      }
    };
  }

  public void testCurveHit() {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl() {
      {
        int x1 = 10;
        int y1 = 10;
        int x2 = 40;
        int y2 = 10;
        int x3 = 60;
        int y3 = 100;
        int x4 = 100;
        int y4 = 100;
        CurveToSelectionEngine c = new CurveToSelectionEngine();
        c.add(null, 1, x1, y1, x2, y2, x3, y3, x4, y4, 0);
        for (double i = 0; i < 1; i += .001) {
          double x = c.evalX(i);
          double y = c.evalY(i);
          assertTrue(c.inRange(0, (int)x, (int)y));

          assertFalse(c.inRange(0, (int)x + 10, (int)y - 10));
        }
      }
    };
  }

  public void testEdgeCasesCurveTo() {
    ScenePickerImpl scenePickerImpl = new ScenePickerImpl();
    scenePickerImpl.reset();
    int x1 = 1;
    int y1 = 1;
    int x2 = 4;
    int y2 = 1;
    int x3 = 6;
    int y3 = 10;
    int x4 = 10;
    int y4 = 10;
    error = 3;
    scenePickerImpl.addCurveTo(1, 4, x1, y1, x2, y2, x3, y3, x4, y4, 0);

    myHitConsumer = (hit) -> {
      assertEquals(1, Math.toIntExact((Integer)hit.object()));
      assertTrue(hit.distance() <= 4);
    };
    expectedDistance = 0;

    for (int i = 0; i < 10000; i++) {
      double x = (i % 100) / 100.;
      @SuppressWarnings("IntegerDivisionInFloatingPointContext") double y = (i / 100) / 100.;
      x = 13 * x - 1;
      y = 13 * y - 1;
      findHits(scenePickerImpl, (int)x, (int)y);
    }
  }
}
