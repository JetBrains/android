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

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

public class ColorComponent extends JPanel {
  private static final int PADDING = 5;

  private final ColorChooserButton myColorChooserButton;
  private final JLabel myNameLabel;
  private final JLabel myValueLabel;

  public ColorComponent() {
    super(new BorderLayout(0, PADDING));

    MatteBorder matteBorder = BorderFactory.createMatteBorder(PADDING, PADDING, PADDING, PADDING, getBackground());
    setBorder(matteBorder);

    myNameLabel = new JLabel("Name");
    myValueLabel = new JLabel("Value");
    myValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    final JPanel innerPanel = new JPanel(new BorderLayout());
    innerPanel.add(myNameLabel, BorderLayout.WEST);
    innerPanel.add(myValueLabel, BorderLayout.EAST);

    add(innerPanel, BorderLayout.NORTH);

    myColorChooserButton = new ColorChooserButton();
    myColorChooserButton.setBorder(null);
    add(myColorChooserButton, BorderLayout.CENTER);
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    if (myColorChooserButton != null) {
      myColorChooserButton.setFont(font);
    }
  }

  public void setButtonBackground(@NotNull final Color color) {
    myColorChooserButton.setBackground(color);
  }

  public void configure(final EditedStyleItem resValue, final List<Color> color) {
    myNameLabel.setText(ThemeEditorUtils.getDisplayHtml(resValue));
    myValueLabel.setText(color.isEmpty() ? "" : ResourceHelper.colorToString(color.get(0)));
    myColorChooserButton.configure(resValue, color);
  }

  public String getValue() {
    return myValueLabel.getText();
  }

  public void addActionListener(final ActionListener listener) {
    myColorChooserButton.addActionListener(listener);
  }

  private static class ColorChooserButton extends JButton {
    private static final Logger LOG = Logger.getInstance(ColorChooserButton.class);

    private static final int TEXT_PADDING = 3;
    private static final int STATES_PADDING = 5;

    private String myValue;
    private @NotNull List<Color> myColors = Collections.emptyList();
    private boolean myIsPublic;

    public void configure(final EditedStyleItem resValue, final List<Color> color) {
      myValue = resValue.getValue();
      myIsPublic = resValue.isPublicAttribute();
      setColors(color);
    }

    @Override
    protected void paintComponent(Graphics g) {
      // Background is filled manually here instead of calling super.paintComponent()
      // because some L'n'Fs (e.g. GTK+) paint additional decoration even with null border.
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      if (myValue == null) {
        LOG.error("Trying to draw ColorChooserButton in inconsistent state (either name or value is null)!");
        return;
      }

      final int width = getWidth();
      final int height = getHeight();

      final int cellSize = height - 2 * STATES_PADDING;
      int xOffset = STATES_PADDING;
      for (final Color color : myColors) {
        g.setColor(color);
        g.fillRect(xOffset, STATES_PADDING, cellSize, cellSize);

        g.setColor(JBColor.BLACK);
        g.drawRect(xOffset, STATES_PADDING, cellSize, cellSize);

        xOffset += cellSize + STATES_PADDING;
      }

      FontMetrics fm = g.getFontMetrics();
      g.setColor(JBColor.BLACK);
      g.drawString(myValue, xOffset, height - TEXT_PADDING - fm.getDescent());

      // If the attribute is private or there are no colors, draw a cross on attribute cell.
      if (!myIsPublic || myColors.isEmpty()) {
        GraphicsUtil.drawCross(g, new Rectangle(0, 0, width, height), 0.5f);
      }
    }

    public void setColors(@NotNull List<Color> colors) {
      myColors = ImmutableList.copyOf(colors);
    }
  }
}
