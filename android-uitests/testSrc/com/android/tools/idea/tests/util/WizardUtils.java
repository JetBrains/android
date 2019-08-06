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
package com.android.tools.idea.tests.util;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.fileEditor.FileEditorManager;
import org.jetbrains.annotations.NotNull;

public final class WizardUtils {
  private WizardUtils() {
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest) {
    createNewProject(guiTest, "Empty Activity");

    // Assert it opens the Code and Layout files on the editor
    assertThat(FileEditorManager.getInstance(guiTest.ideFrame().getProject()).getOpenFiles()).hasLength(2);
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest, @NotNull String activity) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity(activity)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .setSourceLanguage("Java")
      .enterPackageName("com.google.myapplication")
      .wizard()
      .clickFinish();

    IdeFrameFixture frame =  guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app"); // Focus "app" in "Android Pane" to allow adding Activities through the menus (instead of right click)

    // Hide Gradle tool window if needed, as it takes too much space at the right of the editors and might grab the focus (b/138841171)
    frame.getGradleToolWindow().hide();
  }
}
