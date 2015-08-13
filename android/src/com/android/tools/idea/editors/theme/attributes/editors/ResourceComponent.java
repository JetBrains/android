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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

/**
 * Abstract Component for displaying a color or a drawable resource with attribute name, type and value text.
 * It also supports ColorStateList and StateListDrawable and displays all states of the item.
 * Drawable support in {@link DrawableComponent} and Color support in {@link ColorComponent}
 */
abstract class ResourceComponent extends JPanel {

  private static final String COLOR_LIGHT = "6F6F6F";
  private static final String COLOR_DARCULA = "AAAAAA";
  private static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"> - %3$s</font>";

  private static final int DISTANCE_BETWEEN_ROWS = 20;
  private static final int LABEL_BUTTON_GAP = 8;
  private static final int BUTTON_VERTICAL_PADDINGS = 14;
  public static final int SUM_PADDINGS = DISTANCE_BETWEEN_ROWS + LABEL_BUTTON_GAP + BUTTON_VERTICAL_PADDINGS;

  private final ResourceChooserButton myResourceChooserButton = new ResourceChooserButton();
  private final JLabel myNameLabel = new JLabel();

  public ResourceComponent() {
    super(new BorderLayout(0, LABEL_BUTTON_GAP));
    setBorder(BorderFactory.createMatteBorder(DISTANCE_BETWEEN_ROWS / 2, 0, DISTANCE_BETWEEN_ROWS / 2, 0, getBackground()));

    add(myNameLabel, BorderLayout.NORTH);

    myResourceChooserButton.setBorder(null);
    myResourceChooserButton.setBackground(JBColor.WHITE);
    myResourceChooserButton.setForeground(null);
    add(myResourceChooserButton, BorderLayout.CENTER);
  }

  abstract int getIconCount();

  abstract Icon getIconAt(int i);

  abstract void setIconHeight(int height);

  public void configure(String name, String type, String value) {
    String firstColor = UIUtil.isUnderDarcula() ? COLOR_DARCULA : COLOR_LIGHT;
    myNameLabel.setText(String.format(LABEL_TEMPLATE, firstColor, name, type));
    myResourceChooserButton.setText(value);
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    // We need a null check here as this can be called from the setUI that's called from the constructor.
    if (myResourceChooserButton != null) {
      myResourceChooserButton.setFont(font);
    }
  }

  @Override
  public void setComponentPopupMenu(JPopupMenu popup) {
    super.setComponentPopupMenu(popup);
    myResourceChooserButton.setComponentPopupMenu(popup);
  }

  public void addActionListener(final ActionListener listener) {
    myResourceChooserButton.addActionListener(listener);
  }

  class ResourceChooserButton extends JButton {

    private static final int BETWEEN_STATES_PADDING = 2;
    private static final int STATES_PADDING = 6;
    private static final int ARC_SIZE = 10;

    @Override
    protected void paintComponent(Graphics g) {
      setupAAPainting(g);

      final int width = getWidth();
      final int height = getHeight();

      // Background is filled manually here instead of calling super.paintComponent()
      // because some L'n'Fs (e.g. GTK+) paint additional decoration even with null border.
      g.setColor(getBackground());
      g.fillRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);

      g.setColor(Gray._170);
      g.drawRoundRect(0, 0, width - 1, height - 1, ARC_SIZE, ARC_SIZE);

      int xOffset = BETWEEN_STATES_PADDING;
      for (int c = 0; c < getIconCount(); c++) {
        Icon icon = getIconAt(c);
        icon.paintIcon(this, g, xOffset, BETWEEN_STATES_PADDING);
        xOffset += icon.getIconWidth() + BETWEEN_STATES_PADDING;
      }

      xOffset += STATES_PADDING - BETWEEN_STATES_PADDING;

      FontMetrics fm = g.getFontMetrics();
      g.setColor(getForeground());
      int yOffset = (height - fm.getHeight()) / 2 + fm.getAscent();
      g.drawString(getText(), xOffset, yOffset);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      super.setBounds(x, y, width, height);
      setIconHeight(height - 2 * BETWEEN_STATES_PADDING);
    }
  }
}
