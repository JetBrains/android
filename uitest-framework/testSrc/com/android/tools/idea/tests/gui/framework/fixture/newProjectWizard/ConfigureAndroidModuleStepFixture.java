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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ConfigureAndroidModuleStepFixture extends AbstractWizardStepFixture<ConfigureAndroidModuleStepFixture> {
  protected ConfigureAndroidModuleStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureAndroidModuleStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture enterModuleName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Application/Library name:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture selectMinimumSdkApi(@NotNull String api) {
    ApiLevelComboBoxFixture apiLevelComboBox = new ApiLevelComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    apiLevelComboBox.selectApiLevel(api);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture selectInstantAppSupport() {
    findInstantAppCheckbox().select();
    return this;
  }

  @NotNull
  public JCheckBoxFixture findInstantAppCheckbox() {
    return new JCheckBoxFixture(robot(), robot().finder().findByName(findActiveInstance(), "instantApp", JCheckBox.class, false));
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture selectBaseAtom() {
    findBaseAtomCheckbox().select();
    return this;
  }

  @NotNull
  public JCheckBoxFixture findBaseAtomCheckbox() {
    return new JCheckBoxFixture(robot(), robot().finder().findByName(findActiveInstance(), "baseAtom", JCheckBox.class, false));
  }

  @NotNull
  public JPanel findActiveInstance() {
    return robot().finder().findByName(target(), "configureAndroidModuleStep", JPanel.class, true);
  }
}
