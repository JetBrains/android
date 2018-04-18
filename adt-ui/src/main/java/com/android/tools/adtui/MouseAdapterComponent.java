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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Class to build and manipulate rectangles to be drawn in the profiler's UI. This class is not responsible
 * for drawing the rectangles, but only creating, and determining if the mouse is over a rectangle.
 * All dimensions used in this class are represented as a percentage. It is for the child class to scale the
 * rectangles to the dimensions needed for the control. The most common use case is scaling the rectangles by
 * the components width and height in the draw function.
 */
public abstract class MouseAdapterComponent extends AnimatedComponent implements MouseListener, MouseMotionListener {
  public static final double INVALID_MOUSE_POSITION = -Double.MAX_VALUE;
  private double myMouseX = INVALID_MOUSE_POSITION;

  /**
   * Default constructor for a MouseAdapterComponent, the constructor attaches mouse listeners.
   */
  public MouseAdapterComponent() {
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
    setMousePointAndForwardEvent(INVALID_MOUSE_POSITION, e);
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
   * @param x mouse x position to be normalized for determining mouse over.
   * @param e mouse event to be passed on to parent items.
   */
  private void setMousePointAndForwardEvent(double x, MouseEvent e) {
    // TODO (b/74547254): Revisit mouse over animation to be based on range instead of mouse coordinates.
    myMouseX = x;
    // Parent can be null in test.
    if (getParent() != null) {
      getParent().dispatchEvent(e);
    }
    opaqueRepaint();
  }

  public double getMouseX() {
    return myMouseX;
  }
}
