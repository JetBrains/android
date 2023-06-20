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
package com.android.tools.idea.common.scene.draw;

import static com.android.tools.idea.common.scene.SceneComponent.DrawState.HOVER;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import org.jetbrains.annotations.NotNull;

/**
 * Draw the frame of a SceneComponent
 */
public class DrawNlComponentFrame extends DrawRegion {

  private static final Stroke myHoveredStroke = new BasicStroke(2);
  private static final Stroke myNormalStroke = new BasicStroke(1);
  private static final Stroke myWrapStroke =  new BasicStroke(1);
  private static final Stroke myMatchParentStroke = new BasicStroke(1);
  private static final Stroke myDragReceiverStroke = new BasicStroke(3);
  private static final Stroke myMatchConstraintStroke = new BasicStroke(1);

  @NotNull private SceneComponent.DrawState myMode;
  private int myLayoutWidth;
  private int myLayoutHeight;
  private int myLevel = COMPONENT_LEVEL;

  public DrawNlComponentFrame(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = SceneComponent.DrawState.values()[Integer.parseInt(sp[c++])];
    myLayoutWidth = Integer.parseInt(sp[c++]);
    myLayoutHeight = Integer.parseInt(sp[c++]);
  }

  @Override
  public int getLevel() {
    return myLevel;
  }

  public DrawNlComponentFrame(@AndroidDpCoordinate int x,
                              @AndroidDpCoordinate int y,
                              @AndroidDpCoordinate int width,
                              @AndroidDpCoordinate int height,
                              @NotNull SceneComponent.DrawState mode,
                              int layout_width,
                              int layout_height) {
    super(x, y, width, height);
    myMode = mode;
    myLayoutWidth = layout_width;
    myLayoutHeight = layout_height;
    if (mode == SceneComponent.DrawState.SELECTED) {
      myLevel = COMPONENT_SELECTED_LEVEL;
    }
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Stroke previousStroke = g.getStroke();
    Color previousColor = g.getColor();

    g.setStroke(myNormalStroke);
    g.setColor(getFrameColor(colorSet, myMode));

    if (myMode == SceneComponent.DrawState.DRAG) {
      g.setStroke(myDragReceiverStroke);
      g.drawRect(x, y, width, height);
    }
    else {
      Shape clipping = g.getClip();
      if (clipping != null && !clipping.contains(x, y, x + width, x + height)) {
        // Draw dot line
        g.setClip(sceneContext.getRenderableBounds());
        g.setStroke(ColorSet.sDashedStroke);
        g.drawLine(x, y, x, y + height);
        g.drawLine(x + width, y, x + width, y + height);
        g.drawLine(x, y, x + width, y);
        g.drawLine(x, y + height, x + width, y + height);
        g.setClip(clipping);
      }

      g.setStroke(getStroke(myLayoutHeight, myMode));
      g.drawLine(x, y, x, y + height);
      g.drawLine(x + width, y, x + width, y + height);
      g.setStroke(getStroke(myLayoutWidth, myMode));
      g.drawLine(x, y, x + width, y);
      g.drawLine(x, y + height, x + width, y + height);
    }

    g.setColor(previousColor);
    g.setStroke(previousStroke);
  }

  @NotNull
  private static Color getFrameColor(@NotNull ColorSet colorSet, @NotNull SceneComponent.DrawState mode) {
    switch (mode) {
      case SUBDUED:
        return colorSet.getSubduedFrames();
      case NORMAL:
        return colorSet.getFrames();
      case HOVER:
        return colorSet.getHighlightedFrames();
      case SELECTED:
        return colorSet.getSelectedFrames();
      case DRAG:
        return colorSet.getDragReceiverFrames();
      default:
        // Should not happen.
        return colorSet.getFrames();
    }
  }

  @NotNull
  private static Stroke getStroke(int dim, @NotNull SceneComponent.DrawState mode) {
    if (mode == HOVER) {
      return myHoveredStroke;
    }
    if (dim == 0) {
      return myMatchConstraintStroke;
    }
    if (dim == -1) {
      return myMatchParentStroke ;
    }
    if (dim == -2) {
      return myWrapStroke;
    }
    return myNormalStroke;
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode.ordinal() + "," + myLayoutHeight+ "," + myLayoutHeight;
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         @NotNull SceneComponent.DrawState mode,
                         int layout_width,
                         int layout_height) {
    int l = sceneContext.getSwingXDip(rect.x);
    int t = sceneContext.getSwingYDip(rect.y);
    int w = sceneContext.getSwingDimensionDip(rect.width);
    int h = sceneContext.getSwingDimensionDip(rect.height);
    list.add(new DrawNlComponentFrame(l, t, w, h, mode, layout_width, layout_height));
  }
}
