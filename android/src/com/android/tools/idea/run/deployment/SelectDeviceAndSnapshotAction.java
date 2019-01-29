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

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

final class SelectDeviceAndSnapshotAction extends AnAction {
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;
  private final Device myDevice;
  private final String mySnapshot;

  SelectDeviceAndSnapshotAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction, @NotNull Device device) {
    super(device.getName(), null, device.getIcon());

    myComboBoxAction = comboBoxAction;
    myDevice = device;

    Collection<String> snapshots = device.getSnapshots();

    if (snapshots.isEmpty()) {
      mySnapshot = null;
      return;
    }

    if (snapshots.equals(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)) {
      mySnapshot = VirtualDevice.DEFAULT_SNAPSHOT;
      return;
    }

    throw new IllegalArgumentException(device.toString());
  }

  SelectDeviceAndSnapshotAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction, @NotNull Device device, @NotNull String snapshot) {
    super(snapshot);

    myComboBoxAction = comboBoxAction;
    myDevice = device;
    mySnapshot = snapshot;
  }

  @Nullable
  @VisibleForTesting
  String getSnapshot() {
    return mySnapshot;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setSelectedDevice(myDevice);
    myComboBoxAction.setSelectedSnapshot(mySnapshot);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAndSnapshotAction)) {
      return false;
    }

    SelectDeviceAndSnapshotAction action = (SelectDeviceAndSnapshotAction)object;

    return myComboBoxAction.equals(action.myComboBoxAction) &&
           myDevice.equals(action.myDevice) &&
           Objects.equals(mySnapshot, action.mySnapshot);
  }

  @Override
  public int hashCode() {
    int hashCode = myComboBoxAction.hashCode();

    hashCode = 31 * hashCode + myDevice.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(mySnapshot);

    return hashCode;
  }
}
