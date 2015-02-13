/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

public class ColorComponent extends JButton {
  private static final Logger LOG = Logger.getInstance(ColorComponent.class);

  private static final int PADDING = 2;
  private static final int TEXT_PADDING = PADDING + 3;

  private String myName;
  private String myValue;
  private @Nullable Color myColor;
  private Color myDrawnColor;

  private final Color myBackgroundColor;

  public static Border getBorder(final Color borderColor) {
    return BorderFactory.createMatteBorder(PADDING, PADDING, PADDING, PADDING, borderColor);
  }

  public ColorComponent(@NotNull final Color backgroundColor, @NotNull Font labelFont) {
    myBackgroundColor = backgroundColor;
    setFont(labelFont);
  }

  private static Color blendColors(final Color color1, final @Nullable Color color2) {
    if (color2 == null) {
      return color1;
    }

    double k = color2.getAlpha() / ((double) 255);
    return UIUtil.mix(color1, color2, k);
  }

  public void configure(final EditedStyleItem resValue, final Color color) {
    this.myName = resValue.getQualifiedName();
    this.myValue = resValue.getValue();
    setColor(color);
  }

  public String getValue() {
    return myValue;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (myName == null || myValue == null) {
      LOG.error("Trying to draw ColorComponent in inconsistent state (either name or value is null)!");
      return;
    }

    GraphicsUtil.setupAntialiasing(g);

    if (myColor != null) {
      g.setColor(myDrawnColor);
      g.fillRect(PADDING, PADDING, getWidth() - PADDING, getHeight() - PADDING);
    } else {
      g.setColor(JBColor.WHITE);
      g.fillRect(PADDING, PADDING, getWidth() - PADDING, getHeight() - PADDING);
      g.setColor(JBColor.LIGHT_GRAY);
      g.drawLine(PADDING, PADDING, getWidth() - PADDING, getHeight() - PADDING);
      g.drawLine(getWidth() - PADDING, PADDING, PADDING, getHeight() - PADDING);
    }

    //noinspection UseJBColor
    g.setColor(myColor != null && ColorUtil.isDark(myDrawnColor) ? Color.WHITE : Color.BLACK);

    FontMetrics fm = g.getFontMetrics();
    g.drawString(myName, TEXT_PADDING, fm.getHeight() + TEXT_PADDING);
    g.drawString(myValue, TEXT_PADDING, getHeight() - TEXT_PADDING - fm.getDescent());
  }

  public void setColor(@Nullable Color color) {
    myColor = color;
    myDrawnColor = blendColors(myBackgroundColor, myColor);
  }
}
