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
package com.android.tools.idea.editors.theme;

import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Component that renders a list of colors.
 */
public class ColorPalette extends JComponent implements Scrollable {
  protected static int CHECKERED_BACKGROUND_SIZE = 8;

  private int myColorBoxSize = 50;
  private int myColorBoxPadding = myColorBoxSize / 10;
  private boolean myShowCheckeredBackground = false;
  private ColorPaletteModel myColorListModel;

  /**
   * Model for the ColorPalette component.
   */
  public interface ColorPaletteModel {
    /**
     * Returns the number of elements.
     */
    int getCount();

    /**
     * Returns the element located at the index {@code i}.
     */
    Color getColorAt(int i);
  }

  /**
   * Model that defines a static list of colors.
   */
  public static class StaticColorPaletteModel implements ColorPaletteModel {
    private final List<Color> myColorList;

    public StaticColorPaletteModel(@NotNull List<Color> colorList) {
      myColorList = ImmutableList.copyOf(colorList);
    }

    @Override
    public int getCount() {
      return myColorList.size();
    }

    @Override
    public Color getColorAt(int i) {
      return myColorList.get(i);
    }
  }

  public ColorPalette(@NotNull ColorPaletteModel colorListModel) {
    myColorListModel = colorListModel;

    setOpaque(false);
  }

  public ColorPalette() {
    // Constructor used to display some content on the UI designer.
    this(new StaticColorPaletteModel(Collections.<Color>emptyList()));
  }

  public void setModel(ColorPaletteModel colorListModel) {
    myColorListModel = colorListModel;

    revalidate();
  }

  /**
   * Sets the size of each color box in pixels.
   */
  public void setColorBoxSize(int colorSize) {
    myColorBoxSize = colorSize;

    revalidate();
  }

  /**
   * Sets the padding around each color box in pixels.
   */
  public void setColorBoxPadding(int padding) {
    myColorBoxPadding = padding;

    revalidate();
  }

  public void setShowCheckeredBackground(boolean showCheckeredBackground) {
    myShowCheckeredBackground = showCheckeredBackground;
  }

  @Override
  public Dimension getMinimumSize() {
    int minSize = myColorBoxSize + myColorBoxPadding * 2;
    return new Dimension(minSize, minSize);
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet() || myColorListModel.getCount() < 1) {
      return super.getPreferredSize();
    }

    int minSize = myColorBoxSize + myColorBoxPadding * 2;
    return new Dimension(myColorListModel.getCount() * (myColorBoxSize + myColorBoxPadding) + myColorBoxPadding, minSize);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myColorListModel.getCount() < 1) {
      return;
    }

    if (myShowCheckeredBackground) {
      GraphicsUtil.paintCheckeredBackground(g, getBounds(), CHECKERED_BACKGROUND_SIZE);
    }

    final int width = getWidth();
    for (int i = 0; i < myColorListModel.getCount(); i++) {
      g.setColor(myColorListModel.getColorAt(i));
      int x = i * (myColorBoxSize + myColorBoxPadding) + myColorBoxPadding;
      g.fillRect(x, myColorBoxPadding, myColorBoxSize, myColorBoxSize);

      if (x > width) {
        break;
      }
    }
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    Dimension preferredSize = getPreferredSize();
    return new Dimension(preferredSize.width, preferredSize.height + UIUtil.getScrollBarWidth());
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 5;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return myColorBoxSize + myColorBoxPadding;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }
}
