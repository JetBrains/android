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

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * Draw the frame of a SceneComponent
 */
public class DrawNlComponentFrame extends DrawRegion {
  public static final int SUBDUED = 0;
  public static final int NORMAL = 1;
  public static final int OVER = 2;
  public static final int SELECTED = 3;

  static Stroke myNormalStroke = new BasicStroke(1);
  static Stroke myProblemStroke = new BasicStroke(2);
  static Stroke myWrapStroke =  new BasicStroke(1);
  static Stroke myMatchParentStroke = new BasicStroke(1);
  static Stroke myDragReceiverStroke = new BasicStroke(3);
  static Stroke myMatchConstraintStroke = new FancyStroke(FancyStroke.Type.SPRING, 2, 2, 1);

  int myMode;
  int myLayoutWidth;
  int myLayoutHeight;
  int myLevel = COMPONENT_LEVEL;

  public DrawNlComponentFrame(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
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
                            int mode,
                            int layout_width,
                            int layout_height) {
    super(x, y, width, height);
    myMode = mode;
    myLayoutWidth = layout_width;
    myLayoutHeight = layout_height;
    if (mode == SELECTED) {
      myLevel = COMPONENT_SELECTED_LEVEL;
    }
  }

  private Stroke getStroke(int dim) {
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
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color[] colorFrame = {colorSet.getSubduedFrames(), colorSet.getFrames(), colorSet.getHighlightedFrames(), colorSet.getSelectedFrames(), colorSet.getDragReceiverFrames()};
    Stroke previousStroke = g.getStroke();
    g.setStroke(myNormalStroke);
    g.setColor(colorFrame[myMode]);
    if (myLayoutWidth == myLayoutHeight || myMode == 4) {
      if (myMode == 4) {
        g.setStroke(myDragReceiverStroke);
      } else {
        g.setStroke(getStroke(myLayoutWidth));
      }
      g.drawRect(x, y, width, height);
    }
    else {
      g.setColor(colorFrame[myMode]);
      g.setStroke(getStroke(myLayoutHeight));
      g.drawLine(x, y, x, y + height);
      g.drawLine(x + width, y, x + width, y + height);
      g.setColor(colorFrame[myMode]);
      g.setStroke(getStroke(myLayoutWidth));
      g.drawLine(x, y, x + width, y);
      g.drawLine(x, y + height, x + width, y + height);
    }
    g.setStroke(previousStroke);
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode+ "," + myLayoutHeight+ "," + myLayoutHeight;
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         int mode,
                         int layout_width,
                         int layout_height) {
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    list.add(new DrawNlComponentFrame(l, t, w, h, mode, layout_width, layout_height));
  }
}
