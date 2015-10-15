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

import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

public class ColorPaletteComponent implements Icon {
  private static final int ICON_SIZE = JBUI.scale(20);
  private static final int PADDING = JBUI.scale(2);

  private Color myPrimaryColor = null;
  private Color myPrimaryDarkColor = null;
  private Color myAccentColor = null;

  public void setValues(@NotNull Color primaryColor, @NotNull Color primaryDarkColor, @NotNull Color accentColor) {
    myPrimaryColor = primaryColor;
    myPrimaryDarkColor = primaryDarkColor;
    myAccentColor = accentColor;
  }

  public void reset() {
    myPrimaryColor = null;
    myPrimaryDarkColor = null;
    myAccentColor = null;
  }

  @Override
  public void paintIcon(Component component, Graphics graphics, int i, int i1) {
    if (myPrimaryColor != null && myPrimaryDarkColor != null && myAccentColor != null) {
      setupAAPainting(graphics);
      Graphics2D g = (Graphics2D)graphics.create();

      new SwatchComponent.ColorIcon(myPrimaryColor).paint(component, g, i + PADDING, i1 + PADDING, ICON_SIZE, ICON_SIZE);
      new SwatchComponent.ColorIcon(myPrimaryDarkColor)
        .paint(component, g, i + 2 * PADDING + ICON_SIZE, i1 + PADDING, ICON_SIZE, ICON_SIZE);
      new SwatchComponent.ColorIcon(myAccentColor).paint(component, g, i + 3 * PADDING + 2 * ICON_SIZE, i1 + PADDING, ICON_SIZE, ICON_SIZE);
      g.dispose();
    }
  }

  @Override
  public int getIconWidth() {
    return (ICON_SIZE + PADDING) * 3 + PADDING;
  }

  @Override
  public int getIconHeight() {
    return ICON_SIZE + 2 * PADDING;
  }
}