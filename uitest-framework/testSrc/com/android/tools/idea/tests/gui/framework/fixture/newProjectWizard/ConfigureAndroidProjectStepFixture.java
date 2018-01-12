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

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ConfigureAndroidProjectStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureAndroidProjectStepFixture, W> {

  protected ConfigureAndroidProjectStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureAndroidProjectStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<W> enterApplicationName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Application name:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<W> enterCompanyDomain(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Company domain");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<W> setCppSupport(boolean select) {
    selectCheckBoxWithText("Include C++ support", select);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<W> setKotlinSupport(boolean select) {
    selectCheckBoxWithText("Include Kotlin support", select);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<W> enterPackageName(@NotNull String text) {
    LabelWithEditButton editLabel = robot().finder().findByType(target(), LabelWithEditButton.class);

    JButton editButton = robot().finder().findByType(editLabel, JButton.class);
    robot().click(editButton);

    JTextComponent textField = findTextFieldWithLabel("Package name");
    replaceText(textField, text);

    // click "Done"
    robot().click(editButton);
    return this;
  }
}
