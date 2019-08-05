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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SnapshotActionGroup extends ActionGroup {
  private final Device myDevice;
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;
  private final Project myProject;

  SnapshotActionGroup(@NotNull VirtualDevice device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction, @NotNull Project project) {
    super(device.getName(), null, device.getIcon());
    setPopup(true);

    myDevice = device;
    myComboBoxAction = comboBoxAction;
    myProject = project;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent event) {
    return myDevice.getSnapshots().stream()
      .map(this::newSelectDeviceAndSnapshotAction)
      .toArray(AnAction[]::new);
  }

  @NotNull
  private AnAction newSelectDeviceAndSnapshotAction(@NotNull Snapshot snapshot) {
    return new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setProject(myProject)
      .setDevice(myDevice)
      .setSnapshot(snapshot)
      .build();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SnapshotActionGroup)) {
      return false;
    }

    SnapshotActionGroup group = (SnapshotActionGroup)object;
    return myDevice.equals(group.myDevice) && myComboBoxAction.equals(group.myComboBoxAction) && myProject.equals(group.myProject);
  }

  @Override
  public int hashCode() {
    int hashCode = myDevice.hashCode();

    hashCode = 31 * hashCode + myComboBoxAction.hashCode();
    hashCode = 31 * hashCode + myProject.hashCode();

    return hashCode;
  }
}
