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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import java.awt.Color;
import java.awt.Graphics2D;
import org.jetbrains.annotations.NotNull;

/**
 * Draws an Anchor
 */
public class DrawAnchor extends DrawRegion {
  // Percent values relative to the size of the anchor.
  private static final int PAINT_BACKGROUND_SIZE_PERCENT = 20;
  private static final int PAINT_HOVER_SIZE_PERCENT = 40;
  private static final int PAINT_INNER_CIRCLE_SIZE_PERCENT = 10;
  private static final int PAINT_DELETE_ICON_SIZE_PERCENT = 40;
  private static final int PAINT_BASELINE_HOVER_SIZE_PERCENT = 40;
  private static final int PAINT_BASELINE_FILL_SIZE_PERCENT = 40;

  public enum Type {
    NORMAL,
    BASELINE
  }

  public enum Mode {
    NORMAL,
    OVER,
    DELETE,
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

  @Override
  public int getLevel() {
    if (myMode == Mode.OVER || myMode == Mode.DELETE) {
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
    boolean willDelete = myMode == Mode.DELETE;
    Color color = willDelete ? colorSet.getAnchorDisconnectionCircle() : colorSet.getSelectedFrames();

    if (myMode == Mode.OVER || willDelete) {
      // Draw a ring around the anchor Should go a bit over the white background.
      int overRingOffset = width * PAINT_HOVER_SIZE_PERCENT / 100;
      int overRingWidth = width + (overRingOffset * 2);
      g.setColor(color);
      g.fillRoundRect(x - overRingOffset, y - overRingOffset, overRingWidth, overRingWidth, overRingWidth, overRingWidth);
    }

    // The background of the anchor. Goes 20% extra width over the actual size (~2dp).
    g.setColor(background);
    int whiteSpaceOffset = width * PAINT_BACKGROUND_SIZE_PERCENT / 100;
    int whiteSpaceWidth = width + (whiteSpaceOffset * 2);
    g.fillRoundRect(x - whiteSpaceOffset, y - whiteSpaceOffset, whiteSpaceWidth, whiteSpaceWidth, whiteSpaceWidth, whiteSpaceWidth);

    // The fill circle of an anchor.
    g.setColor(color);
    g.fillRoundRect(x, y, width, width, width, width);
    if (willDelete) {
      g.setColor(background);
      paintDeleteConstraintIcon(g);
    }

    if (!myIsConnected) {
      // Add a "hole" for non-connected anchors.
      int innerCircleOffset = width * PAINT_INNER_CIRCLE_SIZE_PERCENT / 100;
      int innerCircleWidth = width - (innerCircleOffset * 2);
      g.setColor(background);
      g.fillRoundRect(x + innerCircleOffset, y + innerCircleOffset, innerCircleWidth, innerCircleWidth, innerCircleWidth, innerCircleWidth);
    }
  }

  /** Paints a little "x" over the anchor. Used when the constraint is about to be deleted. */
  public void paintDeleteConstraintIcon(Graphics2D g) {
    int iconSizeOffset = width * PAINT_DELETE_ICON_SIZE_PERCENT / 100;
    int iconX = x + iconSizeOffset;
    int iconY = y + iconSizeOffset;
    int iconSize = width - 1 - iconSizeOffset;
    g.drawLine(iconX, iconY, x + iconSize, y + iconSize);
    g.drawLine(iconX, y + iconSize, x + iconSize, iconY);
  }

  public void paintBaseline(Graphics2D g, SceneContext sceneContext) {
    int inset = width / 10;
    boolean willDelete = myMode == Mode.DELETE;
    boolean drawAsHover = myMode == Mode.OVER;
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getComponentObligatoryBackground();
    Color color = willDelete ? colorSet.getAnchorDisconnectionCircle() : colorSet.getSelectedFrames();

    g.setColor(color);
    g.fillRect(x, y + height / 2, width, 1);

    int ovalX = x + inset;
    int ovalY = y;
    int ovalW = width - 2 * inset;
    int ovalH = height;

    int backgroundX = ovalX;
    int backgroundY = ovalY;
    int backgroundW = ovalW;
    int backgroundH = height;
    if (drawAsHover) {
      // Increase size of background when hovering over anchor.
      int baselineOffset = height * PAINT_BASELINE_HOVER_SIZE_PERCENT / 100;
      backgroundX -= baselineOffset;
      backgroundY -= baselineOffset;
      backgroundW += baselineOffset * 2;
      backgroundH += baselineOffset * 2;
    }
    // Paint background and outline.
    g.setColor(background);
    g.fillRoundRect(backgroundX, backgroundY, backgroundW, backgroundH, backgroundH, backgroundH);
    g.setColor(color);
    g.drawRoundRect(backgroundX, backgroundY, backgroundW, backgroundH, backgroundH, backgroundH);

    if (drawAsHover) {
      // Paint additional outline.
      g.drawRoundRect(ovalX, ovalY, ovalW, height, height, height);
    }
    int delta = height * PAINT_BASELINE_FILL_SIZE_PERCENT / 100;
    int delta2 = delta * 2;
    if (myIsConnected) {
      // Fill when anchor is connected.
      g.fillRoundRect(ovalX + delta, ovalY + delta, ovalW - delta2, ovalH - delta2, ovalH - delta2, ovalH - delta2);
      g.drawRoundRect(ovalX + delta, ovalY + delta, ovalW - delta2, ovalH - delta2, ovalH - delta2, ovalH - delta2);
    }
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName() + "," + x + "," + y + "," + width + "," + height + "," + myMode.ordinal();
  }

  public static void add(@NotNull DisplayList list,
                         @NotNull SceneContext transform,
                         @AndroidDpCoordinate float x,
                         @AndroidDpCoordinate float y,
                         Type type,
                         boolean isConnected,
                         Mode mode) {
    assert type != Type.BASELINE;
    @SwingCoordinate int swingX = transform.getSwingXDip(x);
    @SwingCoordinate int swingY = transform.getSwingYDip(y);

    int l = swingX - AnchorTarget.ANCHOR_SIZE;
    int t = swingY - AnchorTarget.ANCHOR_SIZE;
    list.add(new DrawAnchor(l, t, AnchorTarget.ANCHOR_SIZE * 2, AnchorTarget.ANCHOR_SIZE * 2, type, isConnected, mode));
  }

  public static void addBaseline(@NotNull DisplayList list,
                                 @NotNull SceneContext transform,
                                 @AndroidDpCoordinate float x,
                                 @AndroidDpCoordinate float y,
                                 @AndroidDpCoordinate float componentWidth,
                                 Type type,
                                 boolean isConnected,
                                 Mode mode) {
    assert type == Type.BASELINE;
    @SwingCoordinate int swingX = transform.getSwingXDip(x);
    @SwingCoordinate int swingY = transform.getSwingYDip(y);
    @SwingCoordinate int swingWidth = transform.getSwingDimensionDip(componentWidth);

    int l = swingX - swingWidth / 2 + AnchorTarget.ANCHOR_SIZE;
    int t = swingY - AnchorTarget.ANCHOR_SIZE / 2;
    int w = swingWidth - 2 * AnchorTarget.ANCHOR_SIZE;
    list.add(new DrawAnchor(l, t, w, AnchorTarget.ANCHOR_SIZE, type, isConnected, mode));
  }
}
