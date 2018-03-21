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
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.JDialog;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class ConfigureKotlinDialogFixture implements ContainerFixture<JDialog>  {

  public static ConfigureKotlinDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Configure Kotlin with Android with Gradle"));
    return new ConfigureKotlinDialogFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;

  private ConfigureKotlinDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  public void clickOk() {
    findAndClickOkButton(this);
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
