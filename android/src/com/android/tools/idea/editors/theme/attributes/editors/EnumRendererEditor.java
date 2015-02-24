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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Table Cell Editor and Renderer for enumerations
 * Uses a ComboBox for a dropdown list of choices
 * In the case where other values are allowed, makes the ComboBox editable
 */
public class EnumRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  private final ComboBox myComboBox;
  private final AttributeDefinitions myAttributeDefinitions;

  public EnumRendererEditor(AttributeDefinitions attributeDefinitions) {
    myAttributeDefinitions = attributeDefinitions;
    myComboBox = new ComboBox();
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopCellEditing();  // registers the selection as soon as clicked
      }
    });
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    EditedStyleItem item = (EditedStyleItem) value;
    String itemValue = item.getValue();
    myComboBox.removeAllItems();
    myComboBox.addItem(itemValue);
    myComboBox.setSelectedItem(itemValue);
    return myComboBox;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    EditedStyleItem item = (EditedStyleItem) value;
    AttributeDefinition attrDefinition = myAttributeDefinitions.getAttrDefByName(item.getName());
    if (attrDefinition != null) {
      if (attrDefinition.getFormats().size() > 1) {
        myComboBox.setEditable(true); // makes the box editable for items that can take values outside of the choices
      } else {
        myComboBox.setEditable(false);
      }
      myComboBox.setModel(new DefaultComboBoxModel(attrDefinition.getValues()));
    }
    myComboBox.setSelectedItem(item.getValue());
    return myComboBox;
  }

  @Override
  public Object getCellEditorValue() {
    return myComboBox.getSelectedItem();
  }
}
