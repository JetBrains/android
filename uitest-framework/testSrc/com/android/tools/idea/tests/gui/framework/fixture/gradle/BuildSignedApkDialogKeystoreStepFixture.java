/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BuildSignedApkDialogKeystoreStepFixture extends AbstractWizardFixture<BuildSignedApkDialogKeystoreStepFixture> {

  public BuildSignedApkDialogKeystoreStepFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(BuildSignedApkDialogKeystoreStepFixture.class, robot, target);
  }

  @NotNull
  public static BuildSignedApkDialogKeystoreStepFixture find(@NotNull Robot robot) {
    JDialog frame = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Generate Signed APK".equals(dialog.getTitle());
      }
    });
    return new BuildSignedApkDialogKeystoreStepFixture(robot, frame);
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture createNew() {
    findWizardButton("Create new...").click();
    return BuildSignedApkDialogCreateKeystoreSubDialogFixture.find(robot());
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyStorePassword(@NotNull String passwd) {
    JPasswordField textComponent = robot().finder().findByLabel(target(), "Key store password:", JPasswordField.class);
    textComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyAlias(@NotNull String alias) {
    JTextField textComponent = robot().finder().findByLabel(target(), "Key alias:", TextFieldWithBrowseButton.class).getChildComponent();
    textComponent.setText(alias);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyPassword(@NotNull String passwd) {
    JPasswordField textComponent = robot().finder().findByLabel(target(), "Key password:", JPasswordField.class);
    textComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture next() {
    JButton nextButton = (JButton) robot().finder().find(c -> c instanceof JButton && ((JButton) c).getText().equals("Next"));
    robot().click(nextButton);
    return new BuildSignedApkDialogGradleStepFixture(robot(), target());
  }
}
