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
import com.android.tools.adtui.TextAccessors;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ThemeMenuAction;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ThemeSelectionDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.TestActionEvent;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static com.android.tools.idea.tests.gui.framework.GuiTests.clickPopupMenuItemMatching;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;

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
    selectDropDownActionButtonItem("API Version in Editor", new ApiLevelPredicate(apiLevel));
    return this;
  }

  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> requireApiLevel(@NotNull String apiLevel) {
    Wait.seconds(1).expecting("API level to be updated").until(() -> apiLevel.equals(getApiLevel()));
    return this;
  }

  public void requireTheme(@NotNull String theme) {
    Wait.seconds(1).expecting("theme to be updated")
      .until(() -> theme.equals(TextAccessors.getTextAccessor(findToolbarButton("Theme in Editor")).getText()));
  }

  /**
   * Returns the current API level of the toolbar's configuration as a String
   */
  public String getApiLevel() {
    return TextAccessors.getTextAccessor(findToolbarButton("API Version in Editor")).getText();
  }

  /**
   * Selects a device matching the given label prefix in the configuration toolbar's device menu
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseDevice(@NotNull String label) {
    selectDropDownActionButtonItem("Device in Editor (D)", new DeviceNamePredicate(label));
    return this;
  }

  public void chooseLayoutVariant(@NotNull String layoutVariant) {
    selectDropDownActionButtonItem("Orientation in Editor (O)", Predicate.isEqual(layoutVariant));
  }

  /**
   * Selects the density matching the given string in the configuration toolbar's density menu
   */
  @NotNull
  public NlConfigurationToolbarFixture<ParentFixture> chooseDensity(@NotNull String density) {
    selectDropDownActionButtonItem("Device Screen Density", item -> item.startsWith(density));
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
    selectDropDownActionButtonItem("Adaptive Icon Shape", item -> item.startsWith(shape));
    return this;
  }

  /**
   * Clicks on the theme toolbar button and opens the theme selection dialog
   */
  @NotNull
  public ThemeSelectionDialogFixture openThemeSelectionDialog() {
    // We directly perform the action here because ActionButton of Theme may be collapsed and cannot be found by finder.
    AnAction themeMenuAction = myToolBar.getActions().stream().filter(action -> action instanceof ThemeMenuAction).findAny().get();
    ApplicationManager.getApplication().invokeLater(() -> themeMenuAction.actionPerformed(new TestActionEvent()));
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

  public void selectDesign() {
    selectDropDownActionButtonItem("Select Design Surface (B)", Predicate.isEqual("Design"));
  }

  public void selectBlueprint() {
    selectDropDownActionButtonItem("Select Design Surface (B)", Predicate.isEqual("Blueprint"));
  }

  public NlConfigurationToolbarFixture<ParentFixture> setOrientationAsLandscape() {
    // If there is any Landscape variation, the text of Action Button will become "Landscape -> [variation_folder]/[layout_name].xml"
    // Use String.startsWith() to cover that case.
    selectDropDownActionButtonItem("Orientation in Editor (O)", item -> item.startsWith("Landscape"));
    return this;
  }

  public NlConfigurationToolbarFixture<ParentFixture> setOrientationAsPortrait() {
    // If there is any Portrait variation, the text of Action Button will become "Portrait -> [variation_folder]/[layout_name].xml"
    // Use String.startsWith() to cover that case.
    selectDropDownActionButtonItem("Orientation in Editor (O)", item -> item.startsWith("Portrait"));
    return this;
  }

  private void selectDropDownActionButtonItem(@NotNull String tooltip, @NotNull Predicate<String> predicate) {
    new ActionButtonFixture(myRobot, findToolbarButton(tooltip)).click();
    clickPopupMenuItemMatching(predicate, myToolBar.getComponent(), myRobot);
  }

  @NotNull
  private ActionButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilShowingAndEnabled(myRobot, myToolBar.getComponent(), Matchers.byTooltip(ActionButton.class, tooltip));
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
      return item.trim().endsWith(apiLevel);
    }
  }
}