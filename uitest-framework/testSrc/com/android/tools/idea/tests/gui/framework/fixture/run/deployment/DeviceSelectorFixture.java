/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.run.deployment;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.deployment.Device;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxAction;
import com.android.tools.idea.tests.gui.framework.fixture.ComboBoxActionFixture;
import com.android.tools.idea.tests.gui.framework.fixture.DeployTargetPickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.util.ui.UIUtil;
import javax.swing.JButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceSelectorFixture {
  @NotNull
  private final Robot myRobot;

  @Nullable
  private final ComboBoxActionFixture myComboBox;

  @Nullable
  private final ActionToolbar myToolbar;

  @Nullable
  private final DeviceAndSnapshotComboBoxAction myAction;

  public DeviceSelectorFixture(@NotNull Robot robot) {
    myRobot = robot;

    if (!StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      myComboBox = null;
      myToolbar = null;
      myAction = null;

      return;
    }

    JButton button = robot.finder().findByName("deviceAndSnapshotComboBoxButton", JButton.class);

    myComboBox = new ComboBoxActionFixture(robot, button);
    myToolbar = UIUtil.getParentOfType(ActionToolbar.class, button);

    myAction = (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
  }

  public void selectDevice(@NotNull String deviceName) {
    if (!StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      DeployTargetPickerDialogFixture.find(myRobot)
        .selectDevice(deviceName)
        .clickOk();

      return;
    }

    Wait.seconds(1).expecting(deviceName).until(() -> anyDeviceNameMatches(deviceName));
    myComboBox.selectItem(deviceName);
  }

  private boolean anyDeviceNameMatches(@NotNull String deviceName) {
    return GuiQuery.get(() -> {
      myToolbar.updateActionsImmediately();

      return myAction.getDevices().stream()
        .map(Device::getName)
        .anyMatch(name -> name.equals(deviceName));
    });
  }

  public void recordEspressoTest(@NotNull IdeFrameFixture ide, @NotNull String deviceName) {
    if (!StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      ide.invokeMenuPath("Run", "Record Espresso Test");

      DeployTargetPickerDialogFixture.find(myRobot)
        .selectDevice(deviceName)
        .clickOk();

      return;
    }

    selectDevice(deviceName);
    ide.invokeMenuPath("Run", "Record Espresso Test");
  }
}
