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

import com.android.tools.idea.run.deployment.Device;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxAction;
import com.android.tools.idea.tests.gui.framework.fixture.ComboBoxActionFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;
import javax.swing.JButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
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

  @NotNull
  private final Project myProject;

  public DeviceSelectorFixture(@NotNull Robot robot, @NotNull Project project) {
    myRobot = robot;

    JButton button = robot.finder().findByName("deviceAndSnapshotComboBoxButton", JButton.class);
    myComboBox = new ComboBoxActionFixture(robot, button);
    myToolbar = UIUtil.getParentOfType(ActionToolbar.class, button);

    myProject = project;
  }

  public void selectDevice(@NotNull String deviceName) {
    Wait.seconds(30).expecting(deviceName).until(() -> anyDeviceNameMatches(device -> device.getName().equals(deviceName)));
    myComboBox.selectItem(deviceName);
  }

  public void waitForDeviceWithKey(@NotNull String key) {
    Wait.seconds(30).expecting(key).until(() -> anyDeviceNameMatches(device -> device.getKey().equals(key)));
  }

  public void selectDeviceWithKey(@NotNull String key) {
    waitForDeviceWithKey(key);

    ActionManager manager = ActionManager.getInstance();
    DeviceAndSnapshotComboBoxAction action = (DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox");
    Wait.seconds(30)
      .expecting(key)
      .until(() -> GuiActionRunner.execute(new GuiQuery<Boolean>() {
        @Nullable
        @Override
        protected Boolean executeInEDT() {
          return action.setSelectedDevice(myProject, key);
        }
      }));
    IdeFrameFixture.find(myRobot).updateToolbars();
  }

  private boolean anyDeviceNameMatches(@NotNull Predicate<Device> matcher) {
    return GuiQuery.get(() -> {
      myToolbar.updateActionsImmediately();
      ActionManager manager = ActionManager.getInstance();

      return ((DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox")).getDevices(myProject).stream()
        .anyMatch(matcher);
    });
  }

  public void troubleshootDeviceConnections(@NotNull IdeFrameFixture ide, @NotNull String appName) {
    ide.selectApp(appName);
    myComboBox.selectItem("Troubleshoot device connections");

    // Without typing Enter the combo box stays open and OpenConnectionAssistantSidePanelAction::actionPerformed never gets called. I wonder
    // if it's because that action doesn't change the selected device. There's precedent in IdeFrameFixture::selectApp and
    // BasePerspectiveConfigurableFixture::findMinimizedModuleSelector. I did consider putting the pressAndReleaseKey call in the combo box
    // fixture itself.
    myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  public void recordEspressoTest(@NotNull IdeFrameFixture ide, @NotNull String deviceName) {
    selectDevice(deviceName);
    ide.invokeMenuPath("Run", "Record Espresso Test");
  }

  public void runApp(@NotNull IdeFrameFixture ide, @NotNull String appName, @NotNull String deviceName) {
    ide.selectApp(appName);
    selectDevice(deviceName);

    ide.findRunApplicationButton().click();
  }

  public void debugApp(@NotNull IdeFrameFixture ide, @NotNull String appName, @NotNull String deviceName) {
    ide.selectApp(appName);
    selectDevice(deviceName);

    ide.findDebugApplicationButton().click();
  }
}
