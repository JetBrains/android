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
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

public class ExpandingEventRenderer<E> implements SimpleEventRenderer<E> {
  private static final int LINE_WIDTH = 12;
  private final Color myLineColor;

  public ExpandingEventRenderer(Color lineColor) {
    myLineColor = lineColor;
  }

  @Override
  public void draw(@NotNull Component parent,
                   @NotNull Graphics2D g2d,
                   @NotNull AffineTransform transform,
                   double length,
                   boolean isMouseOver,
                   EventAction<E> action) {
    // Stash off current state.
    Color currentColor = g2d.getColor();
    Stroke currentStroke = g2d.getStroke();
    double xPosition = transform.getTranslateX() - LINE_WIDTH / 2.0;
    double yPosition = transform.getTranslateY() + LINE_WIDTH / 2.0;
    BasicStroke str = new BasicStroke(LINE_WIDTH);
    g2d.setStroke(str);
    // Draw our initial marker.
    RoundRectangle2D.Double markerRect = new RoundRectangle2D.Double(xPosition, yPosition, 3, LINE_WIDTH, 1, 1);
    g2d.setColor(myLineColor);
    g2d.fill(markerRect);
    // If we are over this element, draw an expanded rectangle in addition to our initial marker.
    if (isMouseOver) {
      RoundRectangle2D.Double expandedRect = new RoundRectangle2D.Double(xPosition, yPosition, length, LINE_WIDTH, 1, 1);
      Color temp = ColorUtil.withAlpha(myLineColor, .8);
      g2d.setColor(temp);
      g2d.fill(expandedRect);
    }
    // Reset current state.
    g2d.setColor(currentColor);
    g2d.setStroke(currentStroke);
  }
}
