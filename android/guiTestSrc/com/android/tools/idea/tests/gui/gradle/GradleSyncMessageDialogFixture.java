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
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class GradleSyncMessageDialogFixture extends ComponentFixture<JDialog> {
  @NotNull
  static GradleSyncMessageDialogFixture find(@NotNull final Robot robot) {
    // Expect a dialog explaining that the version of Gradle in the project's wrapper needs to be updated to version 2.1, and click the
    // "OK" button.
    final AtomicReference<JDialog> dialogRef = new AtomicReference<JDialog>();
    Pause.pause(new Condition("Find Gradle version update dialog") {
      @Override
      public boolean test() {
        Collection<JDialog> allFound = robot.finder().findAll(new GenericTypeMatcher<JDialog>(JDialog.class) {
          @Override
          protected boolean isMatching(JDialog dialog) {
            return "Gradle Sync".equals(dialog.getTitle()) && dialog.isShowing();
          }
        });
        boolean found = allFound.size() == 1;
        if (found) {
          dialogRef.set(getFirstItem(allFound));
        }
        return found;
      }
    }, SHORT_TIMEOUT);
    return new GradleSyncMessageDialogFixture(robot, dialogRef.get());
  }

  private GradleSyncMessageDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  void clickOk() {
    findAndClickOkButton(this);
  }

  void clickCancel() {
    findAndClickCancelButton(this);
  }
}
