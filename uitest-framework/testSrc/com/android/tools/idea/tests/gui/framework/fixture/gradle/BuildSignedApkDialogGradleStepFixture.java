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
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BuildSignedApkDialogGradleStepFixture implements ContainerFixture<JDialog> {

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  BuildSignedApkDialogGradleStepFixture(@NotNull IdeFrameFixture ideFrameFixture, JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
    myRobot = ideFrameFixture.robot();
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture apkDestinationFolder(@NotNull String folder) {
    JTextField textField = robot().finder().findByLabel("APK Destination Folder:", TextFieldWithBrowseButton.class).getTextField();
    new JTextComponentFixture(robot(), textField).deleteText().enterText(folder);
    return this;
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture setV1SignatureEnabled(boolean enabled) {
    JCheckBox v1CheckBox = (JCheckBox) robot().finder()
      .find(c -> c instanceof JCheckBox && "V1 (Jar Signature)".equals(((JCheckBox) c).getText()));
    if (enabled) {
      new JCheckBoxFixture(robot(), v1CheckBox).select();
    } else {
      new JCheckBoxFixture(robot(), v1CheckBox).deselect();
    }

    return this;
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture setV2SignatureEnabled(boolean enabled) {
    JCheckBox v2CheckBox = (JCheckBox) robot().finder()
      .find(c -> c instanceof JCheckBox && "V2 (Full APK Signature)".equals(((JCheckBox) c).getText()));
    if (enabled) {
      new JCheckBoxFixture(robot(), v2CheckBox).select();
    } else {
      new JCheckBoxFixture(robot(), v2CheckBox).deselect();
    }

    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    GuiTests.findAndClickButtonWhenEnabled(this, "Finish");
    Wait.seconds(1).expecting("dialog to disappear").until(() -> !target().isShowing());
    GuiTests.waitForBackgroundTasks(robot());  // because "Finish" here starts a Gradle build
    return myIdeFrameFixture;
  }

  @NotNull
  public BuildSignedApkDialogGradleStepFixture clickFinishAndDismissErrorDialog() {
    GuiTests.findAndClickButtonWhenEnabled(this, "Finish");
    MessagesFixture.findByTitle(robot(), "Error")
      .requireMessageContains("Please select at least one of the signature versions to use")
      .clickOk();

    return this;
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
