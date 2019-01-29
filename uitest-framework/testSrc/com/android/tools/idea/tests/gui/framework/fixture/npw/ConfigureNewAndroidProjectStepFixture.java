/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ConfigureNewAndroidProjectStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureNewAndroidProjectStepFixture, W> {

  ConfigureNewAndroidProjectStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureNewAndroidProjectStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> enterName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Name");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> enterPackageName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Package name");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setSourceLanguage(@NotNull String sourceLanguage) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Language", JComboBox.class, true))
      .selectItem(sourceLanguage);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> selectMinimumSdkApi(@NotNull String api) {
    String name = FormFactor.MOBILE.id + ".minSdk";
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByName(target(), name, JComboBox.class)).selectApiLevel(api);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setIncludeNavController(boolean select) {
    selectCheckBoxWithText("Include Navigation Controller", select);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setUseOfflineRepo(boolean select) {
    selectCheckBoxWithText("Use offline repo", select);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setIncludeInstantApp(boolean select) {
    selectCheckBoxWithText("This project will support instant apps", select);
    return this;
  }
}
