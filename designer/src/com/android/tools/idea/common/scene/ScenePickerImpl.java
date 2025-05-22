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

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * This class provides efficient detection of many objects
 */
public class ScenePickerImpl implements ScenePicker.Writer, ScenePicker.Reader {
  private final static int MAX_DATA_SIZE = 10;
  private final static int INITAL_OBJECT_STORE = 30;
  private final static double EPSILON = 0.00001;
  private double[] mObjectData = new double[100];
  private int mObjectDataUsed = 0;
  private int[] mObjectOffset = new int[INITAL_OBJECT_STORE];
  private int[] mTypes = new int[INITAL_OBJECT_STORE];
  private int[] mRect = new int[INITAL_OBJECT_STORE * 4];
  private Object[] mObjects = new Object[INITAL_OBJECT_STORE];

  private int mObjectCount = 0;
  private final static int OBJECT_LINE = 0;
  private final static int OBJECT_CURVE = 1;
  private final static int OBJECT_RECTANGLE = 2;
  private final static int OBJECT_CIRCLE = 3;
  LineSelectionEngine mLine = new LineSelectionEngine();
  CurveToSelectionEngine mCurve = new CurveToSelectionEngine();
  RectangleSelectionEngine mRectangle = new RectangleSelectionEngine();
  CircleSelectionEngine mCircle = new CircleSelectionEngine();
  SelectionEngine[] myEngines = new SelectionEngine[OBJECT_CIRCLE + 1];

  {
    myEngines[OBJECT_LINE] = mLine;
    myEngines[OBJECT_CURVE] = mCurve;
    myEngines[OBJECT_RECTANGLE] = mRectangle;
    myEngines[OBJECT_CIRCLE] = mCircle;
  }

  ScenePickerImpl() {}

  /**
   * Search through all the shapes added and find shapes in range
   *
   * @param x location x
   * @param y location y
   * @return the list of objects hit on x,y
   */
  @Override
  public ImmutableList<ScenePicker.HitResult> find(int x, int y) {
    ImmutableList.Builder<ScenePicker.HitResult> builder = new ImmutableList.Builder<>();
    for (int i = 0; i < mObjectCount; i++) {
      int p = i * 4;
      int x1 = mRect[p++];
      int y1 = mRect[p++];
      int x2 = mRect[p++];
      int y2 = mRect[p];
      if (inRect(x, y, x1, y1, x2, y2)) {
        SelectionEngine selector = myEngines[mTypes[i]];
        if (selector.inRange(i, x, y)) {
          builder.add(new ScenePicker.HitResult(mObjects[i], selector.distance()));
        }
      }
    }
    return builder.build();
  }

  private static boolean inRect(int x, int y, int x1, int y1, int x2, int y2) {
    if (x < x1) return false;
    if (y < y1) return false;
    if (x > x2) return false;
    if (y > y2) return false;
    return true;
  }


  private static boolean inRect(double x, double y, double x1, double y1, double x2, double y2) {
    if (x < x1) return false;
    if (y < y1) return false;
    if (x > x2) return false;
    if (y > y2) return false;
    return true;
  }

  /**
   * Resets the tables to allow reuse of class
   */
  public void reset() {
    mObjectCount = 0;
    mObjectDataUsed = 0;
    Arrays.fill(mObjects, null);// delete references
  }

  /**
   * resize tables as the number of objects grow
   */
  private void resizeTables() {
    if (mObjectDataUsed > mObjectData.length - MAX_DATA_SIZE) {
      mObjectData = Arrays.copyOf(mObjectData, mObjectData.length * 2);
    }
    if (mObjectCount < mTypes.length) {
      return;
    }
    mObjectOffset = Arrays.copyOf(mObjectOffset, mObjectOffset.length * 2);
    mTypes = Arrays.copyOf(mTypes, mTypes.length * 2);
    mObjects = Arrays.copyOf(mObjects, mObjects.length * 2);
    mRect = Arrays.copyOf(mRect, mRect.length * 2);
  }

  @Override
  public void addLine(Object e, int range, int x1, int y1, int x2, int y2, int width) {
    mLine.add(e, range, x1, y1, x2, y2, width);
  }

  @Override
  public void addRect(Object e, int range, int x1, int y1, int x2, int y2) {
    mRectangle.add(e, range, x1, y1, x2, y2);
  }

  @Override
  public void addCircle(Object e, int range, int x1, int y1, int r) {
    mCircle.add(e, range, x1, y1, r);
  }

  @Override
  public void addCurveTo(Object e, int range, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int width) {
    mCurve.add(e, range, x1, y1, x2, y2, x3, y3, x4, y4, width);
  }

  /**
   * Base class for all selection
   */
  private abstract class SelectionEngine {
    protected int mObject;
    protected int mMouseX;
    protected int mMouseY;
    protected int mDataOffset;

    protected void addRect(int x1, int y1, int x2, int y2) {
      int off = mObjectCount * 4;
      mRect[off++] = x1;
      mRect[off++] = y1;
      mRect[off++] = x2;
      mRect[off] = y2;
    }

    boolean inRange(int object, int x, int y) {
      mObject = object;
      mDataOffset = mObjectOffset[mObject];

      mMouseX = x;
      mMouseY = y;
      return inRange();
    }

    abstract protected boolean inRange();

    abstract double distance();
  }

  /*-----------------------------------------------------------------------*/
  // Support circle Selection
  /*-----------------------------------------------------------------------*/

  class CircleSelectionEngine extends SelectionEngine {
    public void add(Object select, int range, int x1, int y1, int r) {
      resizeTables();
      mTypes[mObjectCount] = OBJECT_CIRCLE;
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      mObjectData[mObjectDataUsed++] = x1;
      mObjectData[mObjectDataUsed++] = y1;
      mObjectData[mObjectDataUsed++] = r;
      mObjects[mObjectCount] = select;
      addRect(x1 - range - r, y1 - range - r, x1 + range + r, y1 + range + r);
      mObjectCount++;
    }

    double mDistance;

    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      double x = mObjectData[mDataOffset + 1];
      double y = mObjectData[mDataOffset + 2];
      double r = mObjectData[mDataOffset + 3];

      double d = Math.hypot(x - mMouseX, y - mMouseY);

      if(d < r)
      {
        mDistance = 0;
        return true;
      }

      mDistance = d - r;
      return mDistance <= range;
    }

    @Override
    double distance() {
      return mDistance;
    }
  }

  /*-----------------------------------------------------------------------*/
  // Support Line Selection
  /*-----------------------------------------------------------------------*/

  class LineSelectionEngine extends SelectionEngine {
    double mDistance;

    public void add(Object select, int range, int x1, int y1, int x2, int y2, int width) {
      resizeTables();
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      mObjectData[mObjectDataUsed++] = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
      mObjectData[mObjectDataUsed++] = x1;
      mObjectData[mObjectDataUsed++] = y1;
      mObjectData[mObjectDataUsed++] = x2;
      mObjectData[mObjectDataUsed++] = y2;
      mObjectData[mObjectDataUsed++] = width;

      if (x1 > x2) {
        int t = x1;
        x1 = x2;
        x2 = t;
      }
      if (y1 > y2) {
        int t = y1;
        y1 = y2;
        y2 = t;
      }
      int r = range + width;
      addRect(x1 - r, y1 - r, x2 + r, y2 + r);
      mObjects[mObjectCount] = select;
      mTypes[mObjectCount] = OBJECT_LINE;
      mObjectCount++;
    }

    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      double lineLengthSq = mObjectData[mDataOffset + 1];
      double x1 = mObjectData[mDataOffset + 2];
      double y1 = mObjectData[mDataOffset + 3];
      double x2 = mObjectData[mDataOffset + 4];
      double y2 = mObjectData[mDataOffset + 5];
      double w = mObjectData[mDataOffset + 6];
      mDistance = Math.max(Math.sqrt(lineDistanceSqr(lineLengthSq, x1, y1, x2, y2, mMouseX, mMouseY)) - w, 0);
      return mDistance <= range;
    }


    @Override
    double distance() {
      return mDistance;
    }
  }

  private static double lineDistanceSqr(double lineLengthSq, double x1, double y1, double x2, double y2, int mouseX, int mouseY) {
    if (lineLengthSq < EPSILON) {
      return Math.hypot(x1 - mouseX, y1 - mouseY);
    }

    double t = ((mouseX - x1) * (x2 - x1) + (mouseY - y1) * (y2 - y1)) / lineLengthSq;
    t = Math.max(0, Math.min(1, t));
    double tx = x1 + t * (x2 - x1);
    double ty = y1 + t * (y2 - y1);
    return ((tx - mouseX) * (tx - mouseX)) + ((ty - mouseY) * (ty - mouseY));
  }

  /*-----------------------------------------------------------------------*/
  // Support rectangle Selection
  /*-----------------------------------------------------------------------*/

  class RectangleSelectionEngine extends SelectionEngine {
    double mDistance;

    public void add(Object select, int range, int x1, int y1, int x2, int y2) {
      resizeTables();
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      if (x1 > x2) {
        int t = x1;
        x1 = x2;
        x2 = t;
      }
      if (y1 > y2) {
        int t = y1;
        y1 = y2;
        y2 = t;
      }
      mObjectData[mObjectDataUsed++] = x1;
      mObjectData[mObjectDataUsed++] = y1;
      mObjectData[mObjectDataUsed++] = x2;
      mObjectData[mObjectDataUsed++] = y2;

      addRect(x1 - range, y1 - range, x2 + range, y2 + range);
      mObjects[mObjectCount] = select;
      mTypes[mObjectCount] = OBJECT_RECTANGLE;
      mObjectCount++;
    }

    /**
     *
     *
     * @return
     */
    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      double x1 = mObjectData[mDataOffset + 1];
      double y1 = mObjectData[mDataOffset + 2];
      double x2 = mObjectData[mDataOffset + 3];
      double y2 = mObjectData[mDataOffset + 4];
      if (inRect(mMouseX, mMouseY, x1, y1, x2, y2)) {
        mDistance = 0;
        return true;
      }
      if (mMouseX >= x1 && mMouseX <= x2) {
        if (mMouseY < y1) {
          mDistance = y1 - mMouseY;
          return mDistance < range;
        }
        if (mMouseY > y2) {
          mDistance = mMouseY - y2;
          return mDistance < range;
        }
      }
      if (mMouseY >= y1 && mMouseY <= y2) {
        if (mMouseX < x1) {
          mDistance = x1 - mMouseX;
          return mDistance < range;
        }
        if (mMouseX > x2) {
          mDistance = mMouseX - x2;
          return mDistance < range;
        }
      }
      if (mMouseX < x1) {
        if (mMouseY < y1) {

          mDistance = Math.hypot(mMouseX - x1, mMouseY - y1);
          if (mDistance <= range) {
            return true;
          }
        } else {
          mDistance = Math.hypot(mMouseX - x1, mMouseY - y2);
          if (mDistance <= range) {
            return true;
          }
        }
      }
      else {
        if (mMouseY <= y1) {
          mDistance = Math.hypot(mMouseX - x2, mMouseY - y1);
          if (mDistance <= range) {
            return true;
          }
        } else {
          mDistance = Math.hypot(mMouseX - x2, mMouseY - y2);
          if (mDistance <= range) {
            return true;
          }
        }
      }

      return false;
    }

    @Override
    double distance() {
      return mDistance;
    }
  }

  /*-----------------------------------------------------------------------*/
  // Support CurveTo Selection
  /*-----------------------------------------------------------------------*/

  public class CurveToSelectionEngine extends SelectionEngine {
    double cx0;
    double cx1;
    double cx2;
    double cx3;
    double cy0;
    double cy1;
    double cy2;
    double cy3;
    double w;
    double mDistance;

    public void add(Object select, int range, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int width) {
      resizeTables();
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      // compute simplified polynomial expressions for efficiency
      cx0 = x1;
      cx1 = 3 * x2 - 3 * x1;
      cx2 = 3 * x3 - 6 * x2 + 3 * x1;
      cx3 = x4 - 3 * x3 + 3 * x2 - x1;
      cy0 = y1;
      cy1 = 3 * y2 - 3 * y1;
      cy2 = 3 * y3 - 6 * y2 + 3 * y1;
      cy3 = y4 - 3 * y3 + 3 * y2 - y1;
      mObjectData[mObjectDataUsed++] = cx0;
      mObjectData[mObjectDataUsed++] = cx1;
      mObjectData[mObjectDataUsed++] = cx2;
      mObjectData[mObjectDataUsed++] = cx3;
      mObjectData[mObjectDataUsed++] = cy0;
      mObjectData[mObjectDataUsed++] = cy1;
      mObjectData[mObjectDataUsed++] = cy2;
      mObjectData[mObjectDataUsed++] = cy3;
      mObjectData[mObjectDataUsed++] = width;
      mObjects[mObjectCount] = select;
      mTypes[mObjectCount] = OBJECT_CURVE;
      bounds(range + width);
      mObjectCount++;
    }

    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      cx0 = mObjectData[mDataOffset + 1];
      cx1 = mObjectData[mDataOffset + 2];
      cx2 = mObjectData[mDataOffset + 3];
      cx3 = mObjectData[mDataOffset + 4];
      cy0 = mObjectData[mDataOffset + 5];
      cy1 = mObjectData[mDataOffset + 6];
      cy2 = mObjectData[mDataOffset + 7];
      cy3 = mObjectData[mDataOffset + 8];
      w = mObjectData[mDataOffset + 9];

      double minDistanceSqr = Integer.MAX_VALUE;
      double widthSqr = w * w;
      double prevX = cx0;
      double prevY = cy0;
      for (double t = .03; t < 1; t += .03) {
        double t2 = t * t;
        double t3 = t * t2;
        double x = cx0 + cx1 * t + cx2 * t2 + cx3 * t3;
        double y = cy0 + cy1 * t + cy2 * t2 + cy3 * t3;

        double segmentSqr = ((x - prevX) * (x - prevX)) + ((y - prevY) * (y - prevY));
        double distanceSqr = lineDistanceSqr(segmentSqr, prevX, prevY, x, y, mMouseX, mMouseY);

        if(distanceSqr < widthSqr) {
          mDistance = 0;
          return true;
        }

        minDistanceSqr = Math.min(minDistanceSqr, distanceSqr);

        prevX = x;
        prevY = y;
      }

      if (minDistanceSqr > (range + w) * (range + w)) {
        return false;
      }

      mDistance = Math.sqrt(minDistanceSqr) - w;
      return true;
    }

    public final double evalX(double t) {
      double t2 = t * t;
      double t3 = t * t2;
      return cx0 + cx1 * t + cx2 * t2 + cx3 * t3;
    }

    public final double evalY(double t) {
      double t2 = t * t;
      double t3 = t * t2;
      return cy0 + cy1 * t + cy2 * t2 + cy3 * t3;
    }

    /**
     * Bounds is calculated by starting and ending point
     * solve(diff dx/dt==0,t) and solve(diff dy/dt==0,t)
     *
     * @param range
     */
    public final void bounds(int range) {
      double x0 = cx0;
      double y0 = cy0;
      double x1 = cx0 + cx1 + cx2 + cx3;
      double y1 = cy0 + cy1 + cy2 + cy3;
      double minx = Math.min(x0, x1);
      double miny = Math.min(y0, y1);
      double maxx = Math.max(x0, x1);
      double maxy = Math.max(y0, y1);

      if (cx3 != 0) {
        double t1 = -(Math.sqrt(cx2 * cx2 - 3 * cx1 * cx3) + cx2) / (3 * cx3);
        double t2 = (Math.sqrt(cx2 * cx2 - 3 * cx1 * cx3) - cx2) / (3 * cx3);
        if (t1 > 0 && t1 < 1) {
          double x = evalX(t1);
          minx = Math.min(x, minx);
          maxx = Math.max(x, maxx);
        }
        if (t2 > 0 && t2 < 1) {
          double x = evalX(t2);
          minx = Math.min(x, minx);
          maxx = Math.max(x, maxx);
        }
      }
      if (cy3 != 0) {
        double t1 = -(Math.sqrt(cy2 * cy2 - 3 * cy1 * cy3) + cy2) / (3 * cy3);
        double t2 = (Math.sqrt(cy2 * cy2 - 3 * cy1 * cy3) - cy2) / (3 * cy3);
        if (t1 > 0 && t1 < 1) {
          double y = evalY(t1);
          miny = Math.min(y, miny);
          maxy = Math.max(y, maxy);
        }
        if (t2 > 0 && t2 < 1) {
          double y = evalY(t2);
          miny = Math.min(y, miny);
          maxy = Math.max(y, maxy);
        }
      }
      addRect((int)minx - range, (int)miny - range,
              (int)(maxx + range), (int)(maxy + range));
    }

    @Override
    double distance() {
      return mDistance;
    }
  }

  @TestOnly
  @NotNull
  public static ScenePickerImpl createForTest() {
    return new ScenePickerImpl();
  }
}
