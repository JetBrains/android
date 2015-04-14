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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.AbstractTableCellEditor;

import java.awt.Component;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.jetbrains.annotations.NotNull;

/**
 * Custom Renderer and Editor for the theme parent attribute.
 * Uses a dropdown to offer the choice between Material Dark, Material Light or Other.
 * Deals with Other through a separate dialog window.
 */
public class ParentRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  private static final String OTHER = "Other ...";
  private static final String DARK = "@android:style/Theme.Material.NoActionBar";
  private static final String LIGHT = "@android:style/Theme.Material.Light.NoActionBar";
  private static final String[] COMBOBOX_OPTIONS = {DARK, LIGHT, OTHER};

  private final ComboBox myComboBox;
  private String myResultValue;
  private final Configuration myConfiguration;

  public ParentRendererEditor(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    myComboBox = new ComboBox();
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
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof String)) {
      return null;
    }

    String stringValue = (String)value;
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel(COMBOBOX_OPTIONS);
    if (!(stringValue.equals(DARK) || stringValue.equals(LIGHT))) {
      comboBoxModel.insertElementAt(stringValue, 0);
    }
    myComboBox.setModel(comboBoxModel);
    myComboBox.setSelectedItem(stringValue);
    return myComboBox;
  }

  @Override
  public Object getCellEditorValue() {
    return myResultValue;
  }

  private class ParentChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      String selectedValue = (String)myComboBox.getSelectedItem();
      if (OTHER.equals(selectedValue)) {
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
      } else {
        myResultValue = selectedValue;
        stopCellEditing();
      }
    }
  }
}
