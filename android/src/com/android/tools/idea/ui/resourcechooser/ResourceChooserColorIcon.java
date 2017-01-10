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
package com.android.tools.idea.ui.resourcechooser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;

public class ResourceChooserColorIcon implements Icon {

  @NotNull private final Color myColor;
  private final int myWidth;
  private final int myHeight;
  @Nullable private final Paint myCheckerPaint;

  public ResourceChooserColorIcon(int size, @NotNull Color color, int checkerboardSize) {
    this(size, size, color, checkerboardSize);
  }

  public ResourceChooserColorIcon(int width, int height, @NotNull Color color, int checkerboardSize) {
    myColor = color;
    myWidth = width;
    myHeight = height;
    myCheckerPaint = checkerboardSize > 0 ? new ResourceChooserImageIcon.CheckerboardPaint(checkerboardSize) : null;
  }

  @Override
  public void paintIcon(@Nullable Component c, @NotNull Graphics g, int x, int y) {

    if (myColor.getAlpha() != 255 && myCheckerPaint != null) {
      ((Graphics2D)g).setPaint(myCheckerPaint);
      g.fillRect(x, y, getIconWidth(), getIconHeight());
    }

    g.setColor(myColor);
    g.fillRect(x, y, getIconWidth(), getIconHeight());
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }
}
