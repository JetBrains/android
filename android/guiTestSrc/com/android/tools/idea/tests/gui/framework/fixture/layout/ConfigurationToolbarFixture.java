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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.junit.Assert.assertEquals;

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

  /**
   * Requires the orientation name to be the given name (typically Portrait or Landscape)
   */
  @SuppressWarnings("ConstantConditions")
  public ConfigurationToolbarFixture requireOrientation(@NotNull String name)  {
    assertEquals(name, getConfiguration().getDeviceState().getName());
    return this;
  }


  /**
   * Requires the configuration theme to be the given theme
   */
  @SuppressWarnings("ConstantConditions")
  public ConfigurationToolbarFixture requireTheme(@NotNull String theme)  {
    assertEquals(theme, getConfiguration().getTheme());
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
  @SuppressWarnings("SpellCheckingInspection")
  public void createLandscapeVariation() {
    JButton menuButton = findToolbarButton("Configuration to render this layout with in the IDE");
    myRobot.click(menuButton);

    clickPopupMenuItem("Create Landscape Variation");
  }

  /**
   * Selects a device matching the given label prefix in the configuration toolbar's device menu
   */
  @SuppressWarnings("SpellCheckingInspection")
  public void chooseDevice(String labelPrefix) {
    JButton menuButton = findToolbarButton("The virtual device to render the layout with");
    myRobot.click(menuButton);

    clickPopupMenuItem(labelPrefix);
  }

  @Nullable
  private Configuration getConfiguration() {
    return myRenderContext.getConfiguration();
  }

  @NotNull
  private JButton findToolbarButton(@NotNull final String tooltip) {
    return myRobot.finder().find(myToolbarWidget, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(JButton button) {
        return tooltip.equals(button.getToolTipText());
      }
    });
  }

  private void clickPopupMenuItem(@NotNull String label) {
    GuiTests.clickPopupMenuItem(label, myToolbarWidget, myRobot);
  }
}