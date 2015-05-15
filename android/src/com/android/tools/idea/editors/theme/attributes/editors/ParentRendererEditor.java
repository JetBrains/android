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


import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.editors.theme.ParentThemesListModel;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.ComboBox;

import java.awt.Component;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Custom Renderer and Editor for the theme parent attribute.
 * Uses a dropdown to offer the choice between Material Dark, Material Light or Other.
 * Deals with Other through a separate dialog window.
 */
public class ParentRendererEditor extends TypedCellEditor<ThemeEditorStyle, AttributeEditorValue> implements TableCellRenderer {
  private final ComboBox myComboBox;
  private String myResultValue;
  private final Configuration myConfiguration;

  public ParentRendererEditor(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    myComboBox = new ComboBox();
    //noinspection GtkPreferredJComboBoxRenderer
    myComboBox.setRenderer(new StyleListCellRenderer(AndroidFacet.getInstance(configuration.getModule())));
    myComboBox.addActionListener(new ParentChoiceListener());
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component;
    if (column == 0) {
      component = table.getDefaultRenderer(String.class).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    } else {
      myComboBox.removeAllItems();
      myComboBox.addItem(value);
      component = myComboBox;
    }
    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, ThemeEditorStyle value, boolean isSelected, int row, int column) {
    ImmutableList<ThemeEditorStyle> defaultThemes = ThemeEditorUtils.getDefaultThemes(new ThemeResolver(myConfiguration));
    myComboBox.setModel(new ParentThemesListModel(defaultThemes, value));
    myResultValue = value.getName();
    return myComboBox;
  }

  @Override
  public AttributeEditorValue getEditorValue() {
    return new AttributeEditorValue(myResultValue, false);
  }

  private class ParentChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      Object selectedValue = myComboBox.getSelectedItem();
      if (ParentThemesListModel.SHOW_ALL_THEMES.equals(selectedValue)) {
        myComboBox.hidePopup();
        final ThemeSelectionDialog dialog = new ThemeSelectionDialog(myConfiguration);

        dialog.show();

        if (dialog.isOK()) {
          myResultValue = dialog.getTheme();
          stopCellEditing();
        }
        else {
          myResultValue = null;
          cancelCellEditing();
        }
      }
      else {
        if (selectedValue instanceof ThemeEditorStyle){
          myResultValue = ((ThemeEditorStyle)selectedValue).getName();
        }
        stopCellEditing();
      }
    }
  }
}
