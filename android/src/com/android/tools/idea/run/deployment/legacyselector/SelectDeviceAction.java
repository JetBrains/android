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
package com.android.tools.idea.run.deployment.legacyselector;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An action for each device in the drop down without a snapshot sublist. When a user selects a device, SelectDeviceAction will set a target
 * for the device in DeviceAndSnapshotComboBoxAction.
 */
public final class SelectDeviceAction extends AnAction {
  @NotNull
  private final Device myDevice;

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  SelectDeviceAction(@NotNull Device device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    myDevice = device;
    myComboBoxAction = comboBoxAction;
  }

  @NotNull
  public Device getDevice() {
    return myDevice;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setIcon(myDevice.getIcon());

    Collection<Device> devices = myComboBoxAction.getDevices(Objects.requireNonNull(event.getProject())).orElseThrow(AssertionError::new);
    Key key = Devices.containsAnotherDeviceWithSameName(devices, myDevice) ? myDevice.getKey() : null;

    presentation.setText(Devices.getText(myDevice, key), false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setTargetSelectedWithComboBox(Objects.requireNonNull(event.getProject()), myDevice.getDefaultTarget());
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAction)) {
      return false;
    }

    SelectDeviceAction action = (SelectDeviceAction)object;
    return myDevice.equals(action.myDevice) && myComboBoxAction.equals(action.myComboBoxAction);
  }

  @Override
  public int hashCode() {
    return 31 * myDevice.hashCode() + myComboBoxAction.hashCode();
  }
}
