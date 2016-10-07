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

import com.android.tools.idea.ui.LabelWithEditLink;
import com.intellij.ui.HyperlinkLabel;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class ConfigureAndroidProjectStepFixture extends AbstractWizardStepFixture<ConfigureAndroidProjectStepFixture> {
  protected ConfigureAndroidProjectStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureAndroidProjectStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterApplicationName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Application name:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterCompanyDomain(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Company Domain:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterPackageName(@NotNull String text) {
    LabelWithEditLink link = robot().finder().findByType(target(), LabelWithEditLink.class);

    HyperlinkLabel editLabel = robot().finder().findByType(link, HyperlinkLabel.class);
    robot().click(editLabel);

    JTextComponent textField = findTextFieldWithLabel("Package name:");
    replaceText(textField, text);

    // click "Done"
    robot().click(editLabel);
    return this;
  }

  @NotNull
  public File getLocationInFileSystem() {
    String location = findTextFieldWithLabel("Project location:").getText();
    assertThat(location).isNotNull();
    assertThat(location).isNotEmpty();
    return new File(location);
  }
}
