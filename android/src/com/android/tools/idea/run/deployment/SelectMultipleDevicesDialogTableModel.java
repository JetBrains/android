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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class SelectMultipleDevicesDialogTableModel extends AbstractTableModel {
  static final int SELECTED_MODEL_COLUMN_INDEX = 0;
  static final int TYPE_MODEL_COLUMN_INDEX = 1;
  private static final int DEVICE_MODEL_COLUMN_INDEX = 2;
  private static final int SERIAL_NUMBER_MODEL_COLUMN_INDEX = 3;
  private static final int BOOT_OPTION_MODEL_COLUMN_INDEX = 4;

  private final @NotNull List<SelectMultipleDevicesDialogTableModelRow> myRows;

  @NotNull
  private final Multiset<String> myDeviceNameMultiset;

  SelectMultipleDevicesDialogTableModel(@NotNull List<Device> devices,
                                        @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet) {
    devices.sort(new DeviceComparator());
    myRows = new ArrayList<>();

    for (Device device : devices) {
      for (Target target : device.getTargets()) {
        myRows.add(new SelectMultipleDevicesDialogTableModelRow(device, selectDeviceSnapshotComboBoxSnapshotsEnabledGet, target));
      }
    }

    myDeviceNameMultiset = devices.stream()
      .map(Device::getName)
      .collect(Collectors.toCollection(() -> HashMultiset.create(devices.size())));
  }

  @NotNull Set<Target> getSelectedTargets() {
    return myRows.stream()
      .filter(SelectMultipleDevicesDialogTableModelRow::isSelected)
      .map(SelectMultipleDevicesDialogTableModelRow::getTarget)
      .collect(Collectors.toSet());
  }

  void setSelectedTargets(@NotNull Set<Target> selectedTargets) {
    IntStream.range(0, myRows.size())
      .filter(modelRowIndex -> selectedTargets.contains(myRows.get(modelRowIndex).getTarget()))
      .forEach(modelRowIndex -> setValueAt(true, modelRowIndex, SELECTED_MODEL_COLUMN_INDEX));
  }

  @Override
  public int getRowCount() {
    return myRows.size();
  }

  @Override
  public int getColumnCount() {
    return 5;
  }

  @NotNull
  @Override
  public String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return "";
      case TYPE_MODEL_COLUMN_INDEX:
        return "Type";
      case DEVICE_MODEL_COLUMN_INDEX:
        return "Device";
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
        return "Serial Number";
      case BOOT_OPTION_MODEL_COLUMN_INDEX:
        return "Boot Option";
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
      case TYPE_MODEL_COLUMN_INDEX:
        return Icon.class;
      case DEVICE_MODEL_COLUMN_INDEX:
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
      case BOOT_OPTION_MODEL_COLUMN_INDEX:
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
      case TYPE_MODEL_COLUMN_INDEX:
      case DEVICE_MODEL_COLUMN_INDEX:
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
      case BOOT_OPTION_MODEL_COLUMN_INDEX:
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
        return myRows.get(modelRowIndex).isSelected();
      case TYPE_MODEL_COLUMN_INDEX:
        return myRows.get(modelRowIndex).getDevice().getIcon();
      case DEVICE_MODEL_COLUMN_INDEX:
        return myRows.get(modelRowIndex).getDeviceCellText();
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
        return getSerialNumber(myRows.get(modelRowIndex).getDevice());
      case BOOT_OPTION_MODEL_COLUMN_INDEX:
        return myRows.get(modelRowIndex).getBootOption();
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  private Object getSerialNumber(@NotNull Device device) {
    if (!(device instanceof PhysicalDevice)) {
      return "";
    }

    if (myDeviceNameMultiset.count(device.getName()) != 1) {
      return device.getKey().toString();
    }

    return "";
  }

  @Override
  public void setValueAt(@NotNull Object value, int modelRowIndex, int modelColumnIndex) {
    myRows.get(modelRowIndex).setSelected((Boolean)value);
    fireTableCellUpdated(modelRowIndex, modelColumnIndex);
  }
}
