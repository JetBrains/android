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
package com.android.tools.idea.editors.gfxtrace.renderers.styles;

import org.jetbrains.annotations.NotNull;

import javax.swing.border.AbstractBorder;
import java.awt.*;

/**
 * This object renders a rounded line border.
 * <p/>
 * This object renders a rounded line border in Swing with customizable border size. It also has an option to render
 * over the surrounding space of the UI component is it wrapping around.
 */
public class RoundedLineBorder extends AbstractBorder {
  @NotNull Color myColor;
  int myBorderSize;
  // Sometimes space is limited and so we want to render "over" the empty space taken up by the encapsulated component.
  boolean myNoInsets;

  public RoundedLineBorder(@NotNull Color color, int borderSize, boolean noInsets) {
    myColor = color;
    myBorderSize = borderSize;
    myNoInsets = noInsets;
  }

  @Override
  public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
    g.setColor(myColor);
    g.drawRoundRect(x, y, width - 1, height - 1, myBorderSize, myBorderSize);
  }

  @Override
  public Insets getBorderInsets(@NotNull Component c, @NotNull Insets insets) {
    if (myNoInsets) {
      insets.set(1, 1, 1, 1);
    }
    else {
      insets.set(myBorderSize, myBorderSize, myBorderSize, myBorderSize);
    }
    return insets;
  }
}
