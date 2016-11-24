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
package com.android.tools.adtui;

import com.android.tools.adtui.model.Range;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * A component for performing/rendering selection.
 */
public final class SelectionComponent extends AnimatedComponent {

  public static final Color DEFAULT_SELECTION_COLOR = new JBColor(new Color(0x80CDE4F8, true), new Color(0x80CDE4F8, true));

  public static final Color DEFAULT_SELECTION_BORDER = new JBColor(0x91C4EF, 0x91C4EF);

  private static final Color DEFAULT_HANDLE = new JBColor(0x696868, 0x696868);

  public static final int HANDLE_HEIGHT = 40;

  public static final int HANDLE_WIDTH = 5;

  private int myMousePressed;
  private float myStartX;
  private float myEndX;
  private boolean myEmpty;

  private enum Mode {
    // The default mode nothing is happening
    NONE,
    // User is currently creating a selection.
    CREATE,
    // User is moving the selection.
    MOVE,
    // User is adjusting the min.
    ADJUST_MIN,
    // User is adjusting the max.
    ADJUST_MAX
  }

  private Mode myMode;

  /**
   * The range being selected.
   */
  @NotNull
  private final Range mySelectionRange;

  /**
   * The reference range.
   */
  @NotNull
  private final Range myRange;

  public SelectionComponent(@NotNull Range selectionRange, @NotNull Range globalRange) {
    myRange = globalRange;
    mySelectionRange = selectionRange;
    myMode = Mode.NONE;

    initListeners();
  }

  private void initListeners() {
    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        Dimension size = getSize();
        int x = e.getX();
        double start = size.getWidth() * myStartX;
        double end = size.getWidth() * myEndX;
        if (start - HANDLE_WIDTH < x && x < start) {
          myMode = Mode.ADJUST_MIN;
        }
        else if (end < x && x < end + HANDLE_WIDTH) {
          myMode = Mode.ADJUST_MAX;
        }
        else if (start <= x && x <= end) {
          myMode = Mode.MOVE;
        }
        else {
          double value = xToRange(x);
          mySelectionRange.setMin(value);
          mySelectionRange.setMax(value);
          myMode = Mode.CREATE;
        }
        myMousePressed = e.getX();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        myMode = Mode.NONE;
      }
    });
    this.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        Dimension size = getSize();
        int delta = e.getX() - myMousePressed;
        double rangeDelta = (myRange.getLength() / size.getWidth()) * delta;
        switch (myMode) {
          case ADJUST_MIN:
            mySelectionRange.setMin(mySelectionRange.getMin() + rangeDelta);
            myMousePressed = e.getX();
            break;
          case ADJUST_MAX:
            mySelectionRange.setMax(mySelectionRange.getMax() + rangeDelta);
            myMousePressed = e.getX();
            break;
          case MOVE:
            mySelectionRange.setMax(mySelectionRange.getMax() + rangeDelta);
            mySelectionRange.setMin(mySelectionRange.getMin() + rangeDelta);
            myMousePressed = e.getX();
            break;
          case CREATE:
            double a = xToRange(myMousePressed);
            double b = xToRange(e.getX());
            mySelectionRange.setMin(a < b ? a : b);
            mySelectionRange.setMax(a < b ? b : a);
            break;
          case NONE:
            break;
        }
      }
    });
  }

  private double xToRange(int x) {
    return x / getSize().getWidth() * myRange.getLength() + myRange.getMin();
  }

  @Override
  protected void updateData() {
    myEmpty = mySelectionRange.isEmpty();
    myStartX = (float)((mySelectionRange.getMin() - myRange.getMin()) / (myRange.getMax() - myRange.getMin()));
    myEndX = (float)((mySelectionRange.getMax() - myRange.getMin()) / (myRange.getMax() - myRange.getMin()));
  }


  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myEmpty) {
      return;
    }

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    float startXPos = (float)(myStartX * dim.getWidth());
    float endXPos = (float)(myEndX * dim.getWidth());

    g.setColor(DEFAULT_SELECTION_COLOR);
    Rectangle2D.Float rect = new Rectangle2D.Float(startXPos, 0, endXPos - startXPos, dim.height);
    g.fill(rect);

    // Draw vertical lines, one for each endsValue.
    g.setColor(DEFAULT_SELECTION_BORDER);
    Path2D.Float path = new Path2D.Float();
    path.moveTo(startXPos, 0);
    path.lineTo(startXPos, dim.height);
    path.moveTo(endXPos, dim.height);
    path.lineTo(endXPos, 0);
    g.draw(path);

    if (myMode != Mode.CREATE) {
      drawHandle(g, startXPos, dim.height, 1.0f);
      drawHandle(g, endXPos, dim.height, -1.0f);
    }
  }

  private void drawHandle(Graphics2D g, float x, float height, float direction) {
    float up = (height - HANDLE_HEIGHT) * 0.5f;
    float down = (height + HANDLE_HEIGHT) * 0.5f;
    float width = HANDLE_WIDTH * direction;

    g.setColor(DEFAULT_HANDLE);
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x, up);
    path.lineTo(x, down);
    path.quadTo(x - width, down, x - width, down - HANDLE_WIDTH);
    path.lineTo(x - width, up + HANDLE_WIDTH);
    path.quadTo(x - width, up, x, up);
    g.fill(path);
  }
}