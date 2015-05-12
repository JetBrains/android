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

import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

public class ColorComponent extends JPanel {
  private static final int DISTANCE_BETWEEN_ROWS = 20;
  private static final String LABEL_TEMPLATE = "<html><b><font color=\"#6F6F6F\">%s</font></b><font color=\"#9B9B9B\">%s</font>";
  private static final int LABEL_BUTTON_GAP = 8;
  private static final int BUTTON_VERTICAL_PADDINGS = 14;
  public static final int SUM_PADDINGS = DISTANCE_BETWEEN_ROWS + LABEL_BUTTON_GAP + BUTTON_VERTICAL_PADDINGS;

  private final ColorChooserButton myColorChooserButton = new ColorChooserButton();
  private final JLabel myNameLabel = new JLabel();
  private String myValue = "";

  public ColorComponent() {
    super(new BorderLayout(0, LABEL_BUTTON_GAP));
    setBorder(BorderFactory.createMatteBorder(DISTANCE_BETWEEN_ROWS / 2, 0, DISTANCE_BETWEEN_ROWS / 2, 0, getBackground()));

    add(myNameLabel, BorderLayout.NORTH);

    myColorChooserButton.setBorder(null);
    myColorChooserButton.setBackground(JBColor.WHITE);
    myColorChooserButton.setForeground(null);
    add(myColorChooserButton, BorderLayout.CENTER);
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    if (myColorChooserButton != null) {
      myColorChooserButton.setFont(font);
    }
  }

  public void configure(final EditedStyleItem resValue, final List<Color> color) {
    String colorText = color.isEmpty() ? "" : " - " + ResourceHelper.colorToString(color.get(0));
    myNameLabel.setText(String.format(LABEL_TEMPLATE, resValue.getQualifiedName(), colorText));
    myValue = resValue.getQualifiedName();
    myColorChooserButton.configure(resValue, color);
  }

  @Override
  public void setComponentPopupMenu(JPopupMenu popup) {
    super.setComponentPopupMenu(popup);
    myColorChooserButton.setComponentPopupMenu(popup);
  }

  public String getValue() {
    return myValue;
  }

  public void addActionListener(final ActionListener listener) {
    myColorChooserButton.addActionListener(listener);
  }

  private static class ColorChooserButton extends JButton {
    private static final Logger LOG = Logger.getInstance(ColorChooserButton.class);

    private static final int BETWEEN_STATES_PADDING = 2;
    private static final int STATES_PADDING = 6;
    private static final int ARC_SIZE = 10;
    public static final double THRESHOLD_BRIGHTNESS = 0.8;

    private String myValue;
    private @NotNull List<Color> myColors = Collections.emptyList();
    private final float[] myHsbArray = new float[3];

    public void configure(final EditedStyleItem resValue, final List<Color> color) {
      myValue = resValue.getValue();
      setColors(color);
    }

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

      if (myValue == null) {
        LOG.error("Trying to draw ColorChooserButton in inconsistent state (either name or value is null)!");
        return;
      }

      final int cellSize = height - 2 * BETWEEN_STATES_PADDING;
      int xOffset = BETWEEN_STATES_PADDING;
      for (final Color color : myColors) {
        g.setColor(color);
        g.fillRoundRect(xOffset, BETWEEN_STATES_PADDING, cellSize, cellSize, ARC_SIZE, ARC_SIZE);

        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), myHsbArray);
        if (myHsbArray[2] > THRESHOLD_BRIGHTNESS) {
          // Drawing a border to avoid displaying white boxes on a white background
          g.setColor(Gray._239);
          g.drawRoundRect(xOffset, BETWEEN_STATES_PADDING, cellSize, cellSize - 1, ARC_SIZE, ARC_SIZE);
        }

        xOffset += cellSize + BETWEEN_STATES_PADDING;
      }

      xOffset += STATES_PADDING - BETWEEN_STATES_PADDING;

      FontMetrics fm = g.getFontMetrics();
      g.setColor(getForeground());
      int yOffset = (height - fm.getHeight()) / 2 + fm.getAscent();
      g.drawString(myValue, xOffset, yOffset);
    }

    public void setColors(@NotNull List<Color> colors) {
      myColors = ImmutableList.copyOf(colors);
    }
  }
}
