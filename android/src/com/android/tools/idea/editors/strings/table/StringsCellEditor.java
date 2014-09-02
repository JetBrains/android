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

import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.AbstractTableCellEditor;

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

  @Override
  public boolean isCellEditable(EventObject e) {
    return e instanceof MouseEvent && ((MouseEvent)e).getClickCount() == 2 && ((MouseEvent)e).getButton() == MouseEvent.BUTTON1;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof String;
    myTextField.setText((String)value);
    return myTextField;
  }

  @Override
  public Object getCellEditorValue() {
    return myTextField.getText();
  }
}
