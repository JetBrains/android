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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SelectDeviceAction extends AnAction {
  private final SelectDeviceComboBoxAction myComboBoxAction;
  private final Device myDevice;

  SelectDeviceAction(@NotNull Device device, @NotNull SelectDeviceComboBoxAction comboBoxAction) {
    super(device.getName(), null, device.getIcon());

    myComboBoxAction = comboBoxAction;
    myDevice = device;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setSelectedDevice(myDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAction)) {
      return false;
    }

    SelectDeviceAction action = (SelectDeviceAction)object;
    return myComboBoxAction.equals(action.myComboBoxAction) && myDevice.equals(action.myDevice);
  }

  @Override
  public int hashCode() {
    return 31 * myComboBoxAction.hashCode() + myDevice.hashCode();
  }
}
