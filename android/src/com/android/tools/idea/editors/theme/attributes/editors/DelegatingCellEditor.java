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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import spantable.CellSpanModel;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.EventObject;

/**
 * Cell editor used to replace {@link ItemResourceValue} with extracted "simple" value,
 * which is usually a String or a Boolean, with the purpose of using standard JTable cell
 * editors, such as checkbox for Boolean values
 */
public class DelegatingCellEditor implements TableCellEditor {
  private final TableCellEditor myDelegate;
  private final boolean myConvertValueToString;

  DelegatingCellEditor(boolean convertValueToString, final TableCellEditor delegate) {
    myConvertValueToString = convertValueToString;
    myDelegate = delegate;
  }

  DelegatingCellEditor(final TableCellEditor delegate) {
    this(true, delegate);
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
    final Object stringValue;
    final CellSpanModel model = (CellSpanModel)table.getModel();

    if (column == 1 && value instanceof EditedStyleItem) {
      final ItemResourceValue resValue = ((EditedStyleItem)value).getItemResourceValue();
      stringValue = AttributesTableModel.extractRealValue(resValue, model.getCellClass(row, column));
    }
    else {
      stringValue = value;
    }

    return myDelegate.getTableCellEditorComponent(table, myConvertValueToString ? stringValue : value, isSelected, row, column);
  }

  @Override
  public Object getCellEditorValue() {
    return myDelegate.getCellEditorValue();
  }

  @Override
  public boolean isCellEditable(final EventObject anEvent) {
    return myDelegate.isCellEditable(anEvent);
  }

  @Override
  public boolean shouldSelectCell(final EventObject anEvent) {
    return myDelegate.shouldSelectCell(anEvent);
  }

  @Override
  public boolean stopCellEditing() {
    return myDelegate.stopCellEditing();
  }

  @Override
  public void cancelCellEditing() {
    myDelegate.cancelCellEditing();
  }

  @Override
  public void addCellEditorListener(final CellEditorListener l) {
    myDelegate.addCellEditorListener(l);
  }

  @Override
  public void removeCellEditorListener(final CellEditorListener l) {
    myDelegate.removeCellEditorListener(l);
  }
}
