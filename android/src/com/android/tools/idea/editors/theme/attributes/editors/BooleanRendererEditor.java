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


import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.uipreview.ChooseResourceDialog;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Custom Renderer and Editor for values of boolean attributes
 * Uses a dropdown to offer the choice between true or false or having a reference
 * Deals with references through a separate dialog window
 */
public class BooleanRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  private static final String USE_REFERENCE = "Use reference ...";
  private static final ResourceType[] BOOLEAN_TYPE = new ResourceType[] { ResourceType.BOOL };
  private static final String[] COMBOBOX_OPTIONS = {"true", "false", USE_REFERENCE};

  private final ComboBox myComboBox;
  private String myResultValue;
  private String myEditedItemValue;
  private final Module myModule;

  public BooleanRendererEditor(Module module) {
    myModule = module;
    myComboBox = new ComboBox();
    myComboBox.addActionListener(new BooleanChoiceListener());
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    EditedStyleItem item = (EditedStyleItem) value;
    myComboBox.removeAllItems();
    myComboBox.addItem(item.getValue());
    return myComboBox;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    EditedStyleItem item = (EditedStyleItem) value;
    myEditedItemValue = item.getValue();
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel(COMBOBOX_OPTIONS);
    if (!(myEditedItemValue.equals("true") || myEditedItemValue.equals("false"))) {
      comboBoxModel.insertElementAt(myEditedItemValue, 0);
    }
    myComboBox.setModel(comboBoxModel);
    myComboBox.setSelectedItem(myEditedItemValue);
    return myComboBox;
  }

  @Override
  public Object getCellEditorValue() {
    return myResultValue;
  }

  private class BooleanChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      String selectedValue = (String) myComboBox.getSelectedItem();
      if (USE_REFERENCE.equals(selectedValue)) {
        final ChooseResourceDialog dialog = new ChooseResourceDialog(myModule, BOOLEAN_TYPE, myEditedItemValue, null);

        dialog.show();

        if (dialog.isOK()) {
          myResultValue = dialog.getResourceName();
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
