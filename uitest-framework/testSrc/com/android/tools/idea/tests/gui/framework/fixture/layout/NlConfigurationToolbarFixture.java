/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Predicate;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;

/**
 * Fixture representing the configuration toolbar above an associated layout editor
 */
public class NlConfigurationToolbarFixture {
  private final Robot myRobot;
  private final ActionToolbar myToolBar;
  private final EditorDesignSurface mySurface;

  public NlConfigurationToolbarFixture(@NotNull Robot robot, @NotNull EditorDesignSurface surface, @NotNull ActionToolbar toolbar) {
    myRobot = robot;
    myToolBar = toolbar;
    mySurface = surface;
  }

  /**
   * Requires the orientation name to be the given name (typically Portrait or Landscape)
   */
  @NotNull
  public NlConfigurationToolbarFixture requireOrientation(@NotNull String name)  {
    Wait.seconds(1).expecting("configuration to be updated").until(() -> {
      Configuration configuration = mySurface.getConfiguration();
      if (configuration != null) {
        State deviceState = configuration.getDeviceState();
        if (deviceState != null) {
          return name.equals(deviceState.getOrientation().getShortDisplayValue());
        }
      }
      return false;
    });
    return this;
  }

  /** Returns the current API level of the toolbar's configuration */
  public int getApiLevel() {
    return Integer.parseInt(new JButtonFixture(myRobot, findToolbarButton("API Version in Editor")).text());
  }

  /**
   * Selects a device matching the given label prefix in the configuration toolbar's device menu
   */
  public void chooseDevice(@NotNull String label) {
    new JButtonFixture(myRobot, findToolbarButton("Device in Editor")).click();
    clickPopupMenuItemMatching(new DeviceNamePredicate(label), myToolBar.getComponent(), myRobot);
  }

  /**
   * Requires the device id to be the given id
   */
  @NotNull
  public NlConfigurationToolbarFixture requireDevice(@NotNull String id)  {
    Wait.seconds(1).expecting("configuration to be updated").until(() -> {
      Configuration configuration = mySurface.getConfiguration();
      if (configuration != null) {
        Device device = configuration.getDevice();
        return device != null && id.equals(device.getId());
      }
      return false;
    });
    return this;
  }

  /**
   * Click on the "Show Design" button
   */
  public NlConfigurationToolbarFixture showDesign() {
    ActionButton button = waitUntilShowing(myRobot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return "Show Design".equals(component.getAction().getTemplatePresentation().getDescription());
      }
    });
    new ActionButtonFixture(myRobot, button).click();
    return this;
  }

  /**
   * Click on the "Show Blueprint" button
   */
  public NlConfigurationToolbarFixture showBlueprint() {
    ActionButton button = waitUntilShowing(myRobot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return "Show Blueprint".equals(component.getAction().getTemplatePresentation().getDescription());
      }
    });
    new ActionButtonFixture(myRobot, button).click();
    return this;
  }

  @NotNull
  private JButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilShowing(myRobot, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        return tooltip.equals(button.getToolTipText());
      }
    });
  }

  private static class DeviceNamePredicate implements Predicate<String> {
    private static final String FILE_ARROW = "\u2192"; // Same as com.android.tools.idea.configurations.ConfigurationAction#FILE_ARROW
    private final String deviceName;

    DeviceNamePredicate(@NotNull String deviceName) {
      this.deviceName = deviceName;
    }

    @Override
    public boolean test(String item) {
      if (item.contains(FILE_ARROW)) {
        return deviceName.equals(item.substring(0, item.lastIndexOf(FILE_ARROW)).trim());
      }
      else if (item.contains("(")) {
        return deviceName.equals(item.substring(item.lastIndexOf('(') + 1, item.lastIndexOf(')')));
      }
      return false;
    }
  }
}
