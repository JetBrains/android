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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.animation.Animation;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Draws an Anchor
 */
public class DrawAnchor extends DrawRegion {

  public enum Type {
    NORMAL,
    BASELINE
  }

  public enum Mode {
    NORMAL,
    OVER,
    /**
     * used during connection to say you CAN connect to me
     */
    CAN_CONNECT,
    /**
     * used during connection to say you CANNOT connect to me
     */
    CANNOT_CONNECT,
    DO_NOT_DRAW
  }

  private final Mode myMode;
  private final boolean myIsConnected;
  private final Type myType;

  public DrawAnchor(@SwingCoordinate int x,
                    @SwingCoordinate int y,
                    @SwingCoordinate int width,
                    @SwingCoordinate int height,
                    Type type,
                    boolean isConnected,
                    Mode mode) {
    super(x, y, width, height);
    myMode = mode;
    myIsConnected = isConnected;
    myType = type;
  }

  private static int getPulseAlpha(int deltaT) {
    return (int)Animation.EaseInOutinterpolator(Math.abs((deltaT) - 500) / 500.0, 0, 255);
  }

  @Override
  public int getLevel() {
    if (myMode == Mode.OVER) {
      return TARGET_OVER_LEVEL;
    }
    return TARGET_LEVEL;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    if (myType == Type.BASELINE) {
      paintBaseline(g, sceneContext);
      return;
    }

    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getComponentObligatoryBackground();
    Color color = colorSet.getSelectedFrames();

    if (myMode == Mode.OVER) {
      if (myIsConnected) {
        g.setColor(colorSet.getAnchorDisconnectionCircle());
      }
      else {
        g.setColor(colorSet.getAnchorConnectionCircle());
      }
      int delta = width / 3;
      int delta2 = delta * 2;
      g.fillRoundRect(x - delta, y - delta, width + delta2, height + delta2, width + delta2, height + delta2);
      g.drawRoundRect(x - delta, y - delta, width + delta2, height + delta2, width + delta2, height + delta2);
    }

    g.setColor(background);
    g.fillRoundRect(x, y, width, height, width, height);
    g.setColor(color);
    g.drawRoundRect(x, y, width, height, width, height);
    int delta = width / 4;
    int delta2 = delta * 2;
    if (myIsConnected) {
      g.fillRoundRect(x + delta, y + delta, width - delta2, height - delta2, width - delta2, height - delta2);
      g.drawRoundRect(x + delta, y + delta, width - delta2, height - delta2, width - delta2, height - delta2);
    }

    if (myMode == Mode.CAN_CONNECT) {
      int alpha = getPulseAlpha((int)(sceneContext.getTime() % 1000));
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
      g.setColor(colorSet.getAnchorConnectionCircle().darker());
      g.fillRoundRect(x, y, width, height, width, height);
      sceneContext.repaint();
      g.setComposite(comp);
    }
    if (myMode == Mode.CANNOT_CONNECT) {
      int alpha = getPulseAlpha((int)(sceneContext.getTime() % 1000));
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
      g.setColor(colorSet.getAnchorDisconnectionCircle());
      g.fillRoundRect(x, y, width, height, width, height);
      sceneContext.repaint();
      g.setComposite(comp);
    }

    if (myMode == Mode.OVER) {
      int alpha = getPulseAlpha((int)(sceneContext.getTime() % 1000));
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
      if (myIsConnected) {
        g.setColor(colorSet.getAnchorDisconnectionCircle());
      }
      else {
        g.setColor(colorSet.getAnchorConnectionCircle());
      }
      g.fillRoundRect(x, y, width, height, width, height);
      sceneContext.repaint();
      g.setComposite(comp);
    }
  }

  public void paintBaseline(Graphics2D g, SceneContext sceneContext) {
    int inset = width / 10;
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getComponentObligatoryBackground();
    Color color = colorSet.getFrames();
    g.setColor(color);
    g.fillRect(x, y + height / 2, width, 1);
    int ovalX = x + inset;
    int ovalW = width - 2 * inset;
    g.setColor(background);
    g.fillRoundRect(ovalX, y, ovalW, height, height, height);
    g.setColor(color);
    g.drawRoundRect(ovalX, y, ovalW, height, height, height);
    int delta = 3;
    int delta2 = delta * 2;
    if (myIsConnected) {
      g.fillRoundRect(ovalX + delta, y + delta, ovalW - delta2, height - delta2, height - delta2, height - delta2);
      g.drawRoundRect(ovalX + delta, y + delta, ovalW - delta2, height - delta2, height - delta2, height - delta2);
    }
    if (myMode == Mode.CAN_CONNECT) {
      int alpha = getPulseAlpha((int)(sceneContext.getTime() % 1000));
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
      g.setColor(colorSet.getAnchorConnectionCircle());
      g.fillRoundRect(ovalX, y, ovalW, height, height, height);
      g.drawRoundRect(ovalX, y, ovalW, height, height, height);
      sceneContext.repaint();
      g.setComposite(comp);
    }
    if (myMode == Mode.OVER) {
      int alpha = getPulseAlpha((int)(sceneContext.getTime() % 1000));
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
      if (myIsConnected) {
        g.setColor(colorSet.getAnchorDisconnectionCircle());
      }
      else {
        g.setColor(colorSet.getAnchorConnectionCircle());
      }
      g.fillRoundRect(ovalX, y, ovalW, height, height, height);
      g.drawRoundRect(ovalX, y, ovalW, height, height, height);
      sceneContext.repaint();
      g.setComposite(comp);
    }
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName() + "," + x + "," + y + "," + width + "," + height + "," + myMode.ordinal();
  }

  public static void add(@NotNull DisplayList list,
                         @NotNull SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         Type type,
                         boolean isConnected,
                         Mode mode) {
    @SwingCoordinate int l = transform.getSwingXDip(left);
    @SwingCoordinate int t = transform.getSwingYDip(top);
    @SwingCoordinate int w = transform.getSwingDimensionDip(right - left);
    @SwingCoordinate int h = transform.getSwingDimensionDip(bottom - top);
    list.add(new DrawAnchor(l, t, w, h, type, isConnected, mode));
  }
}
