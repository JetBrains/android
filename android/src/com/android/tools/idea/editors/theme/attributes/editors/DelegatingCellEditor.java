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
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
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

  public DelegatingCellEditor(boolean convertValueToString, final TableCellEditor delegate) {
    myConvertValueToString = convertValueToString;
    myDelegate = delegate;
  }

  public DelegatingCellEditor(final TableCellEditor delegate) {
    this(true, delegate);
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
    final Object stringValue;
    final CellSpanModel model = (CellSpanModel)table.getModel();

    boolean boldFont = false;
    if (value instanceof EditedStyleItem) {
      final EditedStyleItem item = (EditedStyleItem) value;
      stringValue = ThemeEditorUtils.extractRealValue(item, model.getCellClass(row, column));
      ConfiguredThemeEditorStyle selectedStyle = ((AttributesTableModel)table.getModel()).getSelectedStyle();
      // Displays in bold attributes that are overriding their inherited value
      boldFont = selectedStyle.hasItem(item);
    }
    else {
      // Not an EditedStyleItem for theme name and theme parent.
      stringValue = value;
    }

    final Component returnedComponent =
      myDelegate.getTableCellEditorComponent(table, myConvertValueToString ? stringValue : value, isSelected, row, column);

    returnedComponent.setFont(boldFont ? returnedComponent.getFont().deriveFont(Font.BOLD) :
                              returnedComponent.getFont().deriveFont(Font.PLAIN));
    return returnedComponent;
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
