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
package com.android.tools.sherpa.interaction;

import java.util.Arrays;

/**
 * This class provides efficient detection of many objects
 */
public class DrawPicker {
  private final static int MAX_DATA_SIZE = 10;
  private final static int INITAL_OBJECT_STORE = 30;
  private final static double EPSILON = 0.00001;
  private double[] mObjectData = new double[100];
  private int mObjectDataUsed = 0;
  private int[] mObjectOffset = new int[INITAL_OBJECT_STORE];
  private int[] mTypes = new int[INITAL_OBJECT_STORE];
  private int[] mRect = new int[INITAL_OBJECT_STORE * 4];
  private Object[] mObjects = new Object[INITAL_OBJECT_STORE];
  HitElementListener mHitElementListener;

  private int mObjectCount = 0;
  private final static int OBJECT_LINE = 0;
  private final static int OBJECT_POINT = 1;
  private final static int OBJECT_CURVE = 2;
  private final static int OBJECT_RECTANGLE = 3;
  LineSelectionEngine mLine = new LineSelectionEngine();
  PointSelectionEngine mPoint = new PointSelectionEngine();
  CurveToSelectionEngine mCurve = new CurveToSelectionEngine();
  RectangleSelectionEngine mRectangle = new RectangleSelectionEngine();
  SelectionEngine[] myEngines = new SelectionEngine[OBJECT_RECTANGLE + 1];

  {
    myEngines[OBJECT_LINE] = mLine;
    myEngines[OBJECT_POINT] = mPoint;
    myEngines[OBJECT_CURVE] = mCurve;
    myEngines[OBJECT_RECTANGLE] = mRectangle;
  }

  /**
   * Search through all the shapes added and find shapes in range
   * @param x location x
   * @param y location y
   */
  public void find(int x, int y) {
    for (int i = 0; i < mObjectCount; i++) {
      int p = i * 4;
      int x1 = mRect[p++];
      int y1 = mRect[p++];
      int x2 = mRect[p++];
      int y2 = mRect[p];
      if (inRect(x, y, x1, y1, x2, y2)) {
        SelectionEngine selector = myEngines[mTypes[i]];
        if (selector.inRange(i, x, y)) {
          mHitElementListener.over(mObjects[i], selector.distance());
        }
      }
    }
  }

  /**
   * set the listener to be notified of the objects in range
   * @param listener
   */
  public void setSelectListener(HitElementListener listener) {
    mHitElementListener = listener;
  }

  private final boolean inRect(int x, int y, int x1, int y1, int x2, int y2) {
    if (x < x1) return false;
    if (y < y1) return false;
    if (x >= x2) return false;
    if (y >= y2) return false;
    return true;
  }

  /**
   * The interface of the object to be notified
   */
  public interface HitElementListener {
    void over(Object over, double dist);
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

  /**
   * Add a line to the set
   * @param e
   * @param range
   * @param x1
   * @param y1
   * @param x2
   * @param y2
   */
  public void addLine(Object e, int range, int x1, int y1, int x2, int y2) {
    mLine.add(e, range, x1, y1, x2, y2);
  }

  /**
   * Add a rectangle
   * @param e
   * @param range
   * @param x1
   * @param y1
   * @param x2
   * @param y2
   */
  public void addRect(Object e, int range, int x1, int y1, int x2, int y2) {
    mRectangle.add(e, range, x1, y1, x2, y2);
  }

  /**
   * Add a Point
   * @param e
   * @param range
   * @param x1
   * @param y1
   */
  public void addPoint(Object e, int range, int x1, int y1) {
    mPoint.add(e, range, x1, y1);
  }

  /**
   * Add a Bezier curve == to moveTo(x1,y1) curveTo(x2,y2,x3,y3,x4,y4);
   * @param e
   * @param range
   * @param x1
   * @param y1
   * @param x2
   * @param y2
   * @param x3
   * @param y3
   * @param x4
   * @param y4
   */
  public void addCurveTo(Object e, int range, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
    mCurve.add(e, range, x1, y1, x2, y2, x3, y3, x4, y4);
  }

  /*-----------------------------------------------------------------------*/
  // Support point Selection
  /*-----------------------------------------------------------------------*/

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
  // Support point Selection
  /*-----------------------------------------------------------------------*/

  class PointSelectionEngine extends SelectionEngine {
    public void add(Object select, int range, int x1, int y1) {
      resizeTables();
      mTypes[mObjectCount] = OBJECT_POINT;
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      mObjectData[mObjectDataUsed++] = x1;
      mObjectData[mObjectDataUsed++] = y1;
      mObjects[mObjectCount] = select;
      addRect(x1 - range, y1 - range, x1 + range, y1 + range);
      mObjectCount++;
    }

    double mDistance;

    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      double x = mObjectData[mDataOffset + 1];
      double y = mObjectData[mDataOffset + 2];
      mDistance = Math.hypot(x - mMouseX, y - mMouseY);
      return mDistance < range;
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

    public void add(Object select, int range, int x1, int y1, int x2, int y2) {
      resizeTables();
      mObjectOffset[mObjectCount] = mObjectDataUsed;
      mObjectData[mObjectDataUsed++] = range;
      mObjectData[mObjectDataUsed++] = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
      mObjectData[mObjectDataUsed++] = x1;
      mObjectData[mObjectDataUsed++] = y1;
      mObjectData[mObjectDataUsed++] = x2;
      mObjectData[mObjectDataUsed++] = y2;

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
      addRect(x1 - range, y1 - range, x2 + range, y2 + range);
      mObjects[mObjectCount] = select;
      mTypes[mObjectCount] = OBJECT_LINE;
      mObjectCount++;
    }

    @Override
    protected boolean inRange() {
      double range = mObjectData[mDataOffset];
      ;
      double lineLengthSq = mObjectData[mDataOffset + 1];
      ;
      double x1 = mObjectData[mDataOffset + 2];
      double y1 = mObjectData[mDataOffset + 3];
      double x2 = mObjectData[mDataOffset + 4];
      double y2 = mObjectData[mDataOffset + 5];
      if (lineLengthSq < EPSILON) {
        mDistance = Math.hypot(x1 - mMouseX, y1 - mMouseY);
        return mDistance <= range;
      }

      double t = ((mMouseX - x1) * (x2 - x1) + (mMouseY - y1) * (y2 - y1)) / lineLengthSq;
      t = Math.max(0, Math.min(1, t));
      double tx = x1 + t * (x2 - x1);
      double ty = y1 + t * (y2 - y1);
      mDistance = Math.hypot(tx - mMouseX, ty - mMouseY);
      return mDistance <= range;
    }

    @Override
    double distance() {
      return mDistance;
    }
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

    private final boolean inRect(double x, double y, double x1, double y1, double x2, double y2) {
      if (x < x1) return false;
      if (y < y1) return false;
      if (x >= x2) return false;
      if (y >= y2) return false;
      return true;
    }

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
      if (mMouseX > x1 && mMouseX < x2) {
        if (mMouseY < y1) {
          mDistance = y1 - mMouseY;
          return mDistance < range;
        }
        if (mMouseY > y2) {
          mDistance = mMouseY - y2;
          return mDistance < range;
        }
      }
      if (mMouseY > y1 && mMouseY < y2) {
        if (mMouseX < x1) {
          mDistance = x1 - mMouseX;
          return mDistance < range;
        }
        if (mMouseX > x2) {
          mDistance = mMouseX - x2;
          return mDistance < range;
        }
      }

      return false;
    }

    @Override
    double distance() {
      return 0;
    }
  }

  /*-----------------------------------------------------------------------*/
  // Support CurveTo Selection
  /*-----------------------------------------------------------------------*/

  class CurveToSelectionEngine extends SelectionEngine {
    double cx0;
    double cx1;
    double cx2;
    double cx3;
    double cy0;
    double cy1;
    double cy2;
    double cy3;
    double mDistance;

    public void add(Object select, int range, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
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
      mObjects[mObjectCount] = select;
      mTypes[mObjectCount] = OBJECT_CURVE;
      bounds(range);
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
      double rangeSqr = range * range;
      //TODO currently returns first distance in range not minimum distance
      for (double t = 0; t < 1; t += .03) {
        double t2 = t * t;
        double t3 = t * t2;
        double x = cx0 + cx1 * t + cx2 * t2 + cx3 * t3;
        double y = cy0 + cy1 * t + cy2 * t2 + cy3 * t3;
        double dx = x - mMouseX;
        double dy = y - mMouseY;
        double distanceSq = dx * dx + dy * dy;
        if (rangeSqr > distanceSq) {
          mDistance = Math.sqrt(distanceSq);
          return true;
        }
      }
      return false;
    }

    public final double evalX(double t) {
      double t2 = t * t;
      double t3 = t * t2;
      double x = cx0 + cx1 * t + cx2 * t2 + cx3 * t3;
      return x;
    }

    public final double evalY(double t) {
      double t2 = t * t;
      double t3 = t * t2;
      double y = cy0 + cy1 * t + cy2 * t2 + cy3 * t3;
      return y;
    }

    /**
     * Bounds is calculated by starting and ending point
     * solve(diff dx/dt==0,t) and solve(diff dy/dt==0,t)
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
}
