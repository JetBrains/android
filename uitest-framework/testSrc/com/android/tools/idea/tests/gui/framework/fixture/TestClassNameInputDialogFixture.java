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

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.gct.testrecorder.ui.TestClassNameInputDialog;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

/** Fixture for {@link com.google.gct.testrecorder.ui.TestClassNameInputDialog}. */
public class TestClassNameInputDialogFixture extends IdeaDialogFixture<TestClassNameInputDialog> {

  @NotNull
  public static TestClassNameInputDialogFixture find(@NotNull Robot robot) {
    return new TestClassNameInputDialogFixture(
      robot, find(robot, TestClassNameInputDialog.class, Matchers.byTitle(JDialog.class, "Specify a test class for your test")));
  }

  private TestClassNameInputDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<TestClassNameInputDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }
}
