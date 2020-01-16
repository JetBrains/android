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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.Language;
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
  public ConfigureNewAndroidProjectStepFixture<W> setSourceLanguage(@NotNull Language language) {
    JComboBoxFixture comboBoxFixture =
      new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Language", JComboBox.class, true));

    // Language comboBox may be disabled, depending on the Activity (eg "Compose Activity")
    if (comboBoxFixture.isEnabled()) {
      comboBoxFixture.selectItem(language.toString());
    }
    else {
      assertThat(comboBoxFixture.selectedItem()).isEqualTo(language.toString());
    }
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> selectMinimumSdkApi(@NotNull FormFactor formFactor, int minSdkApi) {
    String name = formFactor.id + ".minSdk";
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByName(target(), name, JComboBox.class)).selectApiLevel(minSdkApi);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> selectMinimumSdkApi(int minSdkApi) {
    return selectMinimumSdkApi(FormFactor.MOBILE, minSdkApi);
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setPairWithPhoneApp(boolean select) {
    selectCheckBoxWithText("Pair with Empty Phone app", select);
    return this;
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<W> setUseKtsBuildFiles(boolean select) {
    selectCheckBoxWithText("Use Kotlin script (.kts) for Gradle build files", select);
    return this;
  }
}
