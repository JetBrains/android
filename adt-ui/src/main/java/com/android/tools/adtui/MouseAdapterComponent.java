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
package com.android.tools.adtui;

import com.intellij.util.containers.*;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.HashMap;

/**
 * Class to build and manipulate rectangles to be drawn in the profiler's UI. This class is not responsible
 * for drawing the rectangles, but only creating and sizing the rectangles according to the mouse over state.
 * All dimensions used in this class are represented as a percentage. It is for the child class to scale the
 * rectangles to the dimensions needed for the control. The most common use case is scaling the rectangles by
 * the components width and height in the draw function.
 *
 * @param K is the key type used to uniquely identify stored rectangles. This key is used to determine the rectangles
 *          height last frame, and adjust it this frame if needed.
 */
public abstract class MouseAdapterComponent<K> extends AnimatedComponent implements MouseListener, MouseMotionListener {

  private static final double EPISLON = 0.0000001;
  private static final double HEIGHT_DELTA_PER_FRAME = .1;

  private double myNormalizedMouseX;
  private double myDefaultHeight = 0;
  private double myExpandedHeight = 0;
  private double myHeightFactor = 1;
  private final HashMap<K, Rectangle2D.Float> myRectangles;

  /**
   * The constructor takes in the rectangle height when not under mouse, and height when under mouse (expanded).
   * The height passed into the constructor follows the standard of working with percentages so the component
   * does not assume the dimensions of the control.
   *
   * @param defaultHeightPercent  the standard height of the rectangle represented as a ratio from 0-1
   * @param expandedHeightPercent the expanded height of the rectangle represented as a ratio from 0-1
   */
  public MouseAdapterComponent(double defaultHeightPercent, double expandedHeightPercent) {
    myDefaultHeight = defaultHeightPercent;
    myExpandedHeight = expandedHeightPercent;
    myRectangles = new HashMap<>();
    attach();
  }

  public void attach() {
    detach();
    addMouseListener(this);
    addMouseMotionListener(this);
  }

  public void detach() {
    removeMouseListener(this);
    removeMouseMotionListener(this);
  }

  /**
   * Function responsible for creating or updating the height of rectangles to be drawn in the UI
   *
   * @param xPercent     the width of the rectangle represented as a percentage of total width from 0-1
   * @param widthPercent the width of a rectangle represented as a percentage of total width from 0-1
   * @param vGap         the spacing added to the height of the rectangle to offset it from the bottom of the control.
   */
  private void updateRect(Rectangle2D.Float rect, double xPercent, double yPercent, double widthPercent, double vGap) {
    // If we are less than the max height of our line, then increase the height.
    double height = rect.getHeight() + vGap;
    if (myNormalizedMouseX != 0 && xPercent < myNormalizedMouseX && xPercent + widthPercent > myNormalizedMouseX) {
      if (height + EPISLON < myExpandedHeight) {
        height += HEIGHT_DELTA_PER_FRAME;
      }
      else {
        height = myExpandedHeight;
      }
    }
    else if (height - EPISLON > myDefaultHeight) {
      height -= HEIGHT_DELTA_PER_FRAME;
    }
    else {
      height = myDefaultHeight;
    }
    // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
    // instead of the top by subtracting our height from 1.
    rect.setRect(xPercent, yPercent + (1 - height), widthPercent, (height - vGap) * myHeightFactor);
  }

  /**
   * @return a copy of the keys defining rectangles currently tracked.
   */
  public Set<K> getRectangleKeys() {
    return ContainerUtil.newHashSet(myRectangles.keySet());
  }

  public void removeRectangle(K key) {
    myRectangles.remove(key);
  }

  public int getRectangleCount() {
    return myRectangles.size();
  }

  public Rectangle2D.Float getRectangle(K key) {
    return myRectangles.get(key);
  }

  public void setHeightFactor(double factor) {
    myHeightFactor = factor;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseExited(MouseEvent e) {
    // Clear mouse position
    setMousePointAndForwardEvent(0, e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  /**
   * Normalizes the mouse position then passes MouseEvent to listeners up the callback stack.
   *
   * @param xPosition mouse x position to be normalized for determining mouse over.
   * @param e         mouse event to be passed on to parent items.
   */
  private void setMousePointAndForwardEvent(double x, MouseEvent e) {
    myNormalizedMouseX = x / (1.0 * getWidth());
    getParent().dispatchEvent(e);
  }

  /**
   * Creates a rectangle if needed with the supplied dimensions. This function will normalize the x, and width values, then store
   * the rectangle off using its key value as a lookup.
   * @param key unique key used to identify the modified rectangle frame over frame.
   * @param previousX value used to determine the x position and width of the rectangle. This value should be relative to the currentX param.
   * @param currentX value used to determine the width of the rectangle. This value should be relative to the previousX param.
   * @param minX minimum value of the range total range used to normalize the x position and width of the rectangle.
   * @param maxX maximum value of the range total range used to normalize the x position and width of the rectangle.
   * @param rectY rectangle height offset from max growth of rectangle. This value is expressed as a percentage from 0-1
   * @param vGap height offset from bottom of rectangle. This value is expressed as percentage from 0-1
   * @return
   */
  protected final Rectangle2D.Float setRectangleData(K key,
                                               double previousX,
                                               double currentX,
                                               double minX,
                                               double maxX,
                                               float rectY,
                                               double vGap) {
    Rectangle2D.Float rectangle = getRectangle(key);
    if (rectangle == null) {
      rectangle = new Rectangle2D.Float();
      myRectangles.put(key, rectangle);
    }
    updateRect(rectangle, (previousX - minX) / (maxX - minX), rectY, (currentX - previousX) / (maxX - minX), vGap);
    return rectangle;
  }
}
