/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.groups.create;

import com.android.tools.idea.deviceManager.groups.GroupableDevice;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class GroupableDeviceTableModel extends AbstractTableModel {
  private final @NotNull List<@NotNull GroupableDevice> myGroupableDevices;

  GroupableDeviceTableModel() {
    this(new ArrayList<>());
  }

  GroupableDeviceTableModel(@NotNull List<@NotNull GroupableDevice> avds) {
    myGroupableDevices = avds;
  }

  @Override
  public int getRowCount() {
    return myGroupableDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case 0:
        return "Device";
      case 1:
        return "Type";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case 0:
        return GroupableDevice.class;
      case 1:
        return String.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    GroupableDevice groupableDevice = myGroupableDevices.get(modelRowIndex);
    switch (modelColumnIndex) {
      case 0:
        return groupableDevice;
      case 1:
        return groupableDevice.getType();
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  /* Table manipulation methods */

  @NotNull GroupableDevice getDeviceAt(int modelRowIndex) {
    return myGroupableDevices.get(modelRowIndex);
  }

  void addDevice(@NotNull GroupableDevice device) {
    myGroupableDevices.add(device);
    int modelRowIndex = myGroupableDevices.size() - 1;
    fireTableRowsInserted(modelRowIndex, modelRowIndex);
  }

  void removeRow(int modelRowIndex) {
    myGroupableDevices.remove(modelRowIndex);
    fireTableRowsDeleted(modelRowIndex, modelRowIndex);
  }

  @NotNull List<@NotNull GroupableDevice> getGroupableDevices() {
    return myGroupableDevices;
  }
}
