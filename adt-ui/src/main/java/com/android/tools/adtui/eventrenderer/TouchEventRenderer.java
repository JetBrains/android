/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.eventrenderer;

import com.android.tools.adtui.model.event.EventAction;
import com.intellij.ui.JBColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import org.jetbrains.annotations.NotNull;

public class TouchEventRenderer<E> implements EventRenderer<E> {

  private static final JBColor HOLD_COLOR = new JBColor(new Color(0x66E2739A, true),
                                                        new Color(0x66E27BA4, true));
  private static final JBColor TOUCH_COLOR = new JBColor(new Color(0xCCE2739A, true),
                                                         new Color(0xCCE27BA4, true));
  private static final int MIN_LENGTH = 20;

  private static final int LINE_WIDTH = 12;

  private static final int BORDER_MARGIN = 2;

  @Override
  public void draw(@NotNull Component parent,
                   @NotNull Graphics2D g2d,
                   @NotNull AffineTransform transform,
                   double length,
                   boolean isMouseOver,
                   EventAction<E> notUsedData) {
    Color currentColor = g2d.getColor();
    Stroke currentStroke = g2d.getStroke();
    double xPosition = transform.getTranslateX() - LINE_WIDTH / 2.0;
    double yPosition = transform.getTranslateY() + LINE_WIDTH / 2.0;

    g2d.setColor(parent.getBackground());
    Ellipse2D.Double ellipse = new Ellipse2D.Double(xPosition - BORDER_MARGIN, yPosition - BORDER_MARGIN, LINE_WIDTH + BORDER_MARGIN * 2,
                                                    LINE_WIDTH + BORDER_MARGIN * 2);
    g2d.fill(ellipse);
    g2d.setColor(TOUCH_COLOR);
    ellipse = new Ellipse2D.Double(xPosition, yPosition, LINE_WIDTH, LINE_WIDTH);
    g2d.fill(ellipse);
    // If the duration of mouse down was significant we draw a trailing line for it.
    if (length >= MIN_LENGTH) {
      BasicStroke str = new BasicStroke(LINE_WIDTH);
      g2d.setStroke(str);
      g2d.setColor(HOLD_COLOR);
      RoundRectangle2D.Double rect = new RoundRectangle2D.Double(xPosition, yPosition, length, LINE_WIDTH, LINE_WIDTH, LINE_WIDTH);
      g2d.fill(rect);
      g2d.setStroke(currentStroke);
    }
    g2d.setColor(currentColor);
  }
}
