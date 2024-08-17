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

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public final class DeviceSelectorFixture {
  private static final Duration DURATION = Duration.ofSeconds(40);

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

  public void waitForDeviceRecognition(@NotNull String item, boolean exists) {
    Wait.seconds(DURATION.getSeconds()).expecting("device '" + item + "' to be " + (exists ? "recognized" : "removed")).until(() -> {
      myIdeFrameFixture.updateToolbars();
      // Click the combo box button to force it to refresh.
      if (!clickComboBoxButtonIfEnabled()) {
        return false;
      }

      try {
        return GuiQuery.getNonNull(() -> {
          Stream<String> stream = Arrays.stream(new JListFixture(myRobot, "deviceAndSnapshotComboBoxList").contents());
          return exists ? stream.anyMatch(item::equals) : stream.noneMatch(item::equals);
        });
      }
      catch (ComponentLookupException exception) {
        return false;
      }
    });
  }

  public @NotNull String getCurrentlySelectedDevice() {
    return new JListFixture(myRobot, "deviceAndSnapshotComboBoxList").selection()[0];
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
