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
package com.android.tools.idea.uibuilder.handlers.linear.draw;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draw a rectangle to show the the potential position of a dragged component
 * in a LinearLayout
 */
public class DrawLinearPlaceholder extends DrawRegion {

  public DrawLinearPlaceholder(
    @AndroidDpCoordinate int x,
    @AndroidDpCoordinate int y,
    @AndroidDpCoordinate int width,
    @AndroidDpCoordinate int height) {
    setBounds(x, y, width, height);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    Color defColor = g.getColor();

    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getDragReceiverBackground());
    g.fill(this);
    g.setColor(colorSet.getDragReceiverFrames());
    g.draw(this);

    g.setColor(defColor);
  }

  @Override
  public int getLevel() {
    return super.getLevel();
  }

  /**
   * @param isLayoutVertical The orientation of the LinearLayout where the place holder will be drawn
   * @param atEnd            set to true if the place holder should be displayed after
   *                         the last component instead of in between two components
   * @param highLightSize    The size of the highlighted area. If the layout is vertical, the highlighted
   *                         area should be the height of the dragged component and if the layout is horizontal it should
   *                         be the component width
   */
  public static void add(DisplayList list, SceneContext context,
                         boolean isLayoutVertical,
                         boolean atEnd,
                         @AndroidDpCoordinate int highLightSize,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom) {


    int x = context.getSwingXDip(left);
    int y = context.getSwingYDip(top);
    int width = context.getSwingDimensionDip(right - left);
    int height = context.getSwingDimensionDip(bottom - top);
    highLightSize = context.getSwingDimensionDip(highLightSize);

    if (isLayoutVertical) {
      if (!atEnd) {
        y -= highLightSize / 2;
      }
      height = highLightSize;
    }
    else {
      if (!atEnd) {
        x -= highLightSize / 2;
      }
      width = highLightSize;
    }
    list.add(new DrawLinearPlaceholder(x, y, JBUIScale.scale(width), JBUIScale.scale(height)));
  }
}
