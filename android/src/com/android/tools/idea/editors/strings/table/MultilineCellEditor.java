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

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AbstractTableCellEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

public class MultilineCellEditor extends AbstractTableCellEditor {
  private static final Dimension ZERO = new Dimension(0, 0);
  private final JTextArea myArea;
  private final JBScrollPane myScrollPane;

  public MultilineCellEditor() {
    myArea = new JTextArea();
    myScrollPane = new JBScrollPane(myArea);
    myScrollPane.getVerticalScrollBar().setPreferredSize(ZERO);
    myScrollPane.getHorizontalScrollBar().setPreferredSize(ZERO);
  }

  @Override
  public boolean isCellEditable(EventObject e) {
    return false;
    // TODO: enable inline editing..
    // edit only on double click
    // return e instanceof MouseEvent && ((MouseEvent)e).getClickCount() == 2 && ((MouseEvent)e).getButton() == MouseEvent.BUTTON1;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    myArea.setText(String.valueOf(((StringResourceTableModel)table.getModel()).getValue(row, column)));
    return myScrollPane;
  }

  @Override
  public Object getCellEditorValue() {
    return myArea.getText();
  }
}
