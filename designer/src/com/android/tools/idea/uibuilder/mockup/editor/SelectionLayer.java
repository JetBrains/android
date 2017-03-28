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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Layer on top of {@link MockupViewPanel} that handles the user interactions
 * and UI for selecting an area.
 */
public class SelectionLayer extends MouseAdapter implements MockupViewLayer {

  private static final int KNOB_SIZE = 15;
  private static final int KNOB_COUNT = 9;
  private static final JBColor HOVERED_KNOB_COLOR = new JBColor(new Color(0x551955A8, true), new Color(0x554D83CD, true));
  private static final JBColor KNOB_COLOR = JBColor.BLACK;
  private static final Color KNOB_OUTLINE = JBColor.WHITE;
  private static final BasicStroke DASH = new BasicStroke(0f,
                                                          BasicStroke.CAP_BUTT,
                                                          BasicStroke.JOIN_MITER,
                                                          10.0f, new float[]{5.0f}, 0.0f);
  private static final Color SELECTION_OVERLAY_COLOR = JBColor.GRAY;

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
  private final static int MOVE = KNOB_COUNT - 1;

  private final static Cursor[] CURSORS = new Cursor[KNOB_COUNT];
  public static final float OVERLAY_ALPHA = 0.5f;

  static {
    CURSORS[N] = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
    CURSORS[E] = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
    CURSORS[S] = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
    CURSORS[W] = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
    CURSORS[NW] = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
    CURSORS[NE] = Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
    CURSORS[SE] = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
    CURSORS[SW] = Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
    CURSORS[MOVE] = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  }

  /**
   * We specify which of x,y,width or/and height of the selection each knob can modify.
   * A Knob move is composed of four numbers representing the x,y,width and height property.
   * The meanings of the number are:
   * 1 -> the property will increase by the same distance as the cursor
   * 0 -> the property wont change
   * -1 -> the property will increase by the opposite of the distance of the cursor
   */
  private static final short[][] myKnobsMoves = new short[KNOB_COUNT][];
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
  private final AffineTransform myAffineTransform;
  private final Point myConvertedMousePoint;
  private int mySelectedKnob = -1;
  private int myHoveredKnob = -1;
  private boolean myFixedRatio;
  private double myRatioWidth;
  private double myRatioHeight;
  private Rectangle myHolderRectangle = new Rectangle();

  public SelectionLayer(JPanel parent, AffineTransform affineTransform) {
    myParent = parent;
    for (int i = 0; i < myKnobs.length; i++) {
      myKnobs[i] = new Rectangle();
    }
    myAffineTransform = affineTransform;
    myConvertedMousePoint = new Point();
  }

  @Override
  public void paint(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    if (!mySelection.isEmpty() && mySelection.x >= 0 && mySelection.y >= 0) {
      drawSelection(g);
      drawSelectionOverlay(g);
      drawHoveredKnob(g);
    }
  }

  /**
   * Draw the selection rectangle
   */
  private void drawSelection(@NotNull Graphics2D g) {
    Rectangle scaledSelection = transformRect(myAffineTransform, mySelection, myHolderRectangle);
    g.setColor(KNOB_COLOR);
    g.draw(scaledSelection);
    g.setColor(KNOB_OUTLINE);
    g.setStroke(DASH);
    g.draw(scaledSelection);
  }

  /**
   * Draw the hovered know if one of the know is hovered
   * @param g the graphics context
   */
  private void drawHoveredKnob(@NotNull Graphics2D g) {
    Composite composite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    if (myHoveredKnob >= 0 && myHoveredKnob != MOVE) {
      g.setColor(HOVERED_KNOB_COLOR);
      g.fill(transformRect(myAffineTransform, myKnobs[myHoveredKnob], myHolderRectangle));
      g.setColor(KNOB_OUTLINE);
      g.draw(transformRect(myAffineTransform, myKnobs[myHoveredKnob], myHolderRectangle));
    }
    g.setComposite(composite);
  }

  /**
   * Draw an overlay around the selection
   * @param g the graphics context
   */
  private void drawSelectionOverlay(@NotNull Graphics2D g) {
    Composite composite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OVERLAY_ALPHA));
    g.setColor(SELECTION_OVERLAY_COLOR);

    // Top
    myHolderRectangle.setBounds(myBounds.x, myBounds.y, myBounds.width, mySelection.y);
    g.fill(transformRect(myAffineTransform, myHolderRectangle, myHolderRectangle));

    // Left
    myHolderRectangle.setBounds(myBounds.x, mySelection.y, mySelection.x, mySelection.height);
    transformRect(myAffineTransform, myHolderRectangle, myHolderRectangle);
    myHolderRectangle.translate(0, 1);
    g.fill(myHolderRectangle);

    // Right
    int x = mySelection.x + mySelection.width;
    myHolderRectangle.setBounds(x, mySelection.y, myBounds.width - x, mySelection.height);
    transformRect(myAffineTransform, myHolderRectangle, myHolderRectangle);
    myHolderRectangle.translate(1, 1);
    myHolderRectangle.grow(-1, 0);
    g.fill(myHolderRectangle);

    // Bottom
    int y = mySelection.y + mySelection.height;
    myHolderRectangle.setBounds(0, y, myBounds.width, myBounds.height - y - 1);
    transformRect(myAffineTransform, myHolderRectangle, myHolderRectangle);
    myHolderRectangle.translate(0, 2);
    g.fill(myHolderRectangle);

    g.setComposite(composite);
  }


  /**
   * Scale the source Rectangle using the provided affine transform and set the converted
   * bounds to the destination rectangle. The source and destination rectangle can be the same.
   *
   * @param transform The transform used to transform the rectangle
   * @param src       The rectangle that will be transformed
   * @param dst       The resulting transformed rectangle
   * @return the destination rectangle
   */
  private static Rectangle transformRect(@NotNull AffineTransform transform, @NotNull Rectangle src, @NotNull Rectangle dst) {
    final double[] location = {src.x, src.y, src.x + src.width, src.y + src.height};
    transform.transform(location, 0, location, 0, 2);
    double x1 = location[0];
    double y1 = location[1];
    double x2 = location[2];
    double y2 = location[3];
    dst.setRect(x1, y1, x2 - x1, y2 - y1);
    return dst;
  }

  public void contentResized() {
    updateKnobPosition();
  }

  /**
   * Set the knobs position regarding the values of the current selection
   */
  private void updateKnobPosition() {
    int convertedKnobSize = (int)Math.round(KNOB_SIZE / myAffineTransform.getScaleX());

    final int x1, y1, x2, y2, hSize, vSize;
    if (mySelection.height < convertedKnobSize * 3 || mySelection.width < convertedKnobSize * 3) {
      x1 = mySelection.x - convertedKnobSize;
      y1 = mySelection.y - convertedKnobSize;
      x2 = mySelection.x + mySelection.width;
      y2 = mySelection.y + mySelection.height;
      hSize = mySelection.width;
      vSize = mySelection.height;
      myKnobs[MOVE].setBounds(mySelection);
    }
    else {
      x1 = mySelection.x;
      y1 = mySelection.y;
      x2 = x1 + mySelection.width - convertedKnobSize;
      y2 = y1 + mySelection.height - convertedKnobSize;
      hSize = mySelection.width - convertedKnobSize * 2;
      vSize = mySelection.height - convertedKnobSize * 2;
      myKnobs[MOVE].setBounds(x1 + convertedKnobSize, y1 + convertedKnobSize, hSize, vSize);
    }

    myKnobs[NW].setBounds(x1, y1, convertedKnobSize, convertedKnobSize);
    myKnobs[NE].setBounds(x2, y1, convertedKnobSize, convertedKnobSize);
    myKnobs[SE].setBounds(x2, y2, convertedKnobSize, convertedKnobSize);
    myKnobs[SW].setBounds(x1, y2, convertedKnobSize, convertedKnobSize);

    myKnobs[N].setBounds((x1 + convertedKnobSize), y1, hSize, convertedKnobSize);
    myKnobs[E].setBounds(x2, y1 + convertedKnobSize, convertedKnobSize, vSize);
    myKnobs[S].setBounds(x1 + convertedKnobSize, y2, hSize, convertedKnobSize);
    myKnobs[W].setBounds(x1, y1 + convertedKnobSize, convertedKnobSize, vSize);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    try {
      myAffineTransform.inverseTransform(e.getPoint(), myClickOrigin);
      myClickOrigin.x = Math.max(myBounds.x, Math.min(myBounds.x + myBounds.width, myClickOrigin.x));
      myClickOrigin.y = Math.max(myBounds.y, Math.min(myBounds.y + myBounds.height, myClickOrigin.y));
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
    }
    catch (NoninvertibleTransformException e1) {
      Logger.getInstance(SelectionLayer.class).warn(e1);
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    resetSelectionLocation();
  }

  /**
   * Reset the location of the selection to the location of the bounds if it is empty
   */
  private void resetSelectionLocation() {
    if (mySelection.isEmpty()) {
      mySelection.setLocation(myBounds.x, myBounds.y);
      updateKnobPosition();
    }
    mySelectedKnob = -1;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (mySelectedKnob < 0) {
      // happens if the user began to do a PanAction in the MockupViewPanel
      // then release the shift key and continue to drag
      if (myHoveredKnob < 0) {
        return;
      }
      mySelectedKnob = myHoveredKnob;
    }
    try {
      myAffineTransform.inverseTransform(e.getPoint(), myConvertedMousePoint);
      // Compute the drag distance
      final int dx = myConvertedMousePoint.x - myClickOrigin.x;
      final int dy = myConvertedMousePoint.y - myClickOrigin.y;

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
    catch (NoninvertibleTransformException e1) {
      Logger.getInstance(SelectionLayer.class).warn(e1);
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    try {
      myAffineTransform.inverseTransform(e.getPoint(), myConvertedMousePoint);
      int hovered = -1;
      if (!mySelection.isEmpty()) {
        for (int i = 0; i < myKnobs.length; i++) {
          if (myKnobs[i].contains(myConvertedMousePoint)) {
            hovered = i;
          }
        }
      }

      // Repaint only if the hovered change changed
      if (hovered != myHoveredKnob) {
        myHoveredKnob = hovered;
        if (hovered >= 0) {
          myParent.setCursor(CURSORS[hovered]);
        }
        else {
          myParent.setCursor(Cursor.getDefaultCursor());
        }
        myParent.repaint();
      }
    }
    catch (NoninvertibleTransformException e1) {
      Logger.getInstance(SelectionLayer.class).warn(e1);
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
    x = Math.min(myBounds.width, Math.max(0, x));
    y = Math.min(myBounds.height, Math.max(0, y));
    width = Math.min(myBounds.width - x, Math.max(0, width));
    height = Math.min(myBounds.height - y, Math.max(0, height));
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
    resetSelectionLocation();
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
