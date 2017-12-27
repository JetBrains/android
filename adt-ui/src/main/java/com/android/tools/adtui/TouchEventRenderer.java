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
package com.android.tools.adtui;

import com.android.tools.adtui.model.event.EventAction;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public class TouchEventRenderer<E> implements SimpleEventRenderer<E> {

  private static final JBColor HOLD_COLOR = new JBColor(new Color(0x668D7BCE, true),
                                                        new Color(0x669876D8, true));
  private static final JBColor TOUCH_COLOR = new JBColor(new Color(0xCC8D7BCE, true),
                                                         new Color(0xCC9876D8, true));
  private static final int MIN_LENGTH = 20;

  // TODO: make this accessible for on mouse over to adjust height.
  private int myLineWidth = 12;

  @Override
  public void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length, EventAction<E> notUsedData) {
    Color currentColor = g2d.getColor();
    Stroke currentStroke = g2d.getStroke();
    double xPosition = transform.getTranslateX() - myLineWidth / 2.0;
    double yPosition = transform.getTranslateY() + myLineWidth / 2.0;

    // If the duration of mouse down was significant we draw a trailing line for it.
    if (length >= MIN_LENGTH) {
      BasicStroke str = new BasicStroke(myLineWidth);
      g2d.setStroke(str);
      g2d.setColor(HOLD_COLOR);
      RoundRectangle2D.Double rect = new RoundRectangle2D.Double(xPosition, yPosition, length, myLineWidth, myLineWidth, myLineWidth);
      g2d.fill(rect);
      g2d.setStroke(currentStroke);
    }
    g2d.setColor(TOUCH_COLOR);
    Ellipse2D.Double ellipse = new Ellipse2D.Double(xPosition, yPosition, myLineWidth, myLineWidth);
    g2d.fill(ellipse);
    g2d.setColor(currentColor);
  }
}
