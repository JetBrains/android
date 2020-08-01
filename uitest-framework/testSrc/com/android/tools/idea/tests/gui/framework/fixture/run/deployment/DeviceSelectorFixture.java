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
import com.android.tools.idea.run.deployment.SelectDeviceAction;
import com.android.tools.idea.run.deployment.SerialNumber;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem;
import java.awt.event.InputEvent;
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
import org.jetbrains.annotations.Nullable;

public final class DeviceSelectorFixture {
  private static final Duration DURATION = Duration.ofSeconds(20);

  @NotNull
  private final Robot myRobot;

  @NotNull
  private final JButtonFixture myComboBoxButton;

  @NotNull
  private final IdeFrameFixture myIdeFrameFixture;

  public DeviceSelectorFixture(@NotNull Robot robot, @NotNull IdeFrameFixture ideFrameFixture) {
    myRobot = robot;
    myComboBoxButton = new JButtonFixture(robot, "deviceAndSnapshotComboBoxButton");
    myIdeFrameFixture = ideFrameFixture;
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
   * @deprecated Prefer to use {@link #selectItem(String)}. This is only used for DeploymentTest. Will be removed.
   */
  @Deprecated
  public void selectDevice(@NotNull IDevice device) {
    Wait.seconds(DURATION.getSeconds()).expecting("device " + device + " to be selected").until(() -> {
      if (!clickComboBoxButtonIfEnabled()) {
        return false;
      }

      try {
        JListFixture fixture = new JListFixture(myRobot, "deviceAndSnapshotComboBoxList");

        String text = comboBoxListTextOf(fixture, device);
        if (text == null) {
          return false;
        }
        fixture.clickItem(text);

        // TODO (b/142343916): Occasionally clicks don't register in the IJ framework.
        //  So as a workaround, we'll manually invoke the device selection action.
        AnAction selectDeviceAction = getComboBoxAction(fixture, device);
        if (selectDeviceAction == null) {
          return false;
        }

        return GuiQuery.getNonNull(() -> {
          InputEvent event = ActionCommand.getInputEvent("SelectDevicesAction");
          return ActionManager.getInstance().tryToExecute(selectDeviceAction, event, null, ActionPlaces.UNKNOWN, true).isDone();
        });
      }
      catch (ComponentLookupException exception) {
        return false;
      }
    });
  }

  @Nullable
  private static String comboBoxListTextOf(@NotNull JListFixture list, @NotNull IDevice device) {
    return GuiQuery.get(() -> {
      @SuppressWarnings("unchecked")
      JList<ActionItem> target = list.target();

      ListModel<ActionItem> model = target.getModel();
      Object key = new SerialNumber(device.getSerialNumber());

      for (int i = 0, size = model.getSize(); i < size; i++) {
        ActionItem actionItem = model.getElementAt(i);
        Object action = actionItem.getAction();

        if (!(action instanceof SelectDeviceAction)) {
          continue;
        }

        if (((SelectDeviceAction)action).getDevice().getKey().equals(key)) {
          return actionItem.toString();
        }
      }

      return null;
    });
  }

  @Nullable
  private static AnAction getComboBoxAction(@NotNull JListFixture list, @NotNull IDevice device) {
    return GuiQuery.get(() -> {
      @SuppressWarnings("unchecked")
      JList<ActionItem> target = list.target();

      ListModel<ActionItem> model = target.getModel();
      Object key = new SerialNumber(device.getSerialNumber());

      for (int i = 0, size = model.getSize(); i < size; i++) {
        ActionItem actionItem = model.getElementAt(i);
        AnAction action = actionItem.getAction();

        if (action instanceof SelectDeviceAction && ((SelectDeviceAction)action).getDevice().getKey().equals(key)) {
          return action;
        }
      }

      return null;
    });
  }

  private boolean clickComboBoxButtonIfEnabled() {
    // It's possible for the UI to not be enabled without user input (e.g. moving the mouse).
    // So we'll manually update the toolbars to ensure that our target button is in a good state.
    myIdeFrameFixture.updateToolbars();

    if (!myComboBoxButton.isEnabled()) {
      return false;
    }

    try {
      myComboBoxButton.click();
      return true;
    }
    catch (IllegalStateException e) {
      // TODO (b/142341755): Race condition. Just let the caller try again.
      return false;
    }
  }

  public void troubleshootDeviceConnections(@NotNull String appName) {
    myIdeFrameFixture.selectApp(appName);
    selectItem("Troubleshoot Device Connections");

    // Without typing Enter the combo box stays open and OpenConnectionAssistantSidePanelAction::actionPerformed never gets called. I wonder
    // if it's because that action doesn't change the selected device. There's precedent in IdeFrameFixture::selectApp and
    // BasePerspectiveConfigurableFixture::findMinimizedModuleSelector. I did consider putting the pressAndReleaseKey call in the combo box
    // fixture itself.
    myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  public void recordEspressoTest(@NotNull String deviceName) {
    selectItem(deviceName);
    myIdeFrameFixture.invokeMenuPath("Run", "Record Espresso Test");
  }

  public void runApp(@NotNull String appName, @NotNull String deviceName) {
    myIdeFrameFixture.selectApp(appName);
    selectItem(deviceName);

    myIdeFrameFixture.findRunApplicationButton().click();
  }

  public void debugApp(@NotNull String appName, @NotNull String deviceName) {
    myIdeFrameFixture.selectApp(appName);
    selectItem(deviceName);

    myIdeFrameFixture.findDebugApplicationButton().click();
  }
}
