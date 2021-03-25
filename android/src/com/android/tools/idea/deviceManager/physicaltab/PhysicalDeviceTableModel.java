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
package com.android.tools.idea.deviceManager.physicaltab;

import com.android.annotations.concurrency.UiThread;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

@UiThread
final class PhysicalDeviceTableModel extends AbstractTableModel {
  private static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  private static final int API_MODEL_COLUMN_INDEX = 1;
  private static final int TYPE_MODEL_COLUMN_INDEX = 2;
  private static final int ACTIONS_MODEL_COLUMN_INDEX = 3;

  private final @NotNull List<@NotNull PhysicalDevice> myDevices;

  PhysicalDeviceTableModel() {
    this(Collections.emptyList());
  }

  PhysicalDeviceTableModel(@NotNull List<@NotNull PhysicalDevice> devices) {
    devices.sort(null);
    myDevices = devices;
  }

  void handleConnectedDevice(@NotNull PhysicalDevice connectedDevice) {
    assert connectedDevice.isConnected();
    int modelRowIndex = PhysicalDevices.indexOf(myDevices, connectedDevice);

    if (modelRowIndex == -1) {
      myDevices.add(connectedDevice);
    }
    else {
      PhysicalDevice device = myDevices.get(modelRowIndex);

      if (device.isConnected()) {
        Logger.getInstance(PhysicalDeviceTableModel.class).warn("Connecting a connected device" + System.lineSeparator()
                                                                + device.toDebugString());
      }

      myDevices.set(modelRowIndex, connectedDevice);
    }

    myDevices.sort(null);
    fireTableDataChanged();
  }

  void handleDisconnectedDevice(@NotNull PhysicalDevice disconnectedDevice) {
    assert !disconnectedDevice.isConnected();
    int modelRowIndex = PhysicalDevices.indexOf(myDevices, disconnectedDevice);

    if (modelRowIndex == -1) {
      Logger.getInstance(PhysicalDeviceTableModel.class).warn("Disconnecting an unknown device" + System.lineSeparator()
                                                              + disconnectedDevice.toDebugString());

      return;
    }

    PhysicalDevice device = myDevices.get(modelRowIndex);

    if (!device.isConnected()) {
      Logger.getInstance(PhysicalDeviceTableModel.class).warn("Disconnecting a disconnected device" + System.lineSeparator()
                                                              + device.toDebugString());
    }

    myDevices.set(modelRowIndex, disconnectedDevice);
    myDevices.sort(null);

    fireTableDataChanged();
  }

  @NotNull Collection<@NotNull PhysicalDevice> getDevices() {
    return myDevices;
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
      case TYPE_MODEL_COLUMN_INDEX:
        return "Type";
      case ACTIONS_MODEL_COLUMN_INDEX:
        return "Actions";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex);
      case API_MODEL_COLUMN_INDEX:
        return "API";
      case TYPE_MODEL_COLUMN_INDEX:
        return "Type";
      case ACTIONS_MODEL_COLUMN_INDEX:
        // TODO You can probably throw an exception here too
        return "Actions";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }
}
