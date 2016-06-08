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
package com.android.tools.idea.uibuilder.mockup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;

public class ResizeableRectangle extends Rectangle {
// TODO remove ?
  static final int HIT_SPACE = 5;

  @NotNull private Rectangle myHitBox;
  @NotNull private Border[] myBorders;
  @NotNull private MouseInteraction myMouseInteraction;

  public ResizeableRectangle() {
    this(0,0,0,0);
  }

  public ResizeableRectangle(Rectangle rectangle) {
    this(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
  }

  public ResizeableRectangle(int x, int y, int width, int height) {
    super(x, y, width, height);

    myMouseInteraction = new MouseInteraction();
    myBorders = new Border[]{
      new Border(this, Border.Position.N),
      new Border(this, Border.Position.S),
      new Border(this, Border.Position.E),
      new Border(this, Border.Position.W)
    };
    myHitBox = new Rectangle();
    updateBorders();
    updateHitbox();
  }

  public Rectangle2D getHitBox() {
    return myHitBox;
  }

  @Nullable
  public Border getBorderAtCoordinate(double x, double y) {
    for (int i = 0; i < myBorders.length; i++) {
      if (myBorders[i].isInBounds(x, y)) {
        return myBorders[i];
      }
    }
    return null;
  }

  @NotNull
  public Border[] getBorders() {
    return myBorders;
  }

  @NotNull
  public MouseInteraction getMouseInteraction() {
    return myMouseInteraction;
  }

  @Override
  public void setRect(Rectangle2D r) {
    super.setRect(r);
    updateBorders();
    updateHitbox();
  }

  private void updateHitbox() {
    myHitBox.setBounds(x - HIT_SPACE,
                       y - HIT_SPACE,
                       width + HIT_SPACE,
                       height + HIT_SPACE);
  }

  private void updateBorders() {
    for (int i = 0; i < myBorders.length; i++) {
      myBorders[i].setBounds(this);
    }
  }

  @Override
  public void setLocation(int x, int y) {
    super.setLocation(x, y);
    updateBorders();
    updateHitbox();
  }

  @Override
  public void setSize(int width, int height) {
    super.setSize(width, height);
    updateBorders();
    updateHitbox();
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    updateBorders();
    updateHitbox();
  }

  static class Border {

    public Rectangle getRectangle() {
      return myBorderBox;
    }

    enum Position {
      N, E, S, W
    }

    private final Position myPosition;
    @NotNull private final Rectangle myBorderBox;

    public Border(@NotNull Rectangle2D rectangle, @NotNull Position position) {
      myBorderBox = new Rectangle();
      myPosition = position;
      setBounds(rectangle);
    }

    private void setBounds(Rectangle2D rectangle) {
      switch (myPosition) {
        case N:
          myBorderBox.setRect(rectangle.getX() + HIT_SPACE, rectangle.getY() - HIT_SPACE,
                              rectangle.getWidth() - HIT_SPACE * 2, HIT_SPACE * 2);
          break;
        case S:
          myBorderBox.setRect(rectangle.getX() + HIT_SPACE, rectangle.getY() + rectangle.getHeight() - HIT_SPACE,
                              rectangle.getWidth() - HIT_SPACE * 2, HIT_SPACE * 2);
          break;
        case W:
          myBorderBox.setRect(rectangle.getX() - HIT_SPACE, rectangle.getY() + HIT_SPACE,
                              HIT_SPACE * 2, rectangle.getHeight() - HIT_SPACE * 2);
          break;
        case E:
          myBorderBox.setRect(rectangle.getX() + rectangle.getWidth() - HIT_SPACE, rectangle.getY() + HIT_SPACE,
                              HIT_SPACE * 2, rectangle.getHeight() - HIT_SPACE * 2);
          break;
      }
    }

    public boolean isInBounds(double x, double y) {
      return myBorderBox.contains(x, y);
    }

    public Position getPosition() {
      return myPosition;
    }
  }

  private class MouseInteraction implements MouseListener, MouseMotionListener {

    @Override
    public void mouseClicked(MouseEvent e) {
      // If on border activate movement for border
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      // Resize the rectangle accordingly
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }
  }
}
