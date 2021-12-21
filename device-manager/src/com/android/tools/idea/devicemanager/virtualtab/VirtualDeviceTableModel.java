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
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

@UiThread
final class VirtualDeviceTableModel extends AbstractTableModel {
  private static final boolean SPLIT_ACTIONS_ENABLED = false;

  static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  static final int API_MODEL_COLUMN_INDEX = 1;
  static final int SIZE_ON_DISK_MODEL_COLUMN_INDEX = 2;
  static final int ACTIONS_MODEL_COLUMN_INDEX = 3;

  private static final int LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX = 3;
  private static final int ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX = 4;
  private static final int EDIT_MODEL_COLUMN_INDEX = 5;
  private static final int POP_UP_MENU_MODEL_COLUMN_INDEX = 6;

  private @NotNull List<@NotNull VirtualDevice> myDevices;
  private final @NotNull Map<@NotNull VirtualDevice, @NotNull SizeOnDisk> myDeviceToSizeOnDiskMap;

  static final class Actions {
    @SuppressWarnings("InstantiationOfUtilityClass")
    static final Actions INSTANCE = new Actions();

    private Actions() {
    }
  }

  private static final class LaunchInEmulatorValue {
    private static final LaunchInEmulatorValue INSTANCE = new LaunchInEmulatorValue();

    private LaunchInEmulatorValue() {
    }

    @Override
    public @NotNull String toString() {
      return "Launch in emulator";
    }
  }

  private static final class EditValue {
    private static final EditValue INSTANCE = new EditValue();

    private EditValue() {
    }

    @Override
    public @NotNull String toString() {
      return "Edit";
    }
  }

  VirtualDeviceTableModel() {
    this(Collections.emptyList());
  }

  @VisibleForTesting
  VirtualDeviceTableModel(@NotNull List<@NotNull VirtualDevice> devices) {
    myDevices = devices;
    myDeviceToSizeOnDiskMap = new HashMap<>();
  }

  @NotNull List<@NotNull VirtualDevice> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<@NotNull VirtualDevice> devices) {
    myDevices = devices;
    fireTableDataChanged();
  }

  int modelRowIndexOf(@NotNull AvdInfo avdInfo) {
    OptionalInt index = IntStream.range(0, myDevices.size())
      .filter(i -> myDevices.get(i).getAvdInfo().equals(avdInfo))
      .findFirst();

    return index.orElse(-1);
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return SPLIT_ACTIONS_ENABLED ? 7 : 4;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return "Device";
        case API_MODEL_COLUMN_INDEX:
          return "API";
        case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
          return "Size on Disk";
        case LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX:
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
        case EDIT_MODEL_COLUMN_INDEX:
        case POP_UP_MENU_MODEL_COLUMN_INDEX:
          return "";
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

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
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return Device.class;
        case API_MODEL_COLUMN_INDEX:
        case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
          return Object.class;
        case LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX:
          return LaunchInEmulatorValue.class;
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
          return ActivateDeviceFileExplorerWindowValue.class;
        case EDIT_MODEL_COLUMN_INDEX:
          return EditValue.class;
        case POP_UP_MENU_MODEL_COLUMN_INDEX:
          return PopUpMenuValue.class;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

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
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
        case API_MODEL_COLUMN_INDEX:
        case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
          return false;
        case LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX:
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
        case EDIT_MODEL_COLUMN_INDEX:
        case POP_UP_MENU_MODEL_COLUMN_INDEX:
          return true;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    return modelColumnIndex == ACTIONS_MODEL_COLUMN_INDEX;
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return myDevices.get(modelRowIndex);
        case API_MODEL_COLUMN_INDEX:
          return myDevices.get(modelRowIndex).getApi();
        case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
          return getSizeOnDisk(myDevices.get(modelRowIndex));
        case LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX:
          return LaunchInEmulatorValue.INSTANCE;
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
          return ActivateDeviceFileExplorerWindowValue.INSTANCE;
        case EDIT_MODEL_COLUMN_INDEX:
          return EditValue.INSTANCE;
        case POP_UP_MENU_MODEL_COLUMN_INDEX:
          return PopUpMenuValue.INSTANCE;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex);
      case API_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex).getApi();
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return getSizeOnDisk(myDevices.get(modelRowIndex));
      case ACTIONS_MODEL_COLUMN_INDEX:
        return Actions.INSTANCE;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  // TODO Put size in VirtualDevice
  private @NotNull Object getSizeOnDisk(@NotNull VirtualDevice device) {
    return myDeviceToSizeOnDiskMap.computeIfAbsent(device, d -> new SizeOnDisk(d.getAvdInfo(), this));
  }
}
