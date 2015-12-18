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

import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.android.dom.attrs.AttributeDefinition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Table Cell Editor and Renderer for enumerations
 * Uses a ComboBox for a dropdown list of choices
 * In the case where other values are allowed, makes the ComboBox editable
 */
public class EnumRendererEditor extends TypedCellRendererEditor<EditedStyleItem, String> {
  private final ComboBox myComboBox;

  public EnumRendererEditor() {
    // Override isShowing because of the use of a {@link CellRendererPane}
    myComboBox = new ComboBox() {
      @Override
      public boolean isShowing() {
        return true;
      }
    };
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopCellEditing();  // registers the selection as soon as clicked
      }
    });
  }

  @Override
  public Component getRendererComponent(JTable table, EditedStyleItem value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component;
    if (column == 0) {
      component = table.getDefaultRenderer(String.class).getTableCellRendererComponent(table, ThemeEditorUtils.getDisplayHtml(value), isSelected, hasFocus, row, column);
    } else {
      String itemValue = value.getValue();
      myComboBox.removeAllItems();
      myComboBox.addItem(itemValue);
      myComboBox.setSelectedItem(itemValue);
      component = myComboBox;
    }

    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    AttributeDefinition attrDefinition =
      ResolutionUtils.getAttributeDefinition(value.getSourceStyle().getConfiguration(), value.getSelectedValue());
    if (attrDefinition != null) {
      if (attrDefinition.getFormats().size() > 1) {
        myComboBox.setEditable(true); // makes the box editable for items that can take values outside of the choices
      } else {
        myComboBox.setEditable(false);
      }
      myComboBox.setModel(new DefaultComboBoxModel(attrDefinition.getValues()));
    }
    myComboBox.setSelectedItem(value.getValue());
    return myComboBox;
  }

  @Override
  public String getEditorValue() {
    return (String)myComboBox.getSelectedItem();
  }
}
