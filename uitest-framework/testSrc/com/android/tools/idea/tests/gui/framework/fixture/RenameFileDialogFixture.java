/*
 * Copyright (C) 2019 The Android Open Source Project
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
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class RenameFileDialogFixture implements ContainerFixture<JDialog> {
  @NotNull private final IdeFrameFixture myIdeFrameFixture;
  @NotNull private final JDialog myDialog;

  @NotNull
  public static RenameFileDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(),
                                               Matchers.byTitle(JDialog.class, "Rename"));
    return new RenameFileDialogFixture(ideFrameFixture, dialog);
  }

  private RenameFileDialogFixture(@NotNull IdeFrameFixture ideFrameFixture,
                                  @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  @NotNull
  public RenameFileDialogFixture enterText(@NotNull String text) {
    JTextComponent input = robot().finder().find(target(), JTextComponentMatcher.any());
    new JTextComponentFixture(robot(), input).enterText(text);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickRefactor() {
    GuiTests.findAndClickRefactorButton(this);
    Wait.seconds(2).expecting(target().getTitle() + " dialog to disappear")
      .until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }

  @NotNull
  public IdeFrameFixture clickCancel() {
    GuiTests.findAndClickCancelButton(this);
    Wait.seconds(2).expecting(target().getTitle() + " dialog to disappear")
      .until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myIdeFrameFixture.robot();
  }

}
