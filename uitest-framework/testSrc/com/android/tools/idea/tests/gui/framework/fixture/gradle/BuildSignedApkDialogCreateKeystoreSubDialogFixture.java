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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JSpinnerFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BuildSignedApkDialogCreateKeystoreSubDialogFixture implements ContainerFixture<JDialog> {

  private final BuildSignedApkDialogKeystoreStepFixture myStepFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  private BuildSignedApkDialogCreateKeystoreSubDialogFixture(
    @NotNull JDialog dialog, @NotNull BuildSignedApkDialogKeystoreStepFixture stepFixture) {
    myStepFixture = stepFixture;
    myDialog = dialog;
    myRobot = stepFixture.robot();
  }

  @NotNull
  public static BuildSignedApkDialogCreateKeystoreSubDialogFixture find(@NotNull BuildSignedApkDialogKeystoreStepFixture stepFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(stepFixture.robot(), Matchers.byTitle(JDialog.class, "New Key Store"));
    return new BuildSignedApkDialogCreateKeystoreSubDialogFixture(dialog, stepFixture);
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyStorePath(@NotNull String path) {
    JTextField textField = ((TextFieldWithBrowseButton) robot().finder().findByLabel(target(), "Key store path:")).getTextField();
    new JTextComponentFixture(robot(), textField).enterText(path);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture password(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByName("myPasswordField", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture passwordConfirm(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByName("myConfirmedPassword", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture alias(@NotNull String alias) {
    JTextField textField = robot().finder().findByLabel(target(), "Alias:", JTextField.class);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(alias);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyPassword(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByName("myKeyPasswordField", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture keyPasswordConfirm(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByName("myConfirmKeyPasswordField", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture validity(String years) {
    JSpinner spinner = robot().finder().findByLabel("Validity (years):", JSpinner.class);
    new JSpinnerFixture(robot(), spinner).enterTextAndCommit(years);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture firstAndLastName(@NotNull String firstAndLastName) {
    JTextField firstAndLastNameComponent = robot().finder().findByLabel("First and Last Name:", JTextField.class);
    new JTextComponentFixture(robot(), firstAndLastNameComponent).enterText(firstAndLastName);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture organizationalUnit(@NotNull String ou) {
    JTextField ouComponent = robot().finder().findByLabel("Organizational Unit:", JTextField.class);
    new JTextComponentFixture(robot(), ouComponent).enterText(ou);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture organization(@NotNull String org) {
    JTextField orgComponent = robot().finder().findByLabel("Organization:", JTextField.class);
    new JTextComponentFixture(robot(), orgComponent).enterText(org);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture cityOrLocality(@NotNull String city) {
    JTextField cityComponent = robot().finder().findByLabel("City or Locality:", JTextField.class);
    new JTextComponentFixture(robot(), cityComponent).enterText(city);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture stateOrProvince(@NotNull String state) {
    JTextField stateComponent = robot().finder().findByLabel("State or Province:", JTextField.class);
    new JTextComponentFixture(robot(), stateComponent).enterText(state);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture countryCode(@NotNull String countryCode) {
    JTextField ccComponent = robot().finder().findByLabel("Country Code (XX):", JTextField.class);
    new JTextComponentFixture(robot(), ccComponent).enterText(countryCode);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(1).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myStepFixture;
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myRobot;
  }
}
