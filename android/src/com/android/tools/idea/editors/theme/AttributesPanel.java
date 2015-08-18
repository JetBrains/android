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

import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Dimension;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;

public class AttributesPanel {
  public static final Border BORDER = BorderFactory.createEmptyBorder(10, 10, 10, 10);

  private JComboBox myThemeCombo;
  private JCheckBox myAdvancedFilterCheckBox;
  private JButton myBackButton;
  private JBLabel mySubStyleLabel;
  private ThemeEditorTable myAttributesTable;
  private JBScrollPane myAttributesScrollPane;
  private JPanel myConfigToolbar;
  private JPanel myRightPanel;
  private JComboBox myAttrGroupCombo;
  private ColorPalette myPalette;
  private JBScrollPane myPaletteScrollPane;

  public AttributesPanel() {
    myBackButton.setIcon(AllIcons.Actions.Back);
    myBackButton.setBorder(BORDER);

    myPaletteScrollPane.setVisible(false);
    myAdvancedFilterCheckBox.setVisible(false);
    myAttrGroupCombo.setVisible(false);

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
    myThemeCombo.setMinimumSize(new Dimension(10, myThemeCombo.getMinimumSize().height));
    myThemeCombo.setPreferredSize(new Dimension(10, myThemeCombo.getPreferredSize().height));
  }

  /**
   * @param theme Does not have to be one of the items in the combo box list.
   */
  public void setSelectedTheme(final ThemeEditorStyle theme) {
    // we set the theme on the model and not the actual combo box
    // as the model allows setting a theme that is not contained in the list, but the combo box does not.
    myThemeCombo.getModel().setSelectedItem(theme);
  }

  public boolean isCreateNewThemeSelected() {
    return ThemesListModel.CREATE_NEW_THEME.equals(myThemeCombo.getSelectedItem());
  }

  public boolean isShowAllThemesSelected() {
    return ThemesListModel.SHOW_ALL_THEMES.equals(myThemeCombo.getSelectedItem());
  }

  public boolean isRenameSelected() {
    Object selectedItem = myThemeCombo.getSelectedItem();
    if (!(selectedItem instanceof String)) {
      // Selected themes are instances of EditedStyleItem
      // So this method will return false on selecting a theme
      return false;
    }
    return ((String)selectedItem).startsWith(ThemesListModel.RENAME);
  }

  public ThemeEditorStyle getSelectedTheme() {
    Object item = myThemeCombo.getSelectedItem();
    if (item instanceof ThemeEditorStyle) {
      return (ThemeEditorStyle)item;
    }
    else if (item instanceof ProjectThemeResolver.ThemeWithSource) {
      return ((ProjectThemeResolver.ThemeWithSource)item).getTheme();
    }

    throw new IllegalStateException("getSelectedTheme() is requested on themes combo while selected item is not theme");
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
    } else {
      mySubStyleLabel.setVisible(true);
      mySubStyleLabel.setText("\u27A5 " + substyleName);
    }
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

  public JBScrollPane getAttributesScrollPane() {
    return myAttributesScrollPane;
  }

  public JPanel getRightPanel() {
    return myRightPanel;
  }

  public JPanel getConfigToolbar() {
    return myConfigToolbar;
  }

  public JBScrollPane getPaletteScrollPane() {
    return myPaletteScrollPane;
  }

  public ColorPalette getPalette() {
    return myPalette;
  }
}
