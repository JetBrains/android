/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.ui.ASGallery;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewModuleDialogFixture implements ContainerFixture<JDialog> {

  public static NewModuleDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), MATCHER);
    return new NewModuleDialogFixture(ideFrameFixture, dialog);
  }

  private static final GenericTypeMatcher<JDialog> MATCHER = new GenericTypeMatcher<JDialog>(JDialog.class) {
    @Override
    protected boolean isMatching(@NotNull JDialog dialog) {
      return "Create New Module".equals(dialog.getTitle());
    }
  };

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  private NewModuleDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
    myRobot = ideFrameFixture.robot();
  }

  @NotNull
  public NewModuleDialogFixture chooseModuleType(String name) {
    new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class)).clickItem(name);
    return this;
  }

  @NotNull
  public NewModuleDialogFixture setModuleName(String name) {
    new JTextComponentFixture(robot(), robot().finder().findByName(target(), "ModuleName", JTextField.class)).selectAll().enterText(name);
    return this;
  }

  @NotNull
  public NewModuleDialogFixture clickNextToStep(String name) {
    GuiTests.findAndClickButton(this, "Next");
    Wait.seconds(5).expecting("next step to appear").until(
      () -> robot().finder().findAll(target(), JLabelMatcher.withText(name).andShowing()).size() == 1);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    GuiTests.findAndClickButton(this, "Finish");
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
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
    return myRobot;
  }
}
