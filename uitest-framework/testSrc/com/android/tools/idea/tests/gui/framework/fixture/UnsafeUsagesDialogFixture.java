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
import com.intellij.refactoring.safeDelete.UnsafeUsagesDialog;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnsafeUsagesDialogFixture extends IdeaDialogFixture<UnsafeUsagesDialog> {

  public UnsafeUsagesDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull UnsafeUsagesDialog dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @NotNull
  public static UnsafeUsagesDialogFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Usages Detected").and(
      new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          return getDialogWrapperFrom(dialog, UnsafeUsagesDialog.class) != null;
        }
      }));
    return new UnsafeUsagesDialogFixture(robot, dialog, getDialogWrapperFrom(dialog, UnsafeUsagesDialog.class));
  }

  public void deleteAnyway() {
    GuiTests.findAndClickButtonWhenEnabled(this, "Delete Anyway");
    waitUntilNotShowing(); // Mac dialogs have an animation, wait until it hides
  }
}
