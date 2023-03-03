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

import com.android.tools.idea.npw.benchmark.BenchmarkModuleType;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguage;
import com.android.tools.idea.wizard.template.Language;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;

public class ConfigureBenchmarkModuleStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureBenchmarkModuleStepFixture, W> {

  ConfigureBenchmarkModuleStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureBenchmarkModuleStepFixture.class, wizard, target);
  }

  public ConfigureBenchmarkModuleStepFixture<W> selectBenchmarkType(@NotNull BenchmarkModuleType type) {
    findRadioButtonWithText(type.getTitle()).select();
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> enterModuleName(@NotNull String text) {
    findTextFieldWithLabel("Module name").setText(text);
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> enterPackageName(@NotNull String text) {
    findTextFieldWithLabel("Package name").setText(text);
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> selectTargetApplicationModule(@NotNull String targetModuleName) {
    findComboBoxWithLabel("Target application").selectItem(targetModuleName);
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> setSourceLanguage(@NotNull Language language) {
    findComboBoxWithLabel("Language").selectItem(language.toString());
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> selectMinimumSdkApi(int minSdkApi) {
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Minimum SDK", JComboBox.class))
      .selectApiLevel(minSdkApi);
    return this;
  }

  @NotNull
  public ConfigureBenchmarkModuleStepFixture<W> setBuildConfigurationLanguage(@NotNull BuildConfigurationLanguage buildConfigurationLanguage) {
    new JComboBoxFixture(robot(), robot().finder().findByName(target(), "buildConfigurationLanguageCombo", JComboBox.class, true))
      .selectItem(buildConfigurationLanguage.toString());
    return this;
  }

  @NotNull
  private JRadioButtonFixture findRadioButtonWithText(@NotNull String label) {
    JRadioButton radioButton = robot().finder().find(new GenericTypeMatcher<JRadioButton>(JRadioButton.class, true) {
      @Override
      protected boolean isMatching(@NotNull JRadioButton component) {
        return component.getText().equals(label);
      }
    });
    return new JRadioButtonFixture(robot(), radioButton);
  }
}
