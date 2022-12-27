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

import static com.android.tools.idea.wizard.template.Language.Java;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.openapi.fileEditor.FileEditorManager;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WizardUtils {
  private WizardUtils() {
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest) {
    createNewProject(guiTest, "Empty Views Activity");

    // Assert it opens the Code and Layout files on the editor
    assertThat(FileEditorManager.getInstance(guiTest.ideFrame().getProject()).getOpenFiles()).hasLength(2);
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest, @NotNull String activity) {
    createNewProject(guiTest, activity, Java);
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest, @NotNull String activity, @Nullable Language language) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity(activity)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .selectMinimumSdkApi(23)
      .setSourceLanguage(language)
      .enterPackageName("com.google.myapplication")
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app"); // Focus "app" in "Android Pane" to allow adding Activities through the menus (instead of right click)
  }
  @NotNull
  public static void createNewProject(@NotNull GuiTestRule guiTest,
                                                    @NotNull String template,
                                                    @NotNull String appName,
                                                    @NotNull String appPackageName,
                                                    int minSdkApi,
                                                    @NotNull Language language) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity(template)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterName(appName)
      .enterPackageName(appPackageName)
      .selectMinimumSdkApi(minSdkApi)
      .setSourceLanguage(language)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app"); // Focus "app" in "Android Pane" to allow adding Activities through the menus (instead of right click)
  }

  //To create a native C++ project, which has additional steps.
  @NotNull
  public static void createNativeCPlusPlusProject(@NotNull GuiTestRule guiTest,
                                      @NotNull String appName,
                                      @NotNull String appPackageName,
                                      int minSdkApi,
                                      @NotNull Language language) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity("Native C++")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterName(appName)
      .enterPackageName(appPackageName)
      .selectMinimumSdkApi(minSdkApi)
      .setSourceLanguage(language)
      .wizard()
      .clickNext()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app"); // Focus "app" in "Android Pane" to allow adding Activities through the menus (instead of right click)
  }
}
