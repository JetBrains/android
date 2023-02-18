/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JDialog;
import java.awt.Component;
import java.awt.Dialog;

public class RefactoringDialogFixture extends DialogFixture {
  @NotNull
  public static RefactoringDialogFixture find(@NotNull Robot robot, @NotNull String dialogTitle) {
    Component dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class, true) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return dialogTitle.equals(component.getTitle());
      }
    });
    return new RefactoringDialogFixture(robot, (Dialog) dialog);
  }

  public RefactoringDialogFixture(@NotNull Robot robot, @NotNull Dialog target) {
    super(robot, target);
  }

  @NotNull
  public JButtonFixture getPreviewButton() {
    return button(new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return "Preview".equals(component.getText());
      }
    });
  }

  public void waitForDialogToDisappear() {
    Wait.seconds(60).expecting(target().getTitle() + " dialog to disappear")
      .until(() -> !target().isShowing());
  }
}
