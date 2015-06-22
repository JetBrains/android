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

import com.android.tools.swing.ClickableLabel;
import com.android.tools.swing.SwatchComponent;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Component for displaying a color or a drawable resource with attribute name, type and value text.
 */
public class ResourceComponent extends JPanel {

  /**
   * ResourceComponent top + bottom margins
   */
  private static final int MARGIN = 20;
  /**
   * Gap between the two rows of the component
   */
  private static final int ROW_GAP = 8;

  private final SwatchComponent mySwatchComponent = new SwatchComponent();
  private final ClickableLabel myNameLabel = new ClickableLabel();

  public ResourceComponent() {
    super(new BorderLayout(0, ROW_GAP));
    setBorder(BorderFactory.createMatteBorder(MARGIN / 2, 0, MARGIN / 2, 0, getBackground()));

    add(myNameLabel, BorderLayout.NORTH);

    mySwatchComponent.setBackground(JBColor.WHITE);
    mySwatchComponent.setForeground(null);
    add(mySwatchComponent, BorderLayout.CENTER);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      return new Dimension(0, MARGIN + ROW_GAP + getFontMetrics(getFont()).getHeight() + mySwatchComponent.getPreferredSize().height);
    }

    return super.getPreferredSize();
  }

  public void setSwatchIcons(@NotNull List<SwatchComponent.SwatchIcon> icons) {
    mySwatchComponent.setSwatchIcons(icons);
  }

  public void setNameText(@NotNull String name) {
    myNameLabel.setText(name);
  }

  public void setValueText(@NotNull String value) {
    mySwatchComponent.setText(value);
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    // We need a null check here as this can be called from the setUI that's called from the constructor.
    if (mySwatchComponent != null) {
      mySwatchComponent.setFont(font);
    }
  }

  @Override
  public void setComponentPopupMenu(JPopupMenu popup) {
    super.setComponentPopupMenu(popup);
    myNameLabel.setComponentPopupMenu(popup);
    mySwatchComponent.setComponentPopupMenu(popup);
  }

  public void addActionListener(final ActionListener listener) {
    myNameLabel.addActionListener(listener);
    mySwatchComponent.addActionListener(listener);
  }
}
