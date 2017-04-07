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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BuildSignedApkDialogKeystoreStepFixture implements ContainerFixture<JDialog> {

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  @NotNull
  public static BuildSignedApkDialogKeystoreStepFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Generate Signed APK"));
    return new BuildSignedApkDialogKeystoreStepFixture(ideFrameFixture, dialog);
  }

  private BuildSignedApkDialogKeystoreStepFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
    myRobot = myIdeFrameFixture.robot();
  }

  @NotNull
  public BuildSignedApkDialogCreateKeystoreSubDialogFixture createNew() {
    GuiTests.findAndClickButton(this, "Create new...");
    return BuildSignedApkDialogCreateKeystoreSubDialogFixture.find(this);
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyStorePassword(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByLabel(target(), "Key store password:", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).deleteText().enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyAlias(@NotNull String alias) {
    JTextField textField = robot().finder().findByLabel(target(), "Key alias:", TextFieldWithBrowseButton.class).getChildComponent();
    new JTextComponentFixture(robot(), textField).deleteText().enterText(alias);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogKeystoreStepFixture keyPassword(@NotNull String password) {
    JPasswordField passwordField = robot().finder().findByLabel(target(), "Key password:", JPasswordField.class);
    new JTextComponentFixture(robot(), passwordField).deleteText().enterText(password);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture clickNext() {
    GuiTests.findAndClickButton(this, "Next");
    return new BuildSignedApkDialogGradleStepFixture(myIdeFrameFixture, myDialog);
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
