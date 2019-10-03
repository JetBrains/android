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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SelectDeviceAction extends AnAction {
  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  @NotNull
  private final Project myProject;

  @NotNull
  private final Device myDevice;

  @NotNull
  static AnAction newSelectDeviceAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                                        @NotNull Project project,
                                        @NotNull Device device) {
    return new SelectDeviceAction(comboBoxAction, project, device, false);
  }

  @NotNull
  static AnAction newSnapshotActionGroupChild(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                                              @NotNull Project project,
                                              @NotNull Device device) {
    return new SelectDeviceAction(comboBoxAction, project, device, true);
  }

  private SelectDeviceAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                             @NotNull Project project,
                             @NotNull Device device,
                             boolean snapshotActionGroupChild) {
    configurePresentation(comboBoxAction, project, device, snapshotActionGroupChild);

    myComboBoxAction = comboBoxAction;
    myProject = project;
    myDevice = device;
  }

  private void configurePresentation(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                                     @NotNull Project project,
                                     @NotNull Device device,
                                     boolean snapshotActionGroupChild) {
    Presentation presentation = getTemplatePresentation();

    if (snapshotActionGroupChild) {
      Snapshot snapshot = device.getSnapshot();
      assert snapshot != null;

      presentation.setText(snapshot.toString(), false);
      return;
    }

    presentation.setIcon(device.getIcon());

    Key key = Devices.containsAnotherDeviceWithSameName(comboBoxAction.getDevices(project), device) ? device.getKey() : null;
    Snapshot snapshot = comboBoxAction.areSnapshotsEnabled() ? device.getSnapshot() : null;

    presentation.setText(Devices.getText(device, key, snapshot), false);
  }

  @NotNull
  @VisibleForTesting
  public Device getDevice() {
    return myDevice;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setSelectedDevice(myProject, myDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAction)) {
      return false;
    }

    SelectDeviceAction action = (SelectDeviceAction)object;
    return myComboBoxAction.equals(action.myComboBoxAction) && myProject.equals(action.myProject) && myDevice.equals(action.myDevice);
  }

  @Override
  public int hashCode() {
    int hashCode = myComboBoxAction.hashCode();

    hashCode = 31 * hashCode + myProject.hashCode();
    hashCode = 31 * hashCode + myDevice.hashCode();

    return hashCode;
  }
}
