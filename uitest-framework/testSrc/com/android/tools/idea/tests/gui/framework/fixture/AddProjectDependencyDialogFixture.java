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

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import javax.swing.JDialog;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

public class AddProjectDependencyDialogFixture implements ContainerFixture<JDialog> {

  public static AddProjectDependencyDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Add Project Dependency"));
    return new AddProjectDependencyDialogFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;

  private AddProjectDependencyDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  @NotNull
  public IdeFrameFixture clickOk() {
    findAndClickOkButton(this);
    return myIdeFrameFixture;
  }

  @NotNull
  public FailedToAddDependencyDialogFixture clickCancel() {
    findAndClickCancelButton(this);
    return FailedToAddDependencyDialogFixture.find(myIdeFrameFixture);
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
