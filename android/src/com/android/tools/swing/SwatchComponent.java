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
package com.android.tools.swing;

import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

/**
 * Component that displays a list of icons and a label
 */
public class SwatchComponent extends ClickableLabel {
  /**
   * Padding used vertically and horizontally
   */
  private static final int PADDING = 2;
  /**
   * Additional padding from the top for the value label. The text padding from the top will be PADDING + TEXT_PADDING
   */
  private static final int TEXT_PADDING = 5;
  /**
   * Separation between states
   */
  private static final int SWATCH_HORIZONTAL_ICONS_PADDING = 2;
  private static final int ARC_SIZE = ThemeEditorConstants.ROUNDED_BORDER_ARC_SIZE;

  private List<SwatchIcon> myIconList = Collections.emptyList();

  public SwatchComponent() {
    setBorder(null);
  }

  public void setSwatchIcons(@NotNull List<SwatchIcon> icons) {
    myIconList = ImmutableList.copyOf(icons);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    setupAAPainting(graphics);
    Graphics2D g = (Graphics2D)graphics.create();

    final int width = getWidth();
    final int height = getHeight();
    final int iconSize = height - 2 * PADDING - 1;

    // Background is filled manually here instead of calling super.paintComponent()
    // because some L'n'Fs (e.g. GTK+) paint additional decoration even with null border.
    g.setColor(getBackground());
    if (getBorder() == null) {
      g.fillRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);
      // Default border
      g.setColor(Gray._170);
      g.drawRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);
    } else {
      g.fillRect(0, 0, width - 1, height - 1);
    }

    Shape savedClip = g.getClip();
    int xOffset = PADDING;
    for (SwatchIcon icon : myIconList) {
      g.setClip(new RoundRectangle2D.Double(xOffset, PADDING, iconSize, iconSize, ARC_SIZE, ARC_SIZE));
      icon.paint(this, g, xOffset, PADDING, iconSize, iconSize);
      g.setColor(Gray._239);
      g.setClip(null);
      g.drawRoundRect(xOffset, PADDING, iconSize, iconSize, ARC_SIZE, ARC_SIZE);
      xOffset += iconSize + SWATCH_HORIZONTAL_ICONS_PADDING;
    }
    g.setClip(savedClip);

    xOffset += SWATCH_HORIZONTAL_ICONS_PADDING * 2;

    // Text is centered vertically so we do not need to use TEXT_PADDING here, only in the preferred size.
    FontMetrics fm = g.getFontMetrics();
    g.setColor(getForeground());
    int yOffset = (height - fm.getHeight()) / 2 + fm.getAscent();
    g.drawString(getText(), xOffset, yOffset);
    g.dispose();
  }

  @Override
  public Dimension getMinimumSize() {
    if (!isPreferredSizeSet()) {
      FontMetrics fm = getFontMetrics(getFont());
      return new Dimension(0, fm.getHeight() + 2 * PADDING + 2 * TEXT_PADDING);
    }
    return super.getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      return getMinimumSize();
    }
    return super.getPreferredSize();
  }

  /**
   * Interface to be implemented by swatch icon providers.
   */
  public interface SwatchIcon {
    void paint(@Nullable Component c, @NotNull Graphics g, int x, int y, int w, int h);
  }

  public static class ColorIcon implements SwatchIcon {
    private final Color myColor;

    public ColorIcon(@NotNull Color color) {
      myColor = color;
    }

    @Override
    public void paint(@Nullable Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      if (myColor.getAlpha() != 0xff) {
        GraphicsUtil.paintCheckeredBackground(g, new Rectangle(x, y, w, h));
      }

      g.setColor(myColor);
      g.fillRect(x, y, w, h);
    }
  }

  public static class SquareImageIcon implements SwatchIcon {
    private ImageIcon myImageIcon;

    public SquareImageIcon(@NotNull ImageIcon imageIcon) {
      myImageIcon = imageIcon;
    }

    @Override
    public void paint(@Nullable Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      Image image = myImageIcon.getImage();
      GraphicsUtil.paintCheckeredBackground(g, new Rectangle(x, y, w, h));
      g.drawImage(image, x, y, w, h, c);
    }
  }

  /**
   * Returns a list of {@link SwatchIcon} for the given {@link Color}.
   */
  @NotNull
  public static List<SwatchIcon> colorListOf(@NotNull List<Color> colors) {
    ImmutableList.Builder<SwatchIcon> colorIcons = ImmutableList.builder();
    for (Color color : colors) {
      colorIcons.add(new ColorIcon(color));
    }

    return colorIcons.build();
  }

  /**
   * Returns a list of {@link SwatchIcon} for the given {@link BufferedImage}.
   */
  @NotNull
  public static List<SwatchIcon> imageListOf(@NotNull List<BufferedImage> images) {
    ImmutableList.Builder<SwatchIcon> iconsList = ImmutableList.builder();
    for (BufferedImage image : images) {
      iconsList.add(new SquareImageIcon(new ImageIcon(image)));
    }

    return iconsList.build();
  }
}
