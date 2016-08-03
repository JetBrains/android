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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Contract;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Layer on top of {@link MockupViewPanel} that handles the user interactions
 * and UI for selecting an area.
 */
public class SelectionLayer extends MouseAdapter implements MockupViewLayer {

  private static final int KNOB_SIZE = 8;
  private static final int KNOB_COUNT = 8;
  private static final JBColor HOVERED_KNOB_COLOR = new JBColor(new Color(0x1955A8), new Color(0x4D83CD));
  private static final JBColor KNOB_COLOR = JBColor.BLACK;
  private static final Color KNOB_OUTLINE = JBColor.WHITE;
  private static final BasicStroke DASH = new BasicStroke(1.0f,
                                                          BasicStroke.CAP_BUTT,
                                                          BasicStroke.JOIN_MITER,
                                                          10.0f, new float[]{5.0f}, 0.0f);

  // Index of the Knobs in myKnobs
  // depending the position
  private final static int N = 0;
  private final static int E = 2;
  private final static int S = 4;
  private final static int W = 6;
  private final static int NW = 1;
  private final static int NE = 3;
  private final static int SE = 5;
  private final static int SW = 7;
  private final static int MOVE = KNOB_COUNT;

  /**
   * We specify which of x,y,width or/and height of the selection each knob can modify.
   * A Knob move is composed of four numbers representing the x,y,width and height property.
   * The meanings of the number are:
   * 1 -> the property will increase by the same distance as the cursor
   * 0 -> the property wont change
   * -1 -> the property will increase by the opposite of the distance of the cursor
   */
  private static final short[][] myKnobsMoves = new short[KNOB_COUNT + 1][];
  private static final int X_MOVE = 0;
  private static final int Y_MOVE = 1;
  private static final int W_MOVE = 2;
  private static final int H_MOVE = 3;

  static {                       // X, Y, W, H
    myKnobsMoves[NW] = new short[]{1, 1, -1, -1};
    myKnobsMoves[N] = new short[]{0, 1, 0, -1};
    myKnobsMoves[NE] = new short[]{0, 1, 1, -1};
    myKnobsMoves[E] = new short[]{0, 0, 1, 0};
    myKnobsMoves[SE] = new short[]{0, 0, 1, 1};
    myKnobsMoves[S] = new short[]{0, 0, 0, 1};
    myKnobsMoves[SW] = new short[]{1, 0, -1, 1};
    myKnobsMoves[W] = new short[]{1, 0, -1, 0};
    myKnobsMoves[MOVE] = new short[]{1, 1, 0, 0};
  }

  private final Rectangle mySelection = new Rectangle();
  private final Rectangle myBounds = new Rectangle();
  private final Rectangle[] myKnobs = new Rectangle[KNOB_COUNT];
  private final Rectangle myOriginalSelection = new Rectangle();
  private final Point myClickOrigin = new Point();
  private final JPanel myParent;
  private int mySelectedKnob = -1;
  private int myHoveredKnob = -1;
  private boolean myFixedRatio;
  private double myRatioWidth;
  private double myRatioHeight;

  public SelectionLayer(JPanel parent) {
    myParent = parent;
    for (int i = 0; i < myKnobs.length; i++) {
      myKnobs[i] = new Rectangle(KNOB_SIZE, KNOB_SIZE);
    }
  }

  @Override
  public void paint(Graphics2D g) {
    if (!mySelection.isEmpty() && mySelection.x >= 0 && mySelection.y >= 0) {
      drawSelection(g);
    }
  }

  private void drawSelection(Graphics2D g) {
    g.setColor(KNOB_COLOR);
    g.draw(mySelection);
    g.setColor(KNOB_OUTLINE);
    final Stroke oldStroke = g.getStroke();
    g.setStroke(DASH);
    g.draw(mySelection);
    g.setStroke(oldStroke);
    for (int i = 0; i < myKnobs.length; i++) {
      Rectangle knob = myKnobs[i];
      g.setColor(KNOB_COLOR);
      g.fill(knob);
      g.setColor(KNOB_OUTLINE);
      g.draw(knob);
    }
    if (myHoveredKnob >= 0) {
      g.setColor(HOVERED_KNOB_COLOR);
      g.fill(myKnobs[myHoveredKnob]);
      g.setColor(KNOB_OUTLINE);
      g.draw(myKnobs[myHoveredKnob]);
    }
  }

  /**
   * Set the knobs position regarding the values of the current selection
   */
  private void updateKnobPosition() {
    final int x1 = mySelection.x - KNOB_SIZE / 2;
    final int y1 = mySelection.y - KNOB_SIZE / 2;
    final int x2 = x1 + mySelection.width;
    final int y2 = y1 + mySelection.height;

    myKnobs[NW].setLocation(x1, y1);
    myKnobs[NE].setLocation(x2, y1);
    myKnobs[SE].setLocation(x2, y2);
    myKnobs[SW].setLocation(x1, y2);

    myKnobs[N].setLocation((x2 + x1) / 2, y1);
    myKnobs[E].setLocation(x2, (y2 + y1) / 2);
    myKnobs[S].setLocation((x2 + x1) / 2, y2);
    myKnobs[W].setLocation(x1, (y2 + y1) / 2);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    myClickOrigin.x = Math.max(myBounds.x, Math.min(myBounds.x + myBounds.width, e.getX()));
    myClickOrigin.y = Math.max(myBounds.y, Math.min(myBounds.y + myBounds.height, e.getY()));
    myOriginalSelection.setBounds(mySelection);

    // Check if the click occurred on one of the knobs
    if (!mySelection.isEmpty()) {
      for (int i = 0; i < myKnobs.length; i++) {
        if (myKnobs[i].contains(myClickOrigin)) {
          mySelectedKnob = i;
          return;
        }
      }
    }
    else {
      myOriginalSelection.setLocation(myClickOrigin);
    }

    if (!mySelection.contains(myClickOrigin)) {
      // If the click is outside the selection, delete the selection
      // and create a new one
      mySelection.setSize(0, 0);
      mySelection.setLocation(myClickOrigin);
      Rectangle2D.intersect(myBounds, mySelection, mySelection);
      myOriginalSelection.setBounds(mySelection);
      mySelectedKnob = SE;
      updateKnobPosition();
    }
    else {
      // Clicked inside current selection make it move
      mySelectedKnob = MOVE;
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    mySelectedKnob = -1;
  }

  @Override
  public void mouseDragged(MouseEvent e) {

    // Clamp the mouse coordinate inside the bounds
    final int x = Math.min(myBounds.x + myBounds.width, Math.max(myBounds.x, e.getX()));
    final int y = Math.min(myBounds.y + myBounds.height, Math.max(myBounds.y, e.getY()));

    // Compute the drag distance
    int dx = x - myClickOrigin.x;
    int dy = y - myClickOrigin.y;

    // Compute the new coordinate of the selection
    int nx = myOriginalSelection.x + dx * myKnobsMoves[mySelectedKnob][X_MOVE];
    int ny = myOriginalSelection.y + dy * myKnobsMoves[mySelectedKnob][Y_MOVE];
    int nw = myOriginalSelection.width + dx * myKnobsMoves[mySelectedKnob][W_MOVE];
    int nh = myOriginalSelection.height + dy * myKnobsMoves[mySelectedKnob][H_MOVE];

    if (myFixedRatio) {
      nh = (int)Math.round(nw * (myRatioHeight / myRatioWidth));
    }

    // Invert the selection direction if the mouse go behind
    // the selection rectangle location
    if (nw < 0) {
      nx += nw;
      nw = -nw;
    }
    if (nh < 0) {
      ny += nh;
      nh = -nh;
    }

    // Ensure that the selection does not go outside the bounds
    if (mySelectedKnob == MOVE) {
      mySelection.setLocation(
        Math.min(Math.max(myBounds.x, nx), myBounds.x + myBounds.width - nw),
        Math.min(Math.max(myBounds.y, ny), myBounds.y + myBounds.height - nh)
      );
    }
    else {
      if (!myFixedRatio || myBounds.contains(nx, ny, nw, nh)) {
        mySelection.setBounds(nx, ny, nw, nh);
      }
      if (!myFixedRatio) {
        Rectangle2D.intersect(myBounds, mySelection, mySelection);
      }
    }
    updateKnobPosition();
    myParent.repaint();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    int hovered = -1;
    if (!mySelection.isEmpty()) {
      for (int i = 0; i < myKnobs.length; i++) {
        if (myKnobs[i].contains(e.getPoint())) {
          hovered = i;
        }
      }

      // Repaint only if the hovered change changed
      if (hovered != myHoveredKnob) {
        if (myHoveredKnob >= 0) {
          myParent.repaint(myKnobs[myHoveredKnob]);
        }
        myHoveredKnob = hovered;
        if (myHoveredKnob >= 0) {
          myParent.repaint(myKnobs[myHoveredKnob]);
        }
      }
    }
  }

  /**
   * Set the bounds where the selection can be made in this layer
   *
   * @param x x coordinate of the bounding rectangle
   * @param y y coordinate of the bounding rectangle
   * @param w width the bounding rectangle
   * @param h height the bounding rectangle
   */
  public void setBounds(int x, int y, int w, int h) {
    myBounds.setBounds(x, y, w, h);
  }

  /**
   * Get the bounds where the selection can be made in this layer
   *
   * @return the bounds where the selection can be made in this layer
   */
  public Rectangle getBounds() {
    return myBounds;
  }

  /**
   * Set the current selection.
   * Does not ensure that the selection is inside the selectable bounds
   *
   * @param x
   * @param y
   * @param width
   * @param height
   * @see #getBounds()
   */
  public void setSelection(int x, int y, int width, int height) {
    mySelection.setBounds(x, y, width, height);
    updateKnobPosition();
    myParent.repaint();
  }

  /**
   * Set the current selection.
   * Does not ensure that the selection is inside the selectable bounds
   *
   * @param selection the rectangle that the selection should match
   */
  public void setSelection(Rectangle selection) {
    setSelection(selection.x, selection.y, selection.width, selection.height);
  }

  /**
   * Get the current selection
   */
  public Rectangle getSelection() {
    return mySelection;
  }

  /**
   * Clear the selection by setting it to 0,0,0,0
   *
   * @see #setSelection(int, int, int, int)
   */
  public void clearSelection() {
    setSelection(0, 0, 0, 0);
  }

  /**
   * Returns true if the selection aspect ratio is fixed
   *
   * @return true if the selection aspect ratio is fixed
   * @see #setAspectRatio(double, double)
   */
  public boolean isFixedRatio() {
    return myFixedRatio;
  }

  /**
   * If fixedRatio is true, the selection's aspect ratio will
   * be fixed to the current selection aspect ratio
   *
   * @param fixedRatio
   */
  public void setFixedRatio(boolean fixedRatio) {
    myFixedRatio = fixedRatio;
    myRatioHeight = mySelection.height;
    myRatioWidth = mySelection.width;
  }

  /**
   * Resize the selection to respect a aspect ratio of width/height.
   * Ensure that the new size will be the closest to the old one
   *
   * @param width  width used to compute the aspect ratio
   * @param height height used to compute the aspect ratio
   */
  public void setAspectRatio(double width, double height) {
    myRatioWidth = width;
    myRatioHeight = height;
    if (mySelection.getWidth() / mySelection.getHeight() == width / height) {
      // Already good aspect ratio
      return;
    }
    final Point2D.Double point = getClosestPointFromLine(myRatioHeight, -myRatioWidth,
                                                         mySelection.width,
                                                         mySelection.height);
    mySelection.width = (int)Math.round(point.x);
    mySelection.height = (int)Math.round(point.y);

    // If the resized selection is over the bounds, replace it inside the bounds
    mySelection.x -= Math.max(0, mySelection.x + mySelection.width - myBounds.x - myBounds.width);
    mySelection.y -= Math.max(0, mySelection.y + mySelection.height - myBounds.y - myBounds.height);

    // If the resized selection location is outside the bounds, replace it inside
    if (mySelection.x < myBounds.x) {
      mySelection.x = myBounds.x;
      mySelection.width = myBounds.width;
      mySelection.height = (int)Math.round(mySelection.width * height / width);
    }
    if (mySelection.y < myBounds.y) {
      mySelection.y = myBounds.y;
      mySelection.height = myBounds.height;
      mySelection.width = (int)Math.round(mySelection.height * width / height);
    }
    updateKnobPosition();
    myParent.repaint();
  }

  /**
   * Find the closest point to the origin (x0, y0) on a line defined by the parametric equation 0 = ax + by
   *
   * @param a  a parameter of the line
   * @param b  b parameter of the line
   * @param x0 x coordinate of the origin
   * @param y0 y coordinate of the origin
   * @return the closest point to (x0, y0) on the line
   */
  @Contract("_, _, _, _ -> !null")
  private static Point2D.Double getClosestPointFromLine(double a, double b, double x0, double y0) {
    double x = (b * (b * x0 - a * y0)) / (a * a + b * b);
    double y = (a * (-b * x0 + a * y0)) / (a * a + b * b);
    return new Point2D.Double(x, y);
  }
}
