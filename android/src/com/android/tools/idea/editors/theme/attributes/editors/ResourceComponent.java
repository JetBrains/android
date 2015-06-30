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

import com.android.tools.swing.ui.ClickableLabel;
import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWithRightAlignedComponent;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * Component for displaying a color or a drawable resource with attribute name, type and value text.
 */
public class ResourceComponent extends JPanel {

  /**
   * ResourceComponent top + bottom margins
   */
  private static final int MARGIN = JBUI.scale(20);
  /**
   * Gap between the two rows of the component
   */
  private static final int ROW_GAP = JBUI.scale(8);

  private final SwatchComponent mySwatchComponent = new SwatchComponent();
  private final ClickableLabel myNameLabel = new ClickableLabel();
  private final ComboBox myVariantCombo = new ComboBox();
  private final ListCellRenderer mySelectedVariantRenderer;
  private final ListCellRenderer myPopupVariantRenderer;

  public ResourceComponent() {
    super(new BorderLayout(0, ROW_GAP));
    setBorder(BorderFactory.createMatteBorder(MARGIN / 2, 0, MARGIN / 2, 0, getBackground()));

    DefaultListCellRenderer selectedVariantRenderer = new DefaultListCellRenderer();
    selectedVariantRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
    mySelectedVariantRenderer = selectedVariantRenderer;
    myPopupVariantRenderer = new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component popupComponent =  super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (!isSelected) {
          popupComponent.setBackground(JBColor.WHITE);
        }
        return popupComponent;
      }
    };

    ListCellRenderer variantRenderer = new ListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (index == -1) {
          return mySelectedVariantRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        return myPopupVariantRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    };

    //noinspection GtkPreferredJComboBoxRenderer
    myVariantCombo.setRenderer(variantRenderer);
    myVariantCombo.setBorder(null);

    Box topRowPanel = new Box(BoxLayout.LINE_AXIS);
    topRowPanel.add(myNameLabel);
    topRowPanel.add(myVariantCombo);
    add(topRowPanel, BorderLayout.NORTH);

    mySwatchComponent.setBackground(JBColor.WHITE);
    mySwatchComponent.setForeground(null);
    add(mySwatchComponent, BorderLayout.CENTER);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      int firstRowHeight = Math.max(getFontMetrics(getFont()).getHeight(), myVariantCombo.getPreferredSize().height);
      int secondRowHeight = mySwatchComponent.getPreferredSize().height;

      return new Dimension(0, MARGIN + ROW_GAP + firstRowHeight + secondRowHeight);
    }

    return super.getPreferredSize();
  }

  public void setSwatchIcons(@NotNull List<SwatchComponent.SwatchIcon> icons) {
    mySwatchComponent.setSwatchIcons(icons);
  }

  public void setNameText(@NotNull String name) {
    myNameLabel.setText(name);
  }

  @NotNull
  public ComboBoxModel getVariantsModel() {
    return myVariantCombo.getModel();
  }

  public void setVariantsModel(@Nullable ComboBoxModel comboBoxModel) {
    myVariantCombo.setModel(comboBoxModel != null ? comboBoxModel : new DefaultComboBoxModel());
  }

  public void addVariantItemListener(@NotNull ItemListener itemListener) {
    myVariantCombo.addItemListener(itemListener);
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
