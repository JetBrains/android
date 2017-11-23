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
package com.android.tools.adtui.instructions;

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * Instruction for rendering text.
 */
public final class TextInstruction extends RenderInstruction {
  @NotNull private final String myText;
  @NotNull private final Font myFont;
  @NotNull private final Dimension mySize;

  public TextInstruction(@NotNull Font font, @NotNull String text) {
    myFont = font;
    myText = text;

    Rectangle2D bounds = myFont.getStringBounds(myText, new FontRenderContext(null, true, true));
    int w = (int)bounds.getWidth();
    int h = (int)bounds.getHeight();

    mySize = new Dimension(w, h);
  }

  @NotNull
  @Override
  public Dimension getSize() {
    return mySize;
  }

  @VisibleForTesting
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull Rectangle bounds) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

    assert (mySize.height <= bounds.height);
    g2d.setColor(c.getForeground());
    g2d.setFont(myFont);
    FontMetrics metrics = SwingUtilities2.getFontMetrics(c, myFont);
    int textY = bounds.y + metrics.getAscent() + ((bounds.height - mySize.height) / 2);
    g2d.drawString(myText, bounds.x, textY);
  }
}
