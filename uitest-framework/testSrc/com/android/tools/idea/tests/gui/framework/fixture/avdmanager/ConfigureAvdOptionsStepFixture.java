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

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.google.common.truth.Truth.assertThat;

public class ConfigureAvdOptionsStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureAvdOptionsStepFixture, W> {

  protected ConfigureAvdOptionsStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureAvdOptionsStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> showAdvancedSettings() {
    try {
      JButton showAdvancedSettingsButton = robot().finder().find(target(), Matchers.byText(JButton.class, "Show Advanced Settings"));
      robot().click(showAdvancedSettingsButton);
    } catch (ComponentLookupException e) {
      throw new RuntimeException("Show Advanced Settings called when advanced settings are already shown.", e);
    }
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> requireAvdName(@NotNull String name) {
    String text = findTextFieldWithLabel("AVD Name").getText();
    assertThat(text).named("AVD name").isEqualTo(name);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> setAvdName(@NotNull String name) {
    JTextComponent textFieldWithLabel = findTextFieldWithLabel("AVD Name");
    replaceText(textFieldWithLabel, name);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> setFrontCamera(@NotNull String selection) {
    JComboBoxFixture frontCameraFixture = findComboBoxWithLabel("Front:");
    frontCameraFixture.selectItem(selection);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> selectGraphicsHardware() {
    findComboBoxWithLabel("Graphics:").selectItem("Hardware - GLES .*");
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> selectGraphicsSoftware() {
    findComboBoxWithLabel("Graphics:").selectItem("Software - GLES .*");
    return this;
  }
}
