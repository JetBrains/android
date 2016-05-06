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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JavadocDialogFixture implements ContainerFixture<JDialog> {

  public static JavadocDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), MATCHER);
    return new JavadocDialogFixture(ideFrameFixture, dialog);
  }

  private static final GenericTypeMatcher<JDialog> MATCHER = new GenericTypeMatcher<JDialog>(JDialog.class) {
    @Override
    protected boolean isMatching(@NotNull JDialog dialog) {
      return "Specify Generate JavaDoc Scope".equals(dialog.getTitle());
    }
  };

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  private JavadocDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
    myRobot = ideFrameFixture.robot();
  }

  @NotNull
  public JavadocDialogFixture enterOutputDirectory(@NotNull String path) {
    JTextField textField =
      GuiTests.waitUntilFound(myRobot, myDialog, GuiTests.matcherForType(TextFieldWithBrowseButton.class)).getTextField();
    myRobot.focusAndWaitForFocusGain(textField);
    myRobot.enterText(path);
    return this;
  }

  @NotNull
  public ExecutionToolWindowFixture.ContentFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return new RunToolWindowFixture(myIdeFrameFixture).findContent("Javadoc");
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
