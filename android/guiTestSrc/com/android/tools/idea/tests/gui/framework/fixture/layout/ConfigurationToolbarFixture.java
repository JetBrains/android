/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceChooserDialogFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.clickPopupMenuItem;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Fixture representing the configuration toolbar above an associated layout editor
 */
public class ConfigurationToolbarFixture {
  private final Robot myRobot;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final LayoutFixture myEditorFixture;
  private final RenderContext myRenderContext;
  private final ConfigurationToolBar myToolbarWidget;

  public ConfigurationToolbarFixture(@NotNull Robot robot, @NotNull LayoutFixture editorFixture, @NotNull RenderContext renderContext,
                                     @NotNull ConfigurationToolBar toolbarWidget) {
    myRobot = robot;
    myEditorFixture = editorFixture;
    myRenderContext = renderContext;
    myToolbarWidget = toolbarWidget;
  }

  /**
   * Requires the device id to be the given id
   */
  @SuppressWarnings("ConstantConditions")
  public ConfigurationToolbarFixture requireDevice(@NotNull String id)  {
    assertEquals(id, getConfiguration().getDevice().getId());
    return this;
  }


  /** Returns true if the given device is currently selected */
  public boolean isDevice(String id) {
    Device device = getNonNullConfiguration().getDevice();
    assertNotNull(device);
    return id.equals(device.getId());
  }

  /**
   * Requires the orientation name to be the given name (typically Portrait or Landscape)
   */
  @NotNull
  public ConfigurationToolbarFixture requireOrientation(@NotNull String name)  {
    State deviceState = getNonNullConfiguration().getDeviceState();
    assertNotNull(deviceState);
    assertEquals(name, deviceState.getName());
    return this;
  }

  /**
   * Requires the configuration theme to be the given theme
   */
  @NotNull
  public ConfigurationToolbarFixture requireTheme(@NotNull String theme)  {
    assertEquals(theme, getNonNullConfiguration().getTheme());
    return this;
  }

  /**
   * Toggles orientation between landscape and portrait
   */
  public void toggleOrientation() {
    JButton button = findToolbarButton("Go to next state");
    myRobot.click(button);
  }

  /**
   * Invokes the "Create Landscape Variation" action in the configuration toolbar's configuration menu
   */
  public void createLandscapeVariation() {
    JButton menuButton = findToolbarButton("Configuration to render this layout with inside the IDE");
    myRobot.click(menuButton);

    doClickPopupMenuItem("Create Landscape Variation");
  }

  public void chooseLocale(@NotNull String locale) {
    JButton localeChooser = findToolbarButton("Locale to render layout with inside the IDE");
    myRobot.click(localeChooser);

    doClickPopupMenuItem(locale);
  }

  public void removePreviews() {
    JButton menuButton = findToolbarButton("Configuration to render this layout with inside the IDE");
    myRobot.click(menuButton);

    doClickPopupMenuItem("None");
  }

  /**
   * Selects a device matching the given label prefix in the configuration toolbar's device menu
   */
  public void chooseDevice(String labelPrefix) {
    JButton menuButton = findToolbarButton("The virtual device to render the layout with");
    myRobot.click(menuButton);

    doClickPopupMenuItem(labelPrefix);
  }

  public void createOtherVariation(@NotNull String variation) {
    JButton menuButton = findToolbarButton("Configuration to render this layout with inside the IDE");
    myRobot.click(menuButton);

    doClickPopupMenuItem("Create Other...");
    ResourceChooserDialogFixture resourceChooser = ResourceChooserDialogFixture.findDialog(myRobot);
    resourceChooser.setDirectoryName(variation);
    resourceChooser.clickOK();
  }

  @NotNull
  private Configuration getNonNullConfiguration() {
    Configuration configuration = getConfiguration();
    assertNotNull(configuration);
    return configuration;
  }

  @Nullable
  private Configuration getConfiguration() {
    return myRenderContext.getConfiguration();
  }

  @NotNull
  private JButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilFound(myRobot, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        return tooltip.equals(button.getToolTipText());
      }
    });
  }

  private void doClickPopupMenuItem(@NotNull String label) {
    clickPopupMenuItem(label, myToolbarWidget, myRobot);
  }
}
