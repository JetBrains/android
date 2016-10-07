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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.google.common.truth.Truth.assertThat;

public class ConfigureAvdOptionsStepFixture extends AbstractWizardStepFixture<ConfigureAvdOptionsStepFixture> {

  protected ConfigureAvdOptionsStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureAvdOptionsStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture showAdvancedSettings() {
    try {
      JButton showAdvancedSettingsButton = robot().finder().find(target(), new GenericTypeMatcher<JButton>(JButton.class) {
        @Override
        protected boolean isMatching(@NotNull JButton component) {
          return "Show Advanced Settings".equals(component.getText());
        }
      });
      robot().click(showAdvancedSettingsButton);
    } catch (ComponentLookupException e) {
      throw new RuntimeException("Show Advanced Settings called when advanced settings are already shown.", e);
    }
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture requireAvdName(@NotNull String name) {
    String text = findTextFieldWithLabel("AVD Name").getText();
    assertThat(text).named("AVD name").isEqualTo(name);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture setAvdName(@NotNull String name) {
    JTextComponent textFieldWithLabel = findTextFieldWithLabel("AVD Name");
    replaceText(textFieldWithLabel, name);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture setFrontCamera(@NotNull String selection) {
    JComboBoxFixture frontCameraFixture = findComboBoxWithLabel("Front:");
    frontCameraFixture.selectItem(selection);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture selectGraphicsHardware() {
    findComboBoxWithLabel("Graphics:").selectItem("Hardware - GLES 2.0");
    return this;
  }
}
