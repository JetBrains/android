/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.android.tools.idea.uibuilder.property.ptable.PTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.TOOLS_URI;

public class NlTableCellEditor extends PTableCellEditor implements NlEditingListener, BrowsePanel.Context {
  private NlComponentEditor myEditor;
  private JTable myTable;
  private int myRow;

  public void init(@NotNull NlComponentEditor editor) {
    myEditor = editor;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;

    myEditor.setProperty((NlProperty)value);
    myTable = table;
    myRow = row;
    return myEditor.getComponent();
  }

  @Override
  public boolean isBooleanEditor() {
    return myEditor instanceof NlBooleanEditor;
  }

  @Override
  public void activate() {
    myEditor.activate();
  }

  @Override
  public Object getCellEditorValue() {
    return myEditor.getValue();
  }

  @Override
  public void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value) {
    stopCellEditing();
  }

  @Override
  public void cancelEditing(@NotNull NlComponentEditor editor) {
    cancelCellEditing();
  }

  @Nullable
  @Override
  public NlProperty getProperty() {
    return getPropertyAt(myTable, myRow);
  }

  @Nullable
  @Override
  public NlProperty getDesignProperty() {
    return getPropertyAt(myTable, myRow + 1);
  }

  @Override
  public void cancelEditing() {
    cancelCellEditing();
  }

  @Override
  public void addDesignProperty() {
    NlPropertyItem property = getPropertyAt(myTable, myRow);
    assert property != null && !TOOLS_URI.equals(property.getNamespace());
    cancelEditing();
    PTableModel model = (PTableModel)myTable.getModel();
    model.insertRow(myRow + 1, property.getDesignTimeProperty());
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> myTable.editCellAt(myRow + 1, 1));
  }

  @Override
  public void removeDesignProperty() {
    NlPropertyItem designProperty = getPropertyAt(myTable, myRow);
    assert designProperty != null && TOOLS_URI.equals(designProperty.getNamespace());
    PTableModel model = (PTableModel)myTable.getModel();
    cancelEditing();
    designProperty.setValue(null);
    model.deleteRow(myRow);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> myTable.editCellAt(myRow - 1, 1));
  }

  @Nullable
  public static NlPropertyItem getPropertyAt(@NotNull JTable table, int row) {
    if (!(table instanceof PTable && row < table.getRowCount())) {
      return null;
    }
    Object value = table.getValueAt(row, 1);
    if (value instanceof NlPropertyItem) {
      return (NlPropertyItem)value;
    }
    return null;
  }
}
