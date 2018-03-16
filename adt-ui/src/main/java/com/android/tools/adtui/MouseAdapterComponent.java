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

import com.intellij.util.containers.ContainerUtil;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Set;

/**
 * Class to build and manipulate rectangles to be drawn in the profiler's UI. This class is not responsible
 * for drawing the rectangles, but only creating, and determining if the mouse is over a rectangle.
 * All dimensions used in this class are represented as a percentage. It is for the child class to scale the
 * rectangles to the dimensions needed for the control. The most common use case is scaling the rectangles by
 * the components width and height in the draw function.
 *
 * @param T is the value type used to be associated with stored rectangles.
 */
public abstract class MouseAdapterComponent<T> extends AnimatedComponent implements MouseListener, MouseMotionListener {
  private double myNormalizedMouseX;
  private double myHeightFactor = 1;
  private final HashMap<Rectangle2D.Float, T> myRectangles;

  /**
   * Default constructor for a MouseAdapterComponent, the constructor attaches mouse listeners.
   */
  public MouseAdapterComponent() {
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
   * Function responsible for creating rectangles to be drawn in the UI
   *
   * @param xPercent     the width of the rectangle represented as a percentage of total width from 0-1
   * @param widthPercent the width of a rectangle represented as a percentage of total width from 0-1
   * @param vGap         the spacing added to the height of the rectangle to offset it from the bottom of the control.
   */
  private Rectangle2D.Float buildRectangle(double xPercent, double yPercent, double widthPercent, double vGap) {
    // If we are less than the max height of our line, then increase the height.
    Rectangle2D.Float rect = new Rectangle2D.Float();
    // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
    // instead of the top by subtracting our height from 1.
    rect.setRect(xPercent, yPercent, widthPercent, (1 - vGap) * myHeightFactor);
    return rect;
  }

  /**
   * @return a copy of the keys defining rectangles currently tracked.
   */
  public Set<Rectangle2D.Float> getRectangles() {
    return ContainerUtil.newHashSet(myRectangles.keySet());
  }

  public void clearRectangles() {
    myRectangles.clear();
  }

  public int getRectangleCount() {
    return myRectangles.size();
  }

  public T getRectangleValue(Rectangle2D.Float rect) {
    return myRectangles.get(rect);
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
    // TODO (b/74547254): Revisit mouse over animation to be based on range instead of mouse coordinates.
    myNormalizedMouseX = x;
    // Parent can be null in test.
    if (getParent() != null) {
      getParent().dispatchEvent(e);
    }
  }

  /**
   * Returns whether the mouse is over a given rectangle.
   *
   * Note: The input rectangle is assumed to be scaled to screen space.
   *
   * @param rectangleScreenSpace The rectangle scaled to be in screen space.
   * @return True if the mouse is within bounds of the {@code rectangleScreenSpace}. False otherwise.
   */
  public boolean isMouseOverRectangle(Rectangle2D rectangleScreenSpace) {
    return myNormalizedMouseX >= rectangleScreenSpace.getX() &&
           myNormalizedMouseX <= rectangleScreenSpace.getWidth() + rectangleScreenSpace.getX();
  }

  /**
   * Creates a rectangle with the supplied dimensions. This function will normalize the x, and width values, then store
   * the rectangle off using its key value as a lookup.
   *
   * @param value     value used to associate with the created rectangle..
   * @param previousX value used to determine the x position and width of the rectangle. This value should be relative to the currentX param.
   * @param currentX  value used to determine the width of the rectangle. This value should be relative to the previousX param.
   * @param minX      minimum value of the range total range used to normalize the x position and width of the rectangle.
   * @param maxX      maximum value of the range total range used to normalize the x position and width of the rectangle.
   * @param rectY     rectangle height offset from max growth of rectangle. This value is expressed as a percentage from 0-1
   * @param vGap      height offset from bottom of rectangle. This value is expressed as percentage from 0-1
   * @return
   */
  protected final Rectangle2D.Float setRectangleData(T value,
                                                     double previousX,
                                                     double currentX,
                                                     double minX,
                                                     double maxX,
                                                     float rectY,
                                                     double vGap) {
    Rectangle2D.Float rectangle = buildRectangle((previousX - minX) / (maxX - minX),
                                                 rectY,
                                                 (currentX - previousX) / (maxX - minX),
                                                 vGap);
    myRectangles.put(rectangle, value);
    return rectangle;
  }
}
