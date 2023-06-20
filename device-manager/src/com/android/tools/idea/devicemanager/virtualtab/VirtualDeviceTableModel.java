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
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Devices;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UiThread
final class VirtualDeviceTableModel extends AbstractTableModel {
  static final int DEVICE_ICON_MODEL_COLUMN_INDEX = 0;
  static final int DEVICE_MODEL_COLUMN_INDEX = 1;
  static final int API_MODEL_COLUMN_INDEX = 2;
  static final int SIZE_ON_DISK_MODEL_COLUMN_INDEX = 3;
  static final int LAUNCH_OR_STOP_MODEL_COLUMN_INDEX = 4;
  static final int ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX = 5;
  static final int EDIT_MODEL_COLUMN_INDEX = 6;
  static final int POP_UP_MENU_MODEL_COLUMN_INDEX = 7;

  private final @Nullable Project myProject;
  private @NotNull List<VirtualDevice> myDevices;
  private final @NotNull NewSetOnline myNewSetOnline;
  private final @NotNull Callable<AvdManagerConnection> myGetDefaultAvdManagerConnection;
  private final @NotNull Function<VirtualDeviceTableModel, FutureCallback<Object>> myNewSetAllOnline;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull Function<IDevice, EmulatorConsole> myGetConsole;
  private final @NotNull BiConsumer<Throwable, Project> myShowErrorDialog;

  @VisibleForTesting
  interface NewSetOnline {
    @NotNull
    FutureCallback<Boolean> apply(@NotNull VirtualDeviceTableModel model, @NotNull Key key);
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

  VirtualDeviceTableModel(@Nullable Project project) {
    this(project, List.of());
  }

  @VisibleForTesting
  VirtualDeviceTableModel(@Nullable Project project, @NotNull Collection<VirtualDevice> devices) {
    this(project,
         devices,
         VirtualDeviceTableModel::newSetOnline,
         AvdManagerConnection::getDefaultAvdManagerConnection,
         SetAllOnline::new,
         new DeviceManagerAndroidDebugBridge(),
         EmulatorConsole::getConsole,
         VirtualTabMessages::showErrorDialog);
  }

  @VisibleForTesting
  VirtualDeviceTableModel(@Nullable Project project,
                          @NotNull Collection<VirtualDevice> devices,
                          @NotNull NewSetOnline newSetOnline,
                          @NotNull Callable<AvdManagerConnection> getDefaultAvdManagerConnection,
                          @NotNull Function<VirtualDeviceTableModel, FutureCallback<Object>> newSetAllOnline,
                          @NotNull DeviceManagerAndroidDebugBridge bridge,
                          @NotNull Function<IDevice, EmulatorConsole> getConsole,
                          @NotNull BiConsumer<Throwable, Project> showErrorDialog) {
    myProject = project;
    myDevices = new ArrayList<>(devices);
    myNewSetOnline = newSetOnline;
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;
    myNewSetAllOnline = newSetAllOnline;
    myBridge = bridge;
    myGetConsole = getConsole;
    myShowErrorDialog = showErrorDialog;
  }

  @VisibleForTesting
  static @NotNull FutureCallback<Boolean> newSetOnline(@NotNull VirtualDeviceTableModel model, @NotNull Key key) {
    return new DeviceManagerFutureCallback<>(VirtualDeviceTableModel.class, online -> {
      int modelRowIndex = Devices.indexOf(model.myDevices, key);

      if (modelRowIndex == -1) {
        return;
      }

      model.myDevices.set(modelRowIndex, model.myDevices.get(modelRowIndex).withState(VirtualDevice.State.valueOf(online)));
      model.fireTableRowsUpdated(modelRowIndex, modelRowIndex);
    });
  }

  @NotNull
  List<VirtualDevice> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<VirtualDevice> devices) {
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

  @NotNull
  ListenableFuture<Boolean> remove(@NotNull VirtualDevice device) {
    FutureCallback<Boolean> callback =
      new DeviceManagerFutureCallback<>(VirtualDeviceTableModel.class, deletionSuccessful -> remove(deletionSuccessful, device));

    @SuppressWarnings("UnstableApiUsage")
    ListenableFuture<Boolean> future = FluentFuture.from(getDefaultAvdManagerConnection())
      .transform(connection -> connection.deleteAvd(device.getAvdInfo()), AppExecutorUtil.getAppExecutorService());

    Futures.addCallback(future, callback, EdtExecutorService.getInstance());
    return future;
  }

  private void remove(boolean deletionSuccessful, @NotNull VirtualDevice device) {
    if (!deletionSuccessful) {
      Logger.getInstance(VirtualDeviceTableModel.class).warn("Failed to delete " + device);
      return;
    }

    remove(device.getKey());
  }

  void remove(@NotNull Key key) {
    int modelRowIndex = Devices.indexOf(myDevices, key);

    if (modelRowIndex == -1) {
      return;
    }

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
    ListenableFuture<Boolean> future = DeviceManagerFutures.appExecutorServiceSubmit(() -> connection.isAvdRunning(device.getAvdInfo()));
    Futures.addCallback(future, myNewSetOnline.apply(this, device.getKey()), EdtExecutorService.getInstance());
  }

  private @NotNull ListenableFuture<AvdManagerConnection> getDefaultAvdManagerConnection() {
    return DeviceManagerFutures.appExecutorServiceSubmit(myGetDefaultAvdManagerConnection);
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 8;
  }

  @SuppressWarnings("DuplicateBranchesInSwitch")
  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    return switch (modelColumnIndex) {
      case DEVICE_ICON_MODEL_COLUMN_INDEX -> "";
      case DEVICE_MODEL_COLUMN_INDEX -> "Device";
      case API_MODEL_COLUMN_INDEX -> "API";
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX -> "Size on Disk";
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX, ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX, EDIT_MODEL_COLUMN_INDEX, POP_UP_MENU_MODEL_COLUMN_INDEX ->
        "";
      default -> throw new AssertionError(modelColumnIndex);
    };
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    return switch (modelColumnIndex) {
      case DEVICE_ICON_MODEL_COLUMN_INDEX -> DeviceType.class;
      case DEVICE_MODEL_COLUMN_INDEX -> Device.class;
      case API_MODEL_COLUMN_INDEX -> AndroidVersion.class;
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX -> Long.class;
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX -> VirtualDevice.State.class;
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX -> ActivateDeviceFileExplorerWindowValue.class;
      case EDIT_MODEL_COLUMN_INDEX -> EditValue.class;
      case POP_UP_MENU_MODEL_COLUMN_INDEX -> PopUpMenuValue.class;
      default -> throw new AssertionError(modelColumnIndex);
    };
  }

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    return switch (modelColumnIndex) {
      case DEVICE_ICON_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex).getIcon().equals(AllIcons.Actions.Download);
      case DEVICE_MODEL_COLUMN_INDEX, API_MODEL_COLUMN_INDEX, SIZE_ON_DISK_MODEL_COLUMN_INDEX -> false;
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX -> {
        VirtualDevice device = myDevices.get(modelRowIndex);
        yield device.getState().isEnabled(device);
      }
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX -> myProject != null && myDevices.get(modelRowIndex).isOnline();
      case EDIT_MODEL_COLUMN_INDEX, POP_UP_MENU_MODEL_COLUMN_INDEX -> true;
      default -> throw new AssertionError(modelColumnIndex);
    };
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    return switch (modelColumnIndex) {
      case DEVICE_ICON_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex).getType();
      case DEVICE_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex);
      case API_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex).getAndroidVersion();
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex).getSizeOnDisk();
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX -> myDevices.get(modelRowIndex).getState();
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX -> ActivateDeviceFileExplorerWindowValue.INSTANCE;
      case EDIT_MODEL_COLUMN_INDEX -> EditValue.INSTANCE;
      case POP_UP_MENU_MODEL_COLUMN_INDEX -> PopUpMenuValue.INSTANCE;
      default -> throw new AssertionError(modelColumnIndex);
    };
  }

  @SuppressWarnings("DuplicateBranchesInSwitch")
  @Override
  public void setValueAt(@NotNull Object value, int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_ICON_MODEL_COLUMN_INDEX:
        break;
      case DEVICE_MODEL_COLUMN_INDEX:
      case API_MODEL_COLUMN_INDEX:
      case SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        assert false : modelColumnIndex;
        break;
      case LAUNCH_OR_STOP_MODEL_COLUMN_INDEX:
        launchOrStop((VirtualDevice.State)value, modelRowIndex);
        break;
      case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
      case EDIT_MODEL_COLUMN_INDEX:
      case POP_UP_MENU_MODEL_COLUMN_INDEX:
        break;
      default:
        assert false : modelColumnIndex;
        break;
    }
  }

  private void launchOrStop(@NotNull VirtualDevice.State state, int modelRowIndex) {
    switch (state) {
      case LAUNCHING -> launch(modelRowIndex);
      case STOPPING -> stop(modelRowIndex);
      default -> {
        assert false : state;
      }
    }
  }

  private void launch(int modelRowIndex) {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.VIRTUAL_LAUNCH_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);

    VirtualDevice device = myDevices.get(modelRowIndex).withState(VirtualDevice.State.LAUNCHING);

    myDevices.set(modelRowIndex, device);
    fireTableCellUpdated(modelRowIndex, LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    Executor executor = EdtExecutorService.getInstance();

    // noinspection UnstableApiUsage
    FluentFuture.from(getDefaultAvdManagerConnection())
      .transformAsync(connection -> connection.startAvd(myProject, device.getAvdInfo(), RequestType.DIRECT), executor)
      .addCallback(myNewSetAllOnline.apply(this), executor);
  }

  private void stop(int modelRowIndex) {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.VIRTUAL_STOP_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);

    VirtualDevice device = myDevices.get(modelRowIndex).withState(VirtualDevice.State.STOPPING);

    myDevices.set(modelRowIndex, device);
    fireTableCellUpdated(modelRowIndex, LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // noinspection UnstableApiUsage
    FluentFuture.from(myBridge.findDevice(myProject, device.getKey()))
      .transform(d -> stop(d, device), AppExecutorUtil.getAppExecutorService())
      .addCallback(myNewSetAllOnline.apply(this), EdtExecutorService.getInstance());
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  @SuppressWarnings("SameReturnValue")
  private @Nullable Void stop(@Nullable IDevice d, @NotNull Object device) {
    if (d == null) {
      throw new ErrorDialogException("Unable to stop " + device,
                                     "An error occurred stopping " + device + ". To stop the device, try manually closing the " + device +
                                     " emulator window.");
    }

    EmulatorConsole console = myGetConsole.apply(d);

    console.kill();
    console.close();

    return null;
  }

  @UiThread
  @VisibleForTesting
  static final class SetAllOnline implements FutureCallback<Object> {
    private final @NotNull VirtualDeviceTableModel myModel;

    @VisibleForTesting
    SetAllOnline(@NotNull VirtualDeviceTableModel model) {
      myModel = model;
    }

    @Override
    public void onSuccess(@Nullable Object object) {
      // The launch succeeded. Rely on the VirtualDeviceChangeListener, which calls VirtualDeviceTableModel::setAllOnline, to transition the
      // device state from VirtualDevice.State.LAUNCHING to VirtualDevice.State.LAUNCHED. Likewise for STOPPING and STOPPED.
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      // The launch failed. Manually transition the state from LAUNCHING to whatever the AvdManagerConnection reports (STOPPED, presumably).
      // Likewise for STOPPING.
      myModel.setAllOnline();

      myModel.myShowErrorDialog.accept(throwable, myModel.myProject);
    }
  }
}
