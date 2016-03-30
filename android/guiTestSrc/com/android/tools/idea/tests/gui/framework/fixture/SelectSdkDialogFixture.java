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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.sdk.SelectSdkDialog;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class SelectSdkDialogFixture extends IdeaDialogFixture<SelectSdkDialog> {
  @NotNull
  public static SelectSdkDialogFixture find(@NotNull Robot robot) {
    return new SelectSdkDialogFixture(robot, find(robot, SelectSdkDialog.class));
  }

  private SelectSdkDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<SelectSdkDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public SelectSdkDialogFixture setJdkPath(@NotNull final File path) {
    final JLabel label = robot().finder().find(target(), JLabelMatcher.withText("Select Java JDK:").andShowing());
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        Component textField = label.getLabelFor();
        assertThat(textField).isInstanceOf(JTextField.class);
        ((JTextField)textField).setText(path.getPath());
      }
    });
    return this;
  }

  @NotNull
  public SelectSdkDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }
}
