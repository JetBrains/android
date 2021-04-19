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

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.wizard.template.Language;
import javax.swing.JComboBox;
import javax.swing.JRootPane;
import org.jetbrains.annotations.NotNull;

public class ConfigureMicrobenchmarkModuleStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureMicrobenchmarkModuleStepFixture, W> {

  ConfigureMicrobenchmarkModuleStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureMicrobenchmarkModuleStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> enterModuleName(@NotNull String text) {
    findTextFieldWithLabel("Module name").setText(text);
    return this;
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> enterPackageName(@NotNull String text) {
    findTextFieldWithLabel("Package name").setText(text);
    return this;
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> setSourceLanguage(@NotNull Language language) {
    findComboBoxWithLabel("Language").selectItem(language.toString());
    return this;
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> selectMinimumSdkApi(int minSdkApi) {
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Minimum SDK", JComboBox.class))
      .selectApiLevel(minSdkApi);
    return this;
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> setUseKtsBuildFiles(boolean select) {
    selectCheckBoxWithText("Use Kotlin script (.kts) for Gradle build files", select);
    return this;
  }
}
