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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.deployment.Key;
import com.android.tools.idea.run.deployment.SelectDeviceAction;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem;
import java.awt.event.KeyEvent;
import java.time.Duration;
import javax.swing.JList;
import javax.swing.ListModel;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public final class DeviceSelectorFixture {
  private static final Duration DURATION = Duration.ofSeconds(20);

  @NotNull
  private final Robot myRobot;

  @NotNull
  private final JButtonFixture myComboBoxButton;

  public DeviceSelectorFixture(@NotNull Robot robot) {
    myRobot = robot;
    myComboBoxButton = new JButtonFixture(robot, "deviceAndSnapshotComboBoxButton");
  }

  public void selectItem(@NotNull String item) {
    Wait.seconds(DURATION.getSeconds()).expecting("item " + item + " to be selected").until(() -> {
      if (!clickComboBoxButtonIfEnabled()) {
        return false;
      }

      try {
        new JListFixture(myRobot, "deviceAndSnapshotComboBoxList").clickItem(item);
        return true;
      }
      catch (ComponentLookupException | LocationUnavailableException exception) {
        return false;
      }
    });
  }

  /**
   * Prefer to use {@link #selectItem(String)}
   */
  public void selectDevice(@NotNull IDevice device) {
    Wait.seconds(DURATION.getSeconds()).expecting("device " + device + " to be selected").until(() -> {
      if (!clickComboBoxButtonIfEnabled()) {
        return false;
      }

      try {
        JListFixture list = new JListFixture(myRobot, "deviceAndSnapshotComboBoxList");
        int i = comboBoxListIndexOf(list, device);

        if (i == -1) {
          clickComboBoxButtonIfEnabled();
          return false;
        }

        list.clickItem(i);
        return true;
      }
      catch (ComponentLookupException exception) {
        return false;
      }
    });
  }

  private static int comboBoxListIndexOf(@NotNull JListFixture list, @NotNull IDevice device) {
    return GuiQuery.get(() -> {
      @SuppressWarnings("unchecked")
      JList<ActionItem> target = list.target();

      ListModel<ActionItem> model = target.getModel();
      Object key = new Key(device.getSerialNumber());

      for (int i = 0, size = model.getSize(); i < size; i++) {
        Object action = model.getElementAt(i).getAction();

        if (!(action instanceof SelectDeviceAction)) {
          continue;
        }

        if (((SelectDeviceAction)action).getDevice().getKey().equals(key)) {
          return i;
        }
      }

      return -1;
    });
  }

  private boolean clickComboBoxButtonIfEnabled() {
    if (!myComboBoxButton.isEnabled()) {
      return false;
    }

    myComboBoxButton.click();
    return true;
  }

  public void troubleshootDeviceConnections(@NotNull IdeFrameFixture ide, @NotNull String appName) {
    ide.selectApp(appName);
    selectItem("Troubleshoot Device Connections");

    // Without typing Enter the combo box stays open and OpenConnectionAssistantSidePanelAction::actionPerformed never gets called. I wonder
    // if it's because that action doesn't change the selected device. There's precedent in IdeFrameFixture::selectApp and
    // BasePerspectiveConfigurableFixture::findMinimizedModuleSelector. I did consider putting the pressAndReleaseKey call in the combo box
    // fixture itself.
    myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  public void recordEspressoTest(@NotNull IdeFrameFixture ide, @NotNull String deviceName) {
    selectItem(deviceName);
    ide.invokeMenuPath("Run", "Record Espresso Test");
  }

  public void runApp(@NotNull IdeFrameFixture ide, @NotNull String appName, @NotNull String deviceName) {
    ide.selectApp(appName);
    selectItem(deviceName);

    ide.findRunApplicationButton().click();
  }

  public void debugApp(@NotNull IdeFrameFixture ide, @NotNull String appName, @NotNull String deviceName) {
    ide.selectApp(appName);
    selectItem(deviceName);

    ide.findDebugApplicationButton().click();
  }
}
