/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SelectDeviceAction extends AnAction {
  @NotNull
  private final Device myDevice;

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  private final boolean mySnapshotActionGroupChild;

  @NotNull
  static AnAction newSelectDeviceAction(@NotNull Device device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    return new SelectDeviceAction(device, comboBoxAction, false);
  }

  @NotNull
  static AnAction newSnapshotActionGroupChild(@NotNull Device device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    return new SelectDeviceAction(device, comboBoxAction, true);
  }

  private SelectDeviceAction(@NotNull Device device,
                             @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                             boolean snapshotActionGroupChild) {
    myDevice = device;
    mySnapshotActionGroupChild = snapshotActionGroupChild;
    myComboBoxAction = comboBoxAction;
  }

  @NotNull
  @VisibleForTesting
  public Device getDevice() {
    return myDevice;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (mySnapshotActionGroupChild) {
      Snapshot snapshot = myDevice.getSnapshot();
      presentation.setText(snapshot == null ? "No Snapshot" : snapshot.toString(), false);

      return;
    }

    presentation.setIcon(myDevice.getIcon());

    Collection<Device> devices = myComboBoxAction.getDevices(Objects.requireNonNull(event.getProject()));
    Key key = Devices.containsAnotherDeviceWithSameName(devices, myDevice) ? myDevice.getKey() : null;
    Snapshot snapshot = myComboBoxAction.areSnapshotsEnabled() ? myDevice.getSnapshot() : null;

    presentation.setText(Devices.getText(myDevice, key, snapshot), false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    DevicesSelectedService.getInstance(Objects.requireNonNull(event.getProject())).setDeviceSelectedWithComboBox(myDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAction)) {
      return false;
    }

    SelectDeviceAction action = (SelectDeviceAction)object;

    return myDevice.equals(action.myDevice) &&
           myComboBoxAction.equals(action.myComboBoxAction) &&
           mySnapshotActionGroupChild == action.mySnapshotActionGroupChild;
  }

  @Override
  public int hashCode() {
    int hashCode = myDevice.hashCode();

    hashCode = 31 * hashCode + myComboBoxAction.hashCode();
    hashCode = 31 * hashCode + Boolean.hashCode(mySnapshotActionGroupChild);

    return hashCode;
  }
}
