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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.refactoring.safeDelete.SafeDeleteDialog;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;

public class DeleteDialogFixture extends IdeaDialogFixture<SafeDeleteDialog> {

  private final IdeFrameFixture ideFrame;
  private final JCheckBoxFixture safeDeleteCheckbox;

  public DeleteDialogFixture(@NotNull IdeFrameFixture ideFrame, @NotNull JDialog target, @NotNull SafeDeleteDialog dialogWrapper) {
    super(ideFrame.robot(), target, dialogWrapper);
    this.ideFrame = ideFrame;
    safeDeleteCheckbox = new JCheckBoxFixture(robot(), robot().finder().find(Matchers.byText(JCheckBox.class, "Safe delete (with usage search)")));
  }

  @NotNull
  public static DeleteDialogFixture find(@NotNull IdeFrameFixture ideFrame) {
    Robot robot = ideFrame.robot();
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Delete").and(
      new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          return getDialogWrapperFrom(dialog, SafeDeleteDialog.class) != null;
        }
      }));
    return new DeleteDialogFixture(ideFrame, dialog, getDialogWrapperFrom(dialog, SafeDeleteDialog.class));
  }

  @NotNull
  public UnsafeUsagesDialogFixture safeDelete() {
    safeDeleteCheckbox.select();
    GuiTests.findAndClickOkButton(this);
    GuiTests.waitForBackgroundTasks(robot());
    return UnsafeUsagesDialogFixture.find(ideFrame);
  }

  @NotNull
  public IdeFrameFixture unsafeDelete() {
    safeDeleteCheckbox.deselect();
    GuiTests.findAndClickOkButton(this);
    waitUntilNotShowing();
    return ideFrame;
  }

}
