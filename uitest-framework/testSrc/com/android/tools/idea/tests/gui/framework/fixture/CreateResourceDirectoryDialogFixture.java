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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.jetbrains.android.actions.CreateResourceDirectoryDialog;
import org.jetbrains.annotations.NotNull;

public final class CreateResourceDirectoryDialogFixture extends IdeaDialogFixture<CreateResourceDirectoryDialog> {
  public CreateResourceDirectoryDialogFixture(@NotNull Robot robot) {
    super(robot, find(robot, CreateResourceDirectoryDialog.class));
  }

  public void waitUntilErrorLabelFound(@NotNull String text) {
    text = "<html><font color='#" + ColorUtil.toHex(JBColor.RED) + "'><left>" + text + "</left></b></font></html>";
    GuiTests.waitUntilFound(robot(), target(), JLabelMatcher.withText(text));
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
  }
}
