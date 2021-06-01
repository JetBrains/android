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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.annotations.NotNull;

final class PopupActionGroup extends DefaultActionGroup {
  @NotNull
  private final Collection<Device> myDevices;

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  PopupActionGroup(@NotNull Collection<Device> devices, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    myDevices = devices;
    myComboBoxAction = comboBoxAction;

    Collection<AnAction> actions =
      comboBoxAction.areSnapshotsEnabled() ? newSelectDeviceActionsOrSnapshotActionGroups() : newSelectDeviceActions();

    addAll(actions);

    if (!actions.isEmpty()) {
      addSeparator();
    }

    ActionManager manager = ActionManager.getInstance();

    add(manager.getAction(SelectMultipleDevicesAction.ID));
    add(manager.getAction(PairDevicesUsingWiFiAction.ID));
    add(manager.getAction(WearDevicePairingAction.ID));
    add(manager.getAction(RunAndroidAvdManagerAction.ID));

    AnAction action = manager.getAction("DeveloperServices.ConnectionAssistant");

    if (action == null) {
      return;
    }

    addSeparator();
    add(action);
  }

  private @NotNull Collection<@NotNull AnAction> newSelectDeviceActionsOrSnapshotActionGroups() {
    int size = myDevices.size();
    Collection<Device> runningDevices = new ArrayList<>(size);
    Collection<Device> availableDevices = new ArrayList<>(size);

    for (Device device : myDevices) {
      if (device.isConnected()) {
        runningDevices.add(device);
        continue;
      }

      availableDevices.add(device);
    }

    boolean runningDevicesPresent = !runningDevices.isEmpty();
    Collection<AnAction> actions = new ArrayList<>(3 + size);
    ActionManager manager = ActionManager.getInstance();

    if (runningDevicesPresent) {
      actions.add(manager.getAction(Heading.RUNNING_DEVICES_ID));
    }

    runningDevices.stream()
      .map(this::newSelectDeviceAction)
      .forEach(actions::add);

    boolean availableDevicesPresent = !availableDevices.isEmpty();

    if (runningDevicesPresent && availableDevicesPresent) {
      actions.add(Separator.create());
    }

    if (availableDevicesPresent) {
      actions.add(manager.getAction(Heading.AVAILABLE_DEVICES_ID));
    }

    availableDevices.stream()
      .map(this::newSelectDeviceActionOrSnapshotActionGroup)
      .forEach(actions::add);

    return actions;
  }

  private @NotNull AnAction newSelectDeviceActionOrSnapshotActionGroup(@NotNull Device device) {
    if (!device.getSnapshots().isEmpty()) {
      return new SnapshotActionGroup(device, myComboBoxAction);
    }

    return newSelectDeviceAction(device);
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceActions() {
    Collection<Device> runningDevices = ContainerUtil.filter(myDevices, Device::isConnected);
    Collection<Device> availableDevices = ContainerUtil.filter(myDevices, device -> !device.isConnected());

    boolean runningDevicesPresent = !runningDevices.isEmpty();
    Collection<AnAction> actions = new ArrayList<>(1 + runningDevices.size() + 2 + availableDevices.size());
    ActionManager manager = ActionManager.getInstance();

    if (runningDevicesPresent) {
      actions.add(manager.getAction(Heading.RUNNING_DEVICES_ID));
    }

    runningDevices.stream()
      .map(this::newSelectDeviceAction)
      .forEach(actions::add);

    boolean availableDevicesPresent = !availableDevices.isEmpty();

    if (runningDevicesPresent && availableDevicesPresent) {
      actions.add(Separator.create());
    }

    if (availableDevicesPresent) {
      actions.add(manager.getAction(Heading.AVAILABLE_DEVICES_ID));
    }

    availableDevices.stream()
      .map(this::newSelectDeviceAction)
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private AnAction newSelectDeviceAction(@NotNull Device device) {
    return new SelectDeviceAction(device, myComboBoxAction);
  }
}
