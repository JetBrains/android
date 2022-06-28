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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.Devices;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevice.LaunchOrStopButtonState;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

@UiThread
final class VirtualDeviceTableModel extends AbstractTableModel {
  static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  static final int API_MODEL_COLUMN_INDEX = 1;
  static final int SIZE_ON_DISK_MODEL_COLUMN_INDEX = 2;
  static final int LAUNCH_OR_STOP_MODEL_COLUMN_INDEX = 3;
  static final int ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX = 4;
  static final int EDIT_MODEL_COLUMN_INDEX = 5;
  static final int POP_UP_MENU_MODEL_COLUMN_INDEX = 6;

  private @NotNull List<@NotNull VirtualDevice> myDevices;

  private final @NotNull Callable<@NotNull AvdManagerConnection> myGetDefaultAvdManagerConnection;
  private final @NotNull NewSetOnline myNewSetOnline;

  @VisibleForTesting
  interface NewSetOnline {
    @NotNull FutureCallback<@NotNull Boolean> apply(@NotNull VirtualDeviceTableModel model, @NotNull Key key);
  }

  static final class EditValue {
    static final EditValue INSTANCE = new EditValue();

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
  VirtualDeviceTableModel(@NotNull Collection<@NotNull VirtualDevice> devices) {
    this(devices, AvdManagerConnection::getDefaultAvdManagerConnection, VirtualDeviceTableModel::newSetOnline);
  }

  @VisibleForTesting
  VirtualDeviceTableModel(@NotNull Collection<@NotNull VirtualDevice> devices,
                          @NotNull Callable<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection,
                          @NotNull NewSetOnline newSetOnline) {
    myDevices = new ArrayList<>(devices);
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;
    myNewSetOnline = newSetOnline;
  }

  @VisibleForTesting
  static @NotNull FutureCallback<@NotNull Boolean> newSetOnline(@NotNull VirtualDeviceTableModel model, @NotNull Key key) {
    return new DeviceManagerFutureCallback<>(VirtualDeviceTableModel.class, online -> {
      int modelRowIndex = Devices.indexOf(model.myDevices, key);

      if (modelRowIndex == -1) {
        return;
      }

      VirtualDevice oldDevice = model.myDevices.get(modelRowIndex);

      VirtualDevice newDevice = new VirtualDevice.Builder()
        .setKey(oldDevice.getKey())
        .setType(oldDevice.getType())
        .setName(oldDevice.getName())
        .setOnline(online)
        .setTarget(oldDevice.getTarget())
        .setCpuArchitecture(oldDevice.getCpuArchitecture())
        .setAndroidVersion(oldDevice.getAndroidVersion())
        .setSizeOnDisk(oldDevice.getSizeOnDisk())
        .setResolution(oldDevice.getResolution())
        .setDensity(oldDevice.getDensity())
        .setAvdInfo(oldDevice.getAvdInfo())
        .build();

      model.myDevices.set(modelRowIndex, newDevice);
      model.fireTableCellUpdated(modelRowIndex, DEVICE_MODEL_COLUMN_INDEX);
    });
  }

  @NotNull List<@NotNull VirtualDevice> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<@NotNull VirtualDevice> devices) {
    myDevices = devices;
    fireTableDataChanged();
  }

  void add(@NotNull VirtualDevice device) {
    myDevices.add(device);

    int modelRowIndex = myDevices.size() - 1;
    fireTableRowsInserted(modelRowIndex, modelRowIndex);
  }

  void set(@NotNull VirtualDevice device) {
    int modelRowIndex = Devices.indexOf(myDevices, device.getKey());

    if (modelRowIndex == -1) {
      return;
    }

    myDevices.set(modelRowIndex, device);
    fireTableRowsUpdated(modelRowIndex, modelRowIndex);
  }

  void remove(@NotNull VirtualDevice device) {
    FutureCallback<Boolean> callback =
      new DeviceManagerFutureCallback<>(VirtualDeviceTableModel.class, deletionSuccessful -> remove(deletionSuccessful, device));

    // noinspection UnstableApiUsage
    FluentFuture.from(getDefaultAvdManagerConnection())
      .transform(connection -> connection.deleteAvd(device.getAvdInfo()), AppExecutorUtil.getAppExecutorService())
      .addCallback(callback, EdtExecutorService.getInstance());
  }

  private void remove(boolean deletionSuccessful, @NotNull VirtualDevice device) {
    if (!deletionSuccessful) {
      Logger.getInstance(VirtualDeviceTableModel.class).warn("Failed to delete " + device);
      return;
    }

    int modelRowIndex = myDevices.indexOf(device);
    myDevices.remove(modelRowIndex);

    fireTableRowsDeleted(modelRowIndex, modelRowIndex);
  }

  void setAllOnline() {
    FutureCallback<AvdManagerConnection> callback = new DeviceManagerFutureCallback<>(VirtualDeviceTableModel.class, this::setAllOnline);
    Futures.addCallback(getDefaultAvdManagerConnection(), callback, EdtExecutorService.getInstance());
  }

  private void setAllOnline(@NotNull AvdManagerConnection connection) {
    myDevices.forEach(device -> setOnline(device, connection));
  }

  private void setOnline(@NotNull VirtualDevice device, @NotNull AvdManagerConnection connection) {
    Executor executor = AppExecutorUtil.getAppExecutorService();

    @SuppressWarnings("UnstableApiUsage")
    ListenableFuture<Boolean> future = Futures.submit(() -> connection.isAvdRunning(device.getAvdInfo()), executor);

    Futures.addCallback(future, myNewSetOnline.apply(this, device.getKey()), EdtExecutorService.getInstance());
  }

  private @NotNull ListenableFuture<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection() {
    // noinspection UnstableApiUsage
    return Futures.submit(myGetDefaultAvdManagerConnection, AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 7;
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
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX:
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
      case EDIT_MODEL_COLUMN_INDEX:
      case POP_UP_MENU_MODEL_COLUMN_INDEX:
        return "";
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
        return AndroidVersion.class;
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return Long.class;
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX:
        return LaunchOrStopButtonState.class;
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

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
      case API_MODEL_COLUMN_INDEX:
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return false;
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX:
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
      case EDIT_MODEL_COLUMN_INDEX:
      case POP_UP_MENU_MODEL_COLUMN_INDEX:
        return true;
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
        return myDevices.get(modelRowIndex).getAndroidVersion();
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex).getSizeOnDisk();
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX:
        return LaunchOrStopButtonState.STOPPED;
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
}
