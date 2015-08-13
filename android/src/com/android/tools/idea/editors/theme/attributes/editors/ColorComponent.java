/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.Gray;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.List;

public class ColorComponent extends ResourceComponent {

  private @NotNull List<Color> myColors = Collections.emptyList();
  private ColorIcon myIcon = new ColorIcon();

  @Override
  int getIconCount() {
    return myColors.size();
  }

  @Override
  Icon getIconAt(int i) {
    myIcon.setColor(myColors.get(i));
    return myIcon;
  }

  @Override
  void setIconHeight(int height) {
    myIcon.setHeight(height);
  }

  public void configure(@NotNull EditedStyleItem resValue, @NotNull List<Color> colors) {
    String colorText = colors.isEmpty() ? "(empty)" : ResourceHelper.colorToString(colors.get(0));
    configure(ThemeEditorUtils.getDisplayHtml(resValue), colorText, resValue.getValue());
    myColors = ImmutableList.copyOf(colors);
  }

  static class ColorIcon implements Icon {

    private static final int ARC_SIZE = 10;
    // If squared distance between two colors are less than this constant they're considered to be similar.
    private static final double THRESHOLD_SQUARED_DISTANCE = 0.01;

    private final float[] myRgbaArray = new float[4];
    private Color myColor;
    private int myCellSize;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (myColor.getAlpha() != 0xff) {
        final RoundRectangle2D.Double clip =
          new RoundRectangle2D.Double(x, y, myCellSize, myCellSize, ARC_SIZE, ARC_SIZE);
        GraphicsUtil.paintCheckeredBackground(g, clip);
      }

      g.setColor(myColor);
      g.fillRoundRect(x, y, myCellSize, myCellSize, ARC_SIZE, ARC_SIZE);

      myColor.getRGBComponents(myRgbaArray);
      if (Math.pow(1.0 - myRgbaArray[0], 2) + Math.pow(1.0 - myRgbaArray[1], 2) + Math.pow(1.0 - myRgbaArray[2], 2) < THRESHOLD_SQUARED_DISTANCE) {
        // Drawing a border to avoid displaying white boxes on a white background
        g.setColor(Gray._239);
        g.drawRoundRect(x, y, myCellSize, myCellSize - 1, ARC_SIZE, ARC_SIZE);
      }
    }

    public void setHeight(int height) {
      myCellSize = height;
    }

    public void setColor(@NotNull Color color) {
      this.myColor = color;
    }

    @Override
    public int getIconWidth() {
      return myCellSize;
    }

    @Override
    public int getIconHeight() {
      return myCellSize;
    }
  }
}
