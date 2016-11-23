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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

public class TouchEventRenderer implements SimpleEventRenderer {

  private static final Color HOLD_COLOR = new Color(214, 196, 228);
  private static final Color TOUCH_COLOR = new Color(156, 110, 189);
  private static final int MIN_LENGTH = 20;

  // TODO: make this accessible for on mouse over to adjust height.
  private int myLineWidth = 12;

  @Override
  public void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length) {

    Color currentColor = g2d.getColor();
    Stroke currentStroke = g2d.getStroke();
    int xPosition = (int)(transform.getTranslateX() -myLineWidth/2.0);
    int yPosition = (int)(transform.getTranslateY() + myLineWidth/2.0);

    // If the duration of mouse down was significant we draw a trailing line for it.
    if (length >= MIN_LENGTH) {
      BasicStroke str = new BasicStroke(myLineWidth);
      g2d.setStroke(str);
      g2d.setColor(HOLD_COLOR);
      g2d.fillRoundRect(xPosition, yPosition, (int)length, myLineWidth, myLineWidth, myLineWidth);
      g2d.setStroke(currentStroke);
    }
    g2d.setColor(TOUCH_COLOR);
    g2d.fillOval(xPosition, yPosition, myLineWidth, myLineWidth);
    g2d.setColor(currentColor);
  }
}
