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
package com.android.tools.idea.tests.gui.gradle;

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.ComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.base.Strings.nullToEmpty;

// TODO: Generalize this class as MessageDialogFixture.
class GradleSyncMessageDialogFixture extends ComponentFixture<JDialog> {
  @NotNull
  static GradleSyncMessageDialogFixture find(@NotNull final Robot robot) {
    // Expect a dialog explaining that the version of Gradle in the project's wrapper needs to be updated to version 2.1, and click the
    // "OK" button.
    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return "Gradle Sync".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new GradleSyncMessageDialogFixture(robot, dialog);
  }

  private GradleSyncMessageDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  @NotNull
  GradleSyncMessageDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  void clickCancel() {
    findAndClickCancelButton(this);
  }

  @NotNull
  String getMessage() {
    final JTextPane textPane = robot.finder().findByType(target, JTextPane.class);
    return GuiActionRunner.execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return nullToEmpty(textPane.getText());
      }
    });
  }
}
