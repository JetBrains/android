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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComboBoxDriver;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LinkCppProjectFixture implements ContainerFixture<JDialog> {

  public static LinkCppProjectFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Link C++ Project with Gradle"));
    return new LinkCppProjectFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;

  private LinkCppProjectFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  @NotNull
  public LinkCppProjectFixture enterCMakeListsPath(@NotNull String path) {
    JTextField textField =
      GuiTests.waitUntilFound(robot(), myDialog, Matchers.byType(TextFieldWithBrowseButton.class)).getTextField();
    new JTextComponentFixture(robot(), textField).enterText(path);
    return this;
  }

  @NotNull
  public LinkCppProjectFixture selectCMakeBuildSystem() {
    new JComboBoxDriver(robot()).selectItem(robot().finder().find(Matchers.byType(JComboBox.class)), "CMake");
    return this;
  }

  @NotNull
  public IdeFrameFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
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
    return myIdeFrameFixture.robot();
  }
}
