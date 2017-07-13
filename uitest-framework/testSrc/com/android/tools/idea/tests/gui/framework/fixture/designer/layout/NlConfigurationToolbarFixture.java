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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ThemeSelectionDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
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
public class NlConfigurationToolbarFixture<ParentFixture> {

  @NotNull private final ParentFixture myParentFixture;
  private final Robot myRobot;
  private final ActionToolbar myToolBar;
  private final EditorDesignSurface mySurface;

  public NlConfigurationToolbarFixture(
    @NotNull ParentFixture parentFixture, @NotNull Robot robot, @NotNull EditorDesignSurface surface, @NotNull ActionToolbar toolbar) {
    myParentFixture = parentFixture;
    myRobot = robot;
    myToolBar = toolbar;
    mySurface = surface;
  }

  @NotNull
  public ParentFixture leaveConfigToolbar() {
    return myParentFixture;
  }

  /**
   * Requires the orientation name to be the given name (typically Portrait or Landscape)
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireOrientation(@NotNull String name) {
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

  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseApiLevel(@NotNull String apiLevel) {
    new JButtonFixture(myRobot, findToolbarButton("API Version in Editor")).click();
    clickPopupMenuItemMatching(new ApiLevelPredicate(apiLevel), myToolBar.getComponent(), myRobot);
    return this;
  }

  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireApiLevel(@NotNull String apiLevel) {
    Wait.seconds(1).expecting("API level to be updated").until(() -> apiLevel.equals(getApiLevel()));
    return this;
  }

  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireTheme(@NotNull String theme) {
    Wait.seconds(1).expecting("theme to be updated")
      .until(() -> theme.equals(new JButtonFixture(myRobot, findToolbarButton("Theme in Editor")).text()));
    return this;
  }

  /**
   * Returns the current API level of the toolbar's configuration as a String
   */
  public String getApiLevel() {
    return new JButtonFixture(myRobot, findToolbarButton("API Version in Editor")).text();
  }

  /**
   * Selects a device matching the given label prefix in the configuration toolbar's device menu
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseDevice(@NotNull String label) {
    new JButtonFixture(myRobot, findToolbarButton("Device in Editor")).click();
    clickPopupMenuItemMatching(new DeviceNamePredicate(label), myToolBar.getComponent(), myRobot);
    return this;
  }

  public void chooseLayoutVariant(@NotNull String layoutVariant) {
    new JButtonFixture(myRobot, findToolbarButton("Layout Variants")).click();
    clickPopupMenuItemMatching(Predicate.isEqual(layoutVariant), myToolBar.getComponent(), myRobot);
  }

  /**
   * Selects the density matching the given string in the configuration toolbar's density menu
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseDensity(@NotNull String density) {
    new JButtonFixture(myRobot, findToolbarButton("Device Screen Density")).click();
    clickPopupMenuItem(density, myToolBar.getComponent(), myRobot);
    return this;
  }

  /**
   * Requires the device density to be the given density
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireDensity(@NotNull String density) {
    Wait.seconds(1).expecting("configuration to be updated").until(() -> {
      Configuration configuration = mySurface.getConfiguration();
      if (configuration != null) {
        Device device = configuration.getDevice();
        return device != null && density.equals(device.getDefaultState().getHardware().getScreen().getPixelDensity().getResourceValue());
      }
      return false;
    });
    return this;
  }

  /**
   * Selects the shape matching the given string in the configuration toolbar's shape menu
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseShape(@NotNull String shape) {
    new JButtonFixture(myRobot, findToolbarButton("Adaptive Icon Shape")).click();
    clickPopupMenuItem(shape, myToolBar.getComponent(), myRobot);
    return this;
  }

  /**
   * Clicks on the theme toolbar button and opens the theme selection dialog
   */
  @NotNull
  public ThemeSelectionDialogFixture openThemeSelectionDialog() {
    new JButtonFixture(myRobot, findToolbarButton("Theme in Editor")).click();
    return ThemeSelectionDialogFixture.find(myRobot);
  }

  /**
   * Requires the device id to be the given id
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireDevice(@NotNull String id) {
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
   * Click on the "Show Design" button if not selected
   */
  public NlConfigurationToolbarFixture<ParentFixture> showDesign() {
    ActionButtonFixture fixture = getToggleDesignButton();
    if (fixture.target().getPopState() != ActionButtonComponent.PUSHED) {
      fixture.click();
    }
    return this;
  }

  /**
   * Click on the "Show Design" if it is selected
   */
  public NlConfigurationToolbarFixture<ParentFixture> hideDesign() {
    ActionButtonFixture fixture = getToggleDesignButton();
    if (fixture.target().getPopState() == ActionButtonComponent.PUSHED) {
      fixture.click();
    }
    return this;
  }

  @NotNull
  private ActionButtonFixture getToggleDesignButton() {
    ActionButton button = waitUntilShowing(myRobot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return "Show Design".equals(component.getAction().getTemplatePresentation().getDescription());
      }
    });
    return new ActionButtonFixture(myRobot, button);
  }

  /**
   * Click on the "Show Blueprint" button if not selected
   */
  public NlConfigurationToolbarFixture<ParentFixture> showBlueprint() {
    ActionButtonFixture fixture = getBlueprintButton();
    if (fixture.target().getPopState() != ActionButtonComponent.PUSHED) {
      fixture.click();
    }
    return this;
  }

  /**
   * Click on the "Show Blueprint" button if not selected
   */
  public NlConfigurationToolbarFixture<ParentFixture> hideBlueprint() {
    ActionButtonFixture fixture = getBlueprintButton();
    if (fixture.target().getPopState() == ActionButtonComponent.PUSHED) {
      fixture.click();
    }
    return this;
  }

  @NotNull
  private ActionButtonFixture getBlueprintButton() {
    ActionButton button = waitUntilShowing(myRobot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return "Show Blueprint".equals(component.getAction().getTemplatePresentation().getDescription());
      }
    });
    return new ActionButtonFixture(myRobot, button);
  }

  /**
   * Click on the "Orientation in Editor" button
   */
  public NlConfigurationToolbarFixture<ParentFixture> switchOrientation() {
    new JButtonFixture(myRobot, findToolbarButton("Orientation in Editor")).click();
    return this;
  }

  @NotNull
  private JButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilShowingAndEnabled(myRobot, myToolBar.getComponent(), Matchers.byTooltip(JButton.class, tooltip));
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

  private static class ApiLevelPredicate implements Predicate<String> {
    private final String apiLevel;

    ApiLevelPredicate(@NotNull String apiLevel) {
      this.apiLevel = apiLevel;
    }

    @Override
    public boolean test(@NotNull String item) {
      item = item.trim();
      return item.endsWith(apiLevel);
    }
  }
}
