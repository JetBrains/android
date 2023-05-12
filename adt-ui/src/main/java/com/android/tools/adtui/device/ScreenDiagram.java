/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.device;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public final class ScreenDiagram extends JPanel {
  private static final Font FIGURE_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 10));

  private static final Color GRAY = new JBColor(Gray._192, Gray._96);
  private int width;
  private int height;

  public ScreenDiagram() {
    this(0, 0);
  }

  public ScreenDiagram(int width, int height) {
    setDimensions(width, height);
  }

  public void setDimensions(int width, int height) {
    this.width = width;
    this.height = height;
    setVisible(width > 0 && height > 0);
    repaint();
  }

  @Override
  public @NotNull Dimension getMaximumSize() {
    Dimension size = super.getMaximumSize();
    size.height = Math.min(size.height, JBUIScale.scale(200));

    return size;
  }

  @Override
  public @NotNull Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();

    size.width = Math.max(size.width, JBUIScale.scale(120));
    size.height = Math.max(size.height, JBUIScale.scale(120));

    return size;
  }

  @Override
  protected void paintComponent(@NotNull Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D graphics2d = null;

    try {
      graphics2d = (Graphics2D)graphics.create();

      graphics2d.setFont(FIGURE_FONT);
      GraphicsUtil.setupAAPainting(graphics2d);
      GraphicsUtil.setupAntialiasing(graphics2d);

      Dimension dimension = getScaledDimension(graphics2d);

      paintWidth(graphics2d, dimension);
      paintScreen(graphics2d, dimension);
      paintHeight(graphics2d, dimension);
    }
    finally {
      if (graphics2d != null) {
        graphics2d.dispose();
      }
    }
  }

  /**
   * Returns the dimensions of the device's dp scaled so that it fits in the component
   */
  private @NotNull Dimension getScaledDimension(@NotNull Graphics graphics) {
    // Leave room at the top and to the right for the dimensions text
    FontMetrics metrics = graphics.getFontMetrics(FIGURE_FONT);

    double maxWidth = getWidth() - metrics.stringWidth("0000 dp") - getFigurePadding() - getOutlineLineWidth() / 2.0 - getLeftPadding();
    double maxHeight = getHeight() - metrics.getHeight() - getFigurePadding() - getOutlineLineWidth() / 2.0;

    double sideRatio = (double) width / (double) height;

    int width;
    int height;

    if (maxHeight * sideRatio > maxWidth) {
      width = (int)maxWidth;
      height = (int)(maxWidth / sideRatio);
    }
    else {
      width = (int)(maxHeight * sideRatio);
      height = (int)maxHeight;
    }

    return new Dimension(Math.max(width, 1), Math.max(height, 1));
  }

  private void paintWidth(@NotNull Graphics2D graphics2d, @NotNull Dimension scaledDimension) {
    // Paint the width line
    graphics2d.setColor(GRAY);
    graphics2d.setStroke(new BasicStroke(getDimensionLineWidth()));

    FontMetrics metrics = graphics2d.getFontMetrics(FIGURE_FONT);
    int lineY = metrics.getHeight() / 2;

    graphics2d.drawLine(getLeftPadding(), lineY, round(getLeftPadding() + scaledDimension.width), lineY);

    // Erase the part of the line that the text overlays
    graphics2d.setColor(getBackground());

    String text = width + " dp";

    int textWidth = metrics.stringWidth(text);
    int textX = round(getLeftPadding() + (scaledDimension.width - textWidth) / 2.0);

    graphics2d.drawLine(textX - getFigurePadding(), lineY, textX + textWidth + getFigurePadding(), lineY);

    // Paint the text
    graphics2d.setColor(getForeground());
    graphics2d.drawString(text, textX, metrics.getHeight() - metrics.getDescent());
  }

  private void paintScreen(@NotNull Graphics2D graphics2d, @NotNull Dimension scaledDimension) {
    graphics2d.setColor(getForeground());
    graphics2d.setStroke(new BasicStroke(getOutlineLineWidth()));

    int y = graphics2d.getFontMetrics(FIGURE_FONT).getHeight() + JBUIScale.scale(getFigurePadding());

    Shape shape = new RoundRectangle2D.Double(getLeftPadding(),
                                              y,
                                              scaledDimension.width,
                                              scaledDimension.height,
                                              JBUIScale.scale(10),
                                              JBUIScale.scale(10));

    graphics2d.draw(shape);
  }

  private void paintHeight(@NotNull Graphics2D graphics2d, @NotNull Dimension scaledDimension) {
    // Paint the height line
    graphics2d.setColor(GRAY);
    graphics2d.setStroke(new BasicStroke(getDimensionLineWidth()));

    FontMetrics metrics = graphics2d.getFontMetrics(FIGURE_FONT);
    int lineX = round(getLeftPadding() + scaledDimension.width + JBUIScale.scale(15));
    int rectangleOffsetY = metrics.getHeight() + getFigurePadding();

    graphics2d.drawLine(lineX, JBUIScale.scale(rectangleOffsetY), lineX, round(JBUIScale.scale(rectangleOffsetY) + scaledDimension.height));

    // Erase the part of the line that the text overlays
    graphics2d.setColor(getBackground());

    int textHeight = metrics.getHeight() - metrics.getDescent();
    int textY = round(JBUIScale.scale(rectangleOffsetY) + (scaledDimension.height + textHeight) / 2.0);

    graphics2d.drawLine(lineX, textY + getFigurePadding(), lineX, textY - textHeight - getFigurePadding());

    // Paint the text
    graphics2d.setColor(getForeground());

    graphics2d.drawString(height + " dp", lineX - JBUIScale.scale(10), textY);
  }

  private static int getFigurePadding() {
    return JBUIScale.scale(3);
  }

  private static int getOutlineLineWidth() {
    return JBUIScale.scale(5);
  }

  private static int getLeftPadding() {
    return JBUIScale.scale(3);
  }

  private static int getDimensionLineWidth() {
    return JBUIScale.scale(1);
  }

  private static int round(double d) {
    return (int)Math.round(d);
  }
}
