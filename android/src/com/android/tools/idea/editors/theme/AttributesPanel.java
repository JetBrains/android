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

import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;

public class AttributesPanel {
  private static final boolean ENABLE_ADVANCED_MODE = false;
  private static final int MAX_SIZE_THEME_SELECTOR = 25;

  public static final Border BORDER = JBUI.Borders.empty(10, 10);
  /* ThemeEditorConstants.ATTRIBUTE_ROW_GAP is already scaled so we use a regular BorderFactory empty border */
  public static final Border LABEL_BORDER =
    BorderFactory.createEmptyBorder(ThemeEditorConstants.ATTRIBUTE_ROW_GAP, 0, ThemeEditorConstants.ATTRIBUTE_ROW_GAP, 0);
  public static final String THEME_SELECTOR_NAME = "Theme Selector";
  public static final String MODULE_SELECTOR_NAME = "Module Selector";

  private JComboBox myThemeCombo;
  private JCheckBox myAdvancedFilterCheckBox;
  private JButton myBackButton;
  private JBLabel mySubStyleLabel;
  private ThemeEditorTable myAttributesTable;
  private final JBScrollPane myAttributesScrollPane;
  private JPanel myRightPanel;
  private JComboBox myAttrGroupCombo;
  private ColorPalette myPalette;
  private JBScrollPane myPaletteScrollPane;
  private JComboBox myModuleCombo;
  private JBLabel myThemeLabel;
  private JBLabel myModuleLabel;
  private JLabel myThemeWarning;

  public AttributesPanel() {
    myThemeCombo.setMinimumSize(ThemeEditorConstants.ATTRIBUTES_PANEL_COMBO_MIN_SIZE);
    myModuleCombo.setMinimumSize(ThemeEditorConstants.ATTRIBUTES_PANEL_COMBO_MIN_SIZE);

    myBackButton.setIcon(AllIcons.Actions.Back);
    myBackButton.setBorder(BORDER);

    myThemeLabel.setBorder(LABEL_BORDER);
    myModuleLabel.setBorder(LABEL_BORDER);
    myThemeLabel.setText(
      String.format(ThemeEditorConstants.ATTRIBUTE_LABEL_TEMPLATE, ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR), "Theme"));
    myModuleLabel.setText(
      String.format(ThemeEditorConstants.ATTRIBUTE_LABEL_TEMPLATE, ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR), "Module"));

    myPaletteScrollPane.setVisible(ENABLE_ADVANCED_MODE);
    myAdvancedFilterCheckBox.setVisible(ENABLE_ADVANCED_MODE);
    myAttrGroupCombo.setVisible(ENABLE_ADVANCED_MODE);

    new ComboboxSpeedSearch(myThemeCombo);

    myBackButton.setToolTipText("Back to the theme");

    myAttributesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myAttributesTable.setTableHeader(null);

    // TODO: TableSpeedSearch does not really support filtered tables since it incorrectly uses the model to calculate the number
    // of available cells. Fix this.
    new TableSpeedSearch(myAttributesTable) {
      @Override
      protected int getElementCount() {
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };

    mySubStyleLabel.setVisible(false);
    mySubStyleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    myPalette.setShowCheckeredBackground(true);

    // Stop the combo box long items from blocking the right panel from being able to be made small.
    myThemeCombo.setMinimumSize(new Dimension(JBUI.scale(10), myThemeCombo.getMinimumSize().height));
    myThemeCombo.setPreferredSize(new Dimension(JBUI.scale(10), myThemeCombo.getPreferredSize().height));

    myThemeCombo.setMaximumRowCount(MAX_SIZE_THEME_SELECTOR);

    // Set combo boxes names to be able to distinguish them in UI tests
    myThemeCombo.setName(THEME_SELECTOR_NAME);
    myModuleCombo.setName(MODULE_SELECTOR_NAME);

    myModuleCombo.setRenderer(new ListCellRendererWrapper<Module>() {
      @Override
      public void customize(JList list, Module value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });

    myAttributesScrollPane = new JBScrollPane(myRightPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    myAttributesTable.setBackground(null); // Get rid of default white background of the table.
    myAttributesScrollPane.setBackground(null); // needed for OS X, as by default is set to white
    myAttributesScrollPane.getViewport().setBackground(null); // needed for OS X, as by default is set to white
    myAttributesScrollPane.getVerticalScrollBar().setUnitIncrement(AndroidPreviewPanel.VERTICAL_SCROLLING_UNIT_INCREMENT);
    myAttributesScrollPane.getVerticalScrollBar().setBlockIncrement(AndroidPreviewPanel.VERTICAL_SCROLLING_BLOCK_INCREMENT);

    myThemeWarning.setIcon(AllIcons.General.BalloonWarning);
  }

  public void setThemeNamePopupMenu(@Nullable JPopupMenu popup) {
    myThemeCombo.setComponentPopupMenu(popup);
  }

  /**
   * @param themeName Does not have to be one of the items in the combo box list.
   */
  public void setSelectedTheme(@Nullable final String themeName) {
    // we set the theme on the model and not the actual combo box
    // as the model allows setting a theme that is not contained in the list, but the combo box does not.
    myThemeCombo.getModel().setSelectedItem(themeName);
    myThemeCombo.hidePopup();
  }

  public void setAdvancedMode(final boolean isAdvanced) {
    myAdvancedFilterCheckBox.setSelected(isAdvanced);
  }

  public boolean isAdvancedMode() {
    return myAdvancedFilterCheckBox.isSelected();
  }

  public void setSubstyleName(final @Nullable String substyleName) {
    if (substyleName == null) {
      mySubStyleLabel.setVisible(false);
    }
    else {
      mySubStyleLabel.setVisible(true);
      mySubStyleLabel.setText("\u27A5 " + substyleName);
    }
  }

  public void setModuleModel(@NotNull ComboBoxModel model) {
    myModuleCombo.setModel(model);
    boolean isVisible = model.getSize() > 1;
    myModuleCombo.setVisible(isVisible);
    myModuleLabel.setVisible(isVisible);
  }

  public void addModuleChangedActionListener(@NotNull ActionListener listener) {
    myModuleCombo.addActionListener(listener);
  }

  public void setShowThemeNotUsedWarning(boolean show) {
    myThemeWarning.setVisible(show);
  }

  // Raw getters ahead

  public JComboBox getThemeCombo() {
    return myThemeCombo;
  }

  public JComboBox getAttrGroupCombo() {
    return myAttrGroupCombo;
  }

  public ThemeEditorTable getAttributesTable() {
    return myAttributesTable;
  }

  public JButton getBackButton() {
    return myBackButton;
  }

  public JCheckBox getAdvancedFilterCheckBox() {
    return myAdvancedFilterCheckBox;
  }

  public JComponent getRightPanel() {
    return myAttributesScrollPane;
  }

  public ColorPalette getPalette() {
    return myPalette;
  }
}
