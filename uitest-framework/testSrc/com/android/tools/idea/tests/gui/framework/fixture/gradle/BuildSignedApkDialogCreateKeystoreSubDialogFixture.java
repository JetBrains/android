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
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class BuildSignedApkDialogCreateKeystoreSubDialogFixture
    extends ComponentFixture<BuildSignedApkDialogCreateKeystoreSubDialogFixture, JDialog> {

  public BuildSignedApkDialogCreateKeystoreSubDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(BuildSignedApkDialogCreateKeystoreSubDialogFixture.class, robot, target);
  }

  @NotNull
  public static BuildSignedApkDialogCreateKeystoreSubDialogFixture find(@NotNull Robot robot) {
    JDialog frame = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "New Key Store".equals(dialog.getTitle());
      }
    });

    return new BuildSignedApkDialogCreateKeystoreSubDialogFixture(robot, frame);
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyStorePath(@NotNull String path) {
    TextFieldWithBrowseButton textComponent = (TextFieldWithBrowseButton) robot().finder().findByLabel(target(), "Key store path:");
    textComponent.setText(path);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture password(@NotNull String passwd) {
    JPasswordField passwdComponent = robot().finder().findByName("myPasswordField", JPasswordField.class);
    passwdComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture passwordConfirm(@NotNull String passwd) {
    JPasswordField passwdComponent = robot().finder().findByName("myConfirmedPassword", JPasswordField.class);
    passwdComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture alias(@NotNull String alias) {
    JTextComponent aliasComponent = (JTextComponent) robot().finder().findByLabel(target(), "Alias:");
    aliasComponent.setText(alias);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyPassword(@NotNull String passwd) {
    JPasswordField passwdComponent = robot().finder().findByName("myKeyPasswordField", JPasswordField.class);
    passwdComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyPasswordConfirm(@NotNull String passwd) {
    JPasswordField passwdComponent = robot().finder().findByName("myConfirmKeyPasswordField", JPasswordField.class);
    passwdComponent.setText(passwd);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture validity(int years) {
    JSpinner spinnerComponent = robot().finder().findByLabel("Validity (years):", JSpinner.class);
    spinnerComponent.setValue(years);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture firstAndLastName(@NotNull String firstAndLastName) {
    JTextField firstAndLastNameComponent = robot().finder().findByLabel("First and Last Name:", JTextField.class);
    firstAndLastNameComponent.setText(firstAndLastName);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture organizationalUnit(@NotNull String ou) {
    JTextField ouComponent = robot().finder().findByLabel("Organizational Unit:", JTextField.class);
    ouComponent.setText(ou);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture organization(@NotNull String org) {
    JTextField orgComponent = robot().finder().findByLabel("Organization:", JTextField.class);
    orgComponent.setText(org);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture cityOrLocality(@NotNull String org) {
    JTextField cityComponent = robot().finder().findByLabel("City or Locality:", JTextField.class);
    cityComponent.setText(org);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture stateOrProvince(@NotNull String org) {
    JTextField stateComponent = robot().finder().findByLabel("State or Province:", JTextField.class);
    stateComponent.setText(org);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture countryCode(@NotNull String org) {
    JTextField ccComponent = robot().finder().findByLabel("Country Code (XX):", JTextField.class);
    ccComponent.setText(org);
    return this;
  }

  public void ok() {
    JButton ok = (JButton) robot().finder().find(c -> c instanceof JButton && ((JButton) c).getText().equals("OK"));
    robot().click(ok);
  }
}
