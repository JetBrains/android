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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguage;
import com.android.tools.idea.wizard.template.CppStandardType;
import com.android.tools.idea.wizard.template.Language;
import javax.swing.JComboBox;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

public class ConfigureAndroidModuleStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureAndroidModuleStepFixture, W> {

  ConfigureAndroidModuleStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureAndroidModuleStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> enterModuleName(@NotNull String text) {
    new JTextComponentFixture(robot(), robot().finder().findByLabel(target(), "Module name", JTextField.class)).setText(text);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> selectMinimumSdkApi(int minSdkApi) {
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Minimum SDK", JComboBox.class))
      .selectApiLevel(minSdkApi);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> selectBytecodeLevel(@NotNull String bytecodeLevel) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Bytecode Level", JComboBox.class))
      .selectItem(bytecodeLevel);
    return this;
  }


  @NotNull
  public ConfigureAndroidModuleStepFixture<W> setSourceLanguage(@NotNull Language language) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Language", JComboBox.class, true))
      .selectItem(language.toString());
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> setBuildConfigurationLanguage(@NotNull BuildConfigurationLanguage language) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Build configuration language", JComboBox.class, true))
      .selectItem(language.toString());
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> setCppStandard(@NotNull CppStandardType cppStandard) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "C++ Standard", JComboBox.class, true))
      .selectItem(cppStandard.name());
    return this;
  }
}
