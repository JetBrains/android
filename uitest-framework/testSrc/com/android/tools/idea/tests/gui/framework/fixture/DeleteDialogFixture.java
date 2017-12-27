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

  public DeleteDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull SafeDeleteDialog dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @NotNull
  public static DeleteDialogFixture find(@NotNull Robot robot, @NotNull String title) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, title).and(
      new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          return getDialogWrapperFrom(dialog, SafeDeleteDialog.class) != null;
        }
      }));
    return new DeleteDialogFixture(robot, dialog, getDialogWrapperFrom(dialog, SafeDeleteDialog.class));
  }

  public DeleteDialogFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return this;
  }

  public UnsafeUsagesDialogFixture waitForUnsafeDialog() {
    GuiTests.waitForBackgroundTasks(robot());
    return UnsafeUsagesDialogFixture.find(robot());
  }

  @NotNull
  public DeleteDialogFixture safe(boolean selected) {
    JCheckBoxFixture checkbox =
      new JCheckBoxFixture(robot(), robot().finder().find(Matchers.byText(JCheckBox.class, "Safe delete (with usage search)")));
    if (selected) {
      checkbox.select();
    }
    else {
      checkbox.deselect();
    }
    return this;
  }
}
