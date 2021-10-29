/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Device;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

@UiThread
final class VirtualDeviceTableModel extends AbstractTableModel {
  static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  private static final int API_MODEL_COLUMN_INDEX = 1;
  private static final int SIZE_ON_DISK_MODEL_COLUMN_INDEX = 2;
  private static final int ACTIONS_MODEL_COLUMN_INDEX = 3;

  private @NotNull List<@NotNull AvdInfo> myDevices = Collections.emptyList();
  private final @NotNull Map<@NotNull AvdInfo, @NotNull SizeOnDisk> myDeviceToSizeOnDiskMap = new HashMap<>();

  private static final class Actions {
    private static final Actions INSTANCE = new Actions();

    private Actions() {
    }
  }

  @NotNull List<@NotNull AvdInfo> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<@NotNull AvdInfo> devices) {
    myDevices = devices;
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 4;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return "Device";
      case API_MODEL_COLUMN_INDEX:
        return "API";
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return "Size on Disk";
      case ACTIONS_MODEL_COLUMN_INDEX:
        return "Actions";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return Device.class;
      case API_MODEL_COLUMN_INDEX:
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return Object.class;
      case ACTIONS_MODEL_COLUMN_INDEX:
        return Actions.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    return modelColumnIndex == ACTIONS_MODEL_COLUMN_INDEX;
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex);
      case API_MODEL_COLUMN_INDEX:
        return VirtualDevices.build(myDevices.get(modelRowIndex)).getApi();
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return myDeviceToSizeOnDiskMap.computeIfAbsent(myDevices.get(modelRowIndex), device -> new SizeOnDisk(device, this));
      case ACTIONS_MODEL_COLUMN_INDEX:
        return Actions.INSTANCE;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }
}
