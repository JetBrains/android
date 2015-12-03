/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.editors.strings.FontUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;

public class StringsCellEditor extends AbstractTableCellEditor {
  private final JBTextField myTextField;

  public StringsCellEditor() {
    myTextField = new JBTextField();
    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          stopCellEditing();
          e.consume();
        }
        else {
          super.keyPressed(e);
        }
      }
    });
  }

  /**
   * @param event expected to be an ActionEvent from the Swing key bindings system or a left double click MouseEvent
   */
  @Override
  public boolean isCellEditable(EventObject event) {
    if (event instanceof MouseEvent) {
      MouseEvent mouseEvent = (MouseEvent)event;

      if (mouseEvent.getClickCount() != 2 || mouseEvent.getButton() != MouseEvent.BUTTON1) {
        return false;
      }
    }

    if (!(event.getSource() instanceof JTable)) {
      return false;
    }

    JTable source = (JTable)event.getSource();

    if (source.getSelectedRowCount() != 1 || source.getSelectedColumnCount() != 1) {
      return false;
    }

    int row = source.convertRowIndexToModel(source.getSelectedRow());
    int col = source.convertColumnIndexToModel(source.getSelectedColumn());

    if (col == ConstantColumn.KEY.ordinal()) {
      return false; // TODO: keys are not editable, we want them to be refactor operations
    }

    StringResourceTableModel model = (StringResourceTableModel)source.getModel();
    String value = (String)model.getValueAt(row, col);

    // multi line values cannot be edited inline
    return !StringsCellRenderer.shouldClip(value);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    StringResourceTableModel model = (StringResourceTableModel)table.getModel();
    row = table.convertRowIndexToModel(row);
    column = table.convertColumnIndexToModel(column);
    String v = (String)model.getValueAt(row, column);

    myTextField.setText(v);
    myTextField.setFont(FontUtil.getFontAbleToDisplay(v, myTextField.getFont()));
    return myTextField;
  }

  @Override
  public Object getCellEditorValue() {
    return myTextField.getText();
  }

  @VisibleForTesting
  public void setCellEditorValue(@NotNull Object value) {
    myTextField.setText((String)value);
  }
}
