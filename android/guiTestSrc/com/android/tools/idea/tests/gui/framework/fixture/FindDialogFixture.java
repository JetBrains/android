/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.find.impl.FindDialog;
import com.intellij.openapi.ui.ComboBox;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class FindDialogFixture extends IdeaDialogFixture<FindDialog> {
  @NotNull
  public static FindDialogFixture find(@NotNull Robot robot) {
    return new FindDialogFixture(robot, find(robot, FindDialog.class));
  }

  private FindDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<FindDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void setTextToFind(@NotNull final String text) {
    final ComboBox input = (ComboBox)getDialogWrapper().getPreferredFocusedComponent();
    assertNotNull(input);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        input.setSelectedItem(text);
      }
    });
  }

  @NotNull
  public FindDialogFixture clickFind() {
    findAndClickButton(this, "Find");
    return this;
  }
}
