/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class ProjectWithKotlinTestUtil {

  public static final String CLASS_NAME = "KotlinClass";
  public static final String KOTLIN_EXTENSION = ".kt";
  public static final String FILE_NAME = "KotlinFile";
  public static final String APP = "app";
  public static final String SRC = "src";
  public static final String MAIN = "main";
  public static final String JAVA = "java";
  public static final String MENU_FILE = "File";
  public static final String MENU_NEW = "New";
  public static final String KOTLIN_FILE_CLASS = "Kotlin File/Class";
  public static final String INTERFACE_NAME = "KotlinInterface";
  public static final String ENUM_NAME = "KotlinEnum";
  public static final String OBJECT_NAME = "KotlinObject";
  public static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);

  protected static void createKotlinFileAndClassAndVerify(@NotNull String projectDirName,
                                                 @NotNull String packageName,
                                                 boolean withKotlinSupport,
                                                 GuiTestRule guiTest,
                                                 EmulatorTestRule emulator) throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish(projectDirName);

    ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();

    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, CLASS_NAME, "Class");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, FILE_NAME, "File");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, INTERFACE_NAME, "Interface");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, ENUM_NAME, "Enum class");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, OBJECT_NAME, "Object");

    if (!withKotlinSupport) {
      EditorNotificationPanelFixture editorNotificationPanelFixture =
        ideFrameFixture.getEditor().awaitNotification("Kotlin not configured");
      editorNotificationPanelFixture.performActionWithoutWaitingForDisappearance("Configure");

      // As default, "All modules containing Kotlin files" option is selected for now.
      ConfigureKotlinDialogFixture.find(ideFrameFixture)
                                  .clickOk();
      ideFrameFixture.requestProjectSync();
    }

    ideFrameFixture.invokeMenuPath("Build", "Rebuild Project").waitForGradleProjectSyncToFinish(Wait.seconds(60));

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.runApp(APP)
                   .selectDevice(emulator.getDefaultAvdName())
                   .clickOk();

    // Check app successfully builds and deploys on emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP)
                   .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 60);
  }

  protected static void newKotlinFileAndClass(@NotNull ProjectViewFixture.PaneFixture projectPane,
                                     @NotNull IdeFrameFixture ideFrameFixture,
                                     @NotNull String projectName,
                                     @NotNull String packageName,
                                     @NotNull String name,
                                     @NotNull String type) {
    Wait.seconds(30).expecting("Path should be found.").until(() -> {
      try {
        projectPane.clickPath(projectName, APP, SRC, MAIN, JAVA, packageName)
                   .invokeMenuPath(MENU_FILE, MENU_NEW, KOTLIN_FILE_CLASS);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    NewKotlinClassDialogFixture.find(ideFrameFixture)
                               .enterName(name)
                               .selectType(type)
                               .clickOk();

    String fileName = name + KOTLIN_EXTENSION;
    Wait.seconds(5).expecting(fileName + " file should be opened")
        .until(() -> fileName.equals(ideFrameFixture.getEditor().getCurrentFileName()));
  }

  protected static void createNewBasicKotlinProject(boolean hasCppSupport, GuiTestRule guiTest) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
                                                      .createNewProject();
    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      newProjectWizard
        .getChooseAndroidProjectStep()
        .chooseActivity(hasCppSupport ? "Native C++" : "Empty Activity")
        .wizard()
        .clickNext()
        .getConfigureNewAndroidProjectStep()
        .enterPackageName("android.com")
        .setSourceLanguage("Kotlin");
    }
    else {
      newProjectWizard.getConfigureAndroidProjectStep()
                      .enterPackageName("android.com")
                      .setCppSupport(hasCppSupport)
                      .setKotlinSupport(true); // Default "App name", "company domain" and "package name"

      newProjectWizard.clickNext();
      newProjectWizard.clickNext(); // Skip "Select minimum SDK Api" step
      newProjectWizard.clickNext(); // Skip "Add Activity" step
    }

    if (hasCppSupport) {
      newProjectWizard.clickNext();
    }

    newProjectWizard.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));

    // Build project after Gradle sync finished.
    guiTest.ideFrame().invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD, Wait.seconds(60));
  }
}
