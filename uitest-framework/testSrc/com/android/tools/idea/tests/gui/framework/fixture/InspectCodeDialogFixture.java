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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InspectCodeDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static InspectCodeDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    GuiTests.waitForBackgroundTasks(ideFrameFixture.robot());
    return new InspectCodeDialogFixture(
      ideFrameFixture, find(ideFrameFixture.robot(), DialogWrapper.class, Matchers.byTitle(JDialog.class, "Specify Inspection Scope")));
  }

  private final IdeFrameFixture myIdeFrameFixture;

  private InspectCodeDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull DialogAndWrapper<DialogWrapper> dialogAndWrapper) {
    super(ideFrameFixture.robot(), dialogAndWrapper);
    myIdeFrameFixture = ideFrameFixture;
  }

  public InspectionsFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());

    // Wait for processing project usages to finish as running in background.
    GuiTests.waitForBackgroundTasks(robot());

    return InspectionsFixture.find(myIdeFrameFixture);
  }

  public InspectionsFixture clickButton(@NotNull String buttonText) {
    GuiTests.findAndClickButton(this, buttonText);
    Wait.seconds(30).expecting("dialog to disappear").until(() -> !target().isShowing());

    // Wait for processing project usages to finish as running in background.
    GuiTests.waitForBackgroundTasks(robot(), Wait.seconds(180));
    return InspectionsFixture.find(myIdeFrameFixture);
  }

  public void clickAnalyze() {
    GuiTests.findAndClickButton(this, "Analyze");
    waitUntilNotShowing();
    GuiTests.waitForBackgroundTasks(robot(), Wait.seconds(180));
  }
}
