/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class SelectDeploymentTargetsDialogTableModel extends AbstractTableModel {
  static final int SELECTED_MODEL_COLUMN_INDEX = 0;
  static final int ICON_MODEL_COLUMN_INDEX = 1;
  private static final int NAME_MODEL_COLUMN_INDEX = 2;

  @NotNull
  private final List<Device> myDevices;

  @NotNull
  private final JTable myTable;

  SelectDeploymentTargetsDialogTableModel(@NotNull Project project, @NotNull JTable table) {
    myDevices = ServiceManager.getService(project, AsyncDevicesGetter.class).get();
    myDevices.sort(new DeviceComparator());

    myTable = table;
  }

  @NotNull
  Device getDeviceAt(int modelRowIndex) {
    return myDevices.get(modelRowIndex);
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @NotNull
  @Override
  public String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return "";
      case ICON_MODEL_COLUMN_INDEX:
        return "Type";
      case NAME_MODEL_COLUMN_INDEX:
        return "Device name";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  @Override
  public Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return Boolean.class;
      case ICON_MODEL_COLUMN_INDEX:
        return Icon.class;
      case NAME_MODEL_COLUMN_INDEX:
        return Object.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return true;
      case ICON_MODEL_COLUMN_INDEX:
      case NAME_MODEL_COLUMN_INDEX:
        return false;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  @Override
  public Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return myTable.isRowSelected(myTable.convertRowIndexToView(modelRowIndex));
      case ICON_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex).getIcon();
      case NAME_MODEL_COLUMN_INDEX:
        return Devices.getText(myDevices.get(modelRowIndex), myDevices);
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public void setValueAt(@NotNull Object value, int modelRowIndex, int modelColumnIndex) {
    int viewRowIndex = myTable.convertRowIndexToView(modelRowIndex);

    if ((boolean)value) {
      myTable.addRowSelectionInterval(viewRowIndex, viewRowIndex);
    }
    else {
      myTable.removeRowSelectionInterval(viewRowIndex, viewRowIndex);
    }

    fireTableCellUpdated(modelRowIndex, modelColumnIndex);
  }
}
