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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellEditor;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.TOOLS_URI;

public class NlTableCellEditor extends PTableCellEditor implements NlEditingListener, BrowsePanel.Context {
  protected NlComponentEditor myEditor;
  protected BrowsePanel myBrowsePanel;
  protected JTable myTable;
  protected int myRow;

  public void init(@NotNull NlComponentEditor editor, @Nullable BrowsePanel browsePanel) {
    myEditor = editor;
    myBrowsePanel = browsePanel;
  }

  @Override
  public Component getTableCellEditorComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;

    myEditor.setProperty((NlProperty)value);
    startCellEditing(table, row);
    if (myBrowsePanel != null) {
      myBrowsePanel.setDesignState(getDesignState(table, row));
    }
    return myEditor.getComponent();
  }

  protected void startCellEditing(@NotNull JTable table, int row) {
    myTable = table;
    myRow = row;
  }

  @VisibleForTesting
  @Nullable
  JTable getTable() {
    return myTable;
  }

  @VisibleForTesting
  int getRow() {
    return myRow;
  }

  protected final NlComponentEditor getEditor() {
    return myEditor;
  }

  @Override
  public boolean stopCellEditing() {
    if (myTable != null) {
      // Bug: 231647 Move focus back to table before hiding the editor
      myTable.requestFocus();
      if (!super.stopCellEditing()) {
        return false;
      }
      myTable = null;
      myRow = -1;
      myEditor.setProperty(EmptyProperty.INSTANCE);
    }
    return true;
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

  @Override
  public void cancelEditing() {
    cancelCellEditing();
  }

  @Override
  public void addDesignProperty() {
    cancelEditing();
    addDesignProperty(myTable, myRow);
  }

  @Override
  public void removeDesignProperty() {
    cancelEditing();
    removeDesignProperty(myTable, myRow);
  }

  public static PropertyDesignState getDesignState(@NotNull JTable table, int row) {
    NlProperty property = getPropertyAt(table, row);
    if (property == null) {
      return PropertyDesignState.NOT_APPLICABLE;
    }
    if (TOOLS_URI.equals(property.getNamespace())) {
      NlProperty runtimeProperty = getRuntimeProperty(table, row);
      return runtimeProperty != null ? PropertyDesignState.IS_REMOVABLE_DESIGN_PROPERTY : PropertyDesignState.NOT_APPLICABLE;
    }
    else {
      NlProperty designProperty = getDesignProperty(table, row);
      return designProperty != null ? PropertyDesignState.HAS_DESIGN_PROPERTY : PropertyDesignState.MISSING_DESIGN_PROPERTY;
    }
  }

  @Nullable
  public static NlPropertyItem getPropertyAt(@NotNull JTable table, int row) {
    if (!(table instanceof PTable && row >= 0 && row < table.getRowCount())) {
      return null;
    }
    Object value = table.getValueAt(row, 1);
    if (value instanceof NlPropertyItem) {
      return (NlPropertyItem)value;
    }
    return null;
  }

  @Nullable
  public static NlProperty getRuntimeProperty(@NotNull JTable table, int row) {
    NlPropertyItem currentProperty = getPropertyAt(table, row);
    NlPropertyItem previousProperty = getPropertyAt(table, getPreviousPropertyRow(currentProperty, table, row));
    if (currentProperty != null &&
        previousProperty != null &&
        previousProperty.getName().equals(currentProperty.getName()) &&
        TOOLS_URI.equals(currentProperty.getNamespace()) &&
        !TOOLS_URI.equals(previousProperty.getNamespace())) {
      return previousProperty;
    }
    return null;
  }

  @Nullable
  public static NlProperty getDesignProperty(@NotNull JTable table, int row) {
    NlPropertyItem currentProperty = getPropertyAt(table, row);
    NlPropertyItem nextProperty = getPropertyAt(table, getNextPropertyRow(currentProperty, row));
    if (currentProperty != null &&
        nextProperty != null &&
        nextProperty.getName().equals(currentProperty.getName()) &&
        !TOOLS_URI.equals(currentProperty.getNamespace()) &&
        TOOLS_URI.equals(nextProperty.getNamespace())) {
      return nextProperty;
    }
    return null;
  }

  public static void addDesignProperty(@NotNull JTable table, int row) {
    NlPropertyItem property = getPropertyAt(table, row);
    assert property != null && !TOOLS_URI.equals(property.getNamespace());
    assert getDesignProperty(table, row) == null;
    PTableModel model = (PTableModel)table.getModel();
    int nextRow = getNextPropertyRow(property, row);
    model.insertRow(nextRow, property.getDesignTimeProperty());
    if (property.isExpanded()) {
      model.expand(nextRow);
    }
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> table.editCellAt(row + 1, 1));
  }

  public static void removeDesignProperty(@NotNull JTable table, int row) {
    NlPropertyItem designProperty = getPropertyAt(table, row);
    assert designProperty != null && TOOLS_URI.equals(designProperty.getNamespace());
    int previousRow = getPreviousPropertyRow(designProperty, table, row);
    PTableModel model = (PTableModel)table.getModel();
    designProperty.setValue(null);
    designProperty.delete();
    model.collapse(row);
    model.deleteRow(row);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> table.editCellAt(previousRow, 1));
  }

  private static int getNextPropertyRow(@Nullable NlPropertyItem currentProperty, int row) {
    if (currentProperty == null) {
      return -1;
    }
    return row + 1 + (currentProperty.isExpanded() ? currentProperty.getChildren().size() : 0);
  }

  private static int getPreviousPropertyRow(@Nullable NlPropertyItem currentProperty, @NotNull JTable table, int row) {
    if (currentProperty == null || row == 0) {
      return -1;
    }
    PTableItem previous = (PTableItem)table.getValueAt(row - 1, 1);
    if (previous.getParent() != null && currentProperty.getParent() == null) {
      return row - previous.getParent().getChildren().size() - 1;
    }
    return row - 1;
  }
}
