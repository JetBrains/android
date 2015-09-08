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
package com.android.tools.swing.ui;

import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

/**
 * Component that displays a list of icons and a label
 */
public class SwatchComponent extends JComponent {
  /**
   * Padding used vertically and horizontally
   */
  private static final int PADDING = JBUI.scale(2);
  /**
   * Additional padding from the top for the value label. The text padding from the top will be PADDING + TEXT_PADDING
   */
  private static final int TEXT_PADDING = JBUI.scale(8);
  /**
   * Separation between states
   */
  private static final int SWATCH_HORIZONTAL_ICONS_PADDING = JBUI.scale(2);
  private static final int ARC_SIZE = ThemeEditorConstants.ROUNDED_BORDER_ARC_SIZE;
  private static final Color DEFAULT_BORDER_COLOR = Gray._170;
  private static final Color WARNING_BORDER_COLOR = JBColor.ORANGE;
  private String myText = "";

  private List<SwatchIcon> myIconList = Collections.emptyList();
  private short myMaxIcons;
  private boolean myHasOverflowIcons;
  private Color myBorderColor;

  /**
   * Constructs a SwatchComponent with a maximum number of icons. If the number of icons is greater than maxIcons
   * the component will display a text with the number of icons left to display. When the user clicks that icon the
   * icons will expand until the user leaves the control area.
   */
  public SwatchComponent(short maxIcons) {
    myMaxIcons = maxIcons;
    setBorder(null);
    myBorderColor = DEFAULT_BORDER_COLOR;

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        SwatchComponent.this.mouseReleased(e);
      }
    });
  }

  /**
   * Maximum number of icons to display on the component. If the list of swatch icons is bigger than this number a "..." label
   * will be displayed to see the rest in a popup dialog.
   */
  public void setMaxIcons(short maxIcons) {
    myMaxIcons = maxIcons;
  }

  public void setSwatchIcons(@NotNull List<? extends SwatchIcon> icons) {
    myIconList = ImmutableList.copyOf(icons);
  }

  public void setWarningBorder(boolean isWarning) {
    myBorderColor = isWarning ? WARNING_BORDER_COLOR : DEFAULT_BORDER_COLOR;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    setupAAPainting(graphics);
    Graphics2D g = (Graphics2D)graphics.create();

    final int width = getWidth();
    final int height = getHeight();
    final int iconSize = getIconSize();

    // Background is filled manually here instead of calling super.paintComponent()
    // because some L'n'Fs (e.g. GTK+) paint additional decoration even with null border.
    g.setColor(getBackground());
    if (getBorder() == null) {
      g.fillRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);
      // Default border
      g.setColor(myBorderColor);
      g.drawRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);
    } else {
      g.fillRect(0, 0, width - 1, height - 1);
    }

    Shape savedClip = g.getClip();
    int xOffset = PADDING;
    int nIcons = myIconList.size();
    int iconsToPaint;
    RoundRectangle2D clipRectangle = new RoundRectangle2D.Double();

    // Since the overflow icons takes the same space as one icon, only draw it if the number of overflow icons is at least 2.
    if (nIcons - myMaxIcons >= 2) {
      myHasOverflowIcons = true;
      iconsToPaint = myMaxIcons;
    } else {
      myHasOverflowIcons = false;
      iconsToPaint = nIcons;
    }

    for (SwatchIcon icon : myIconList) {
      if (iconsToPaint-- <= 0) {
        // Do not paint more icons
        break;
      }

      clipRectangle.setRoundRect(xOffset, PADDING, iconSize, iconSize, ARC_SIZE, ARC_SIZE);
      g.clip(clipRectangle);
      icon.paint(this, g, xOffset, PADDING, iconSize, iconSize);
      g.setColor(Gray._239);
      g.setClip(savedClip);
      g.drawRoundRect(xOffset, PADDING, iconSize, iconSize, ARC_SIZE, ARC_SIZE);
      xOffset += iconSize + SWATCH_HORIZONTAL_ICONS_PADDING;
    }

    Rectangle textRectangle = new Rectangle();
    // Should we paint the overflow icon?
    if (myHasOverflowIcons) {
      int overFlowIcons = nIcons - myMaxIcons;
      g.setColor(JBColor.LIGHT_GRAY);
      textRectangle.setBounds(xOffset, PADDING, iconSize, iconSize);
      GraphicsUtil.drawCenteredString(g, textRectangle, "+" + overFlowIcons);
      xOffset += iconSize + SWATCH_HORIZONTAL_ICONS_PADDING;
    }

    xOffset += SWATCH_HORIZONTAL_ICONS_PADDING * 2;

    // Text is centered vertically so we do not need to use TEXT_PADDING here, only in the preferred size.
    textRectangle.setBounds(xOffset, 0, width - xOffset, height);
    g.setColor(getForeground());
    GraphicsUtil.drawCenteredString(g, textRectangle, myText, false, true);
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

  private int getIconSize() {
    return getHeight() - 2 * PADDING - 1;
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

  public void setText(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public void addActionListener(@NotNull ActionListener listener) {
    listenerList.add(ActionListener.class, listener);
  }

  @SuppressWarnings("unused")
  public void removeActionListener(@NotNull ActionListener listener) {
    listenerList.remove(ActionListener.class, listener);
  }

  private void mouseReleased(@NotNull MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e) || !contains(e.getPoint())) {
      return;
    }

    if (myHasOverflowIcons) {
      // Check if the click was in the overflow text
      int iconSize = getIconSize();
      Rectangle overflowIconRect = new Rectangle(myMaxIcons * iconSize, PADDING, iconSize, iconSize);

      if (overflowIconRect.contains(e.getPoint())) {
        final short previousMaxIcons = myMaxIcons;
        addMouseListener(new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            SwatchComponent.this.removeMouseListener(this);
            setMaxIcons(previousMaxIcons);
            repaint();
          }
        });
        setMaxIcons(Short.MAX_VALUE);
        // Display all the other colors until the mouse exits the component
        repaint();
        return;
      }
    }

    ActionListener[] actionListeners = listenerList.getListeners(ActionListener.class);
    ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, myText, e.getWhen(), e.getModifiers());
    for (ActionListener listener : actionListeners) {
      listener.actionPerformed(event);
    }
  }
}
