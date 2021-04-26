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
package com.android.tools.idea.devicemanager.groups;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class DeviceGroupsTableModel extends AbstractTableModel {
  private final @NotNull List<@NotNull DeviceGroup> myDeviceGroups;

  DeviceGroupsTableModel(@NotNull List<@NotNull DeviceGroup> groups) {
    myDeviceGroups = groups;
  }

  @Override
  public int getRowCount() {
    return myDeviceGroups.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case 0:
        return "Group";
      case 1:
        return "Actions";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case 0:
        return DeviceGroup.class;
      case 1:
        return String.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    DeviceGroup group = myDeviceGroups.get(modelRowIndex);
    switch (modelColumnIndex) {
      case 0:
        return group;
      case 1:
        return "TODO"; // TODO: add action buttons
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }
}
