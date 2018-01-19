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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewKotlinClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class ProjectWithKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String CLASS_NAME = "KotlinClass";
  private static final String KOTLIN_EXTENSION = ".kt";
  private static final String FILE_NAME = "KotlinFile";
  private static final String PROJECT_DIR_NAME = "LinkProjectWithKotlin";
  private static final String APP = "app";
  private static final String SRC = "src";
  private static final String MAIN = "main";
  private static final String JAVA = "java";
  private static final String PACKAGE_NAME = "com.android.linkprojectwithkotlin";
  private static final String MENU_FILE = "File";
  private static final String MENU_NEW = "New";
  private static final String KOTLIN_FILE_CLASS = "Kotlin File/Class";
  private static final String INTERFACE_NAME = "KotlinInterface";
  private static final String ENUM_NAME = "KotlinEnum";
  private static final String OBJECT_NAME = "KotlinObject";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final String KOTLIN_SUPPORT_PROJECT_DIR_NAME = "KotlinSupportProject";
  private static final String KOTLIN_SUPPORT_PACKAGE_NAME = "com.android.kotlinsupportproject";

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 30f26a59-108e-49cc-bec0-586f518ea3cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import LinkProjectWithKotlin project, which doesn't support Kotlin
   *      and wait for project sync to finish.
   *   2. Select Project view and expand directory to Java package and click on it.
   *   3. From menu, click on "File->New->Kotlin File/Class".
   *   4. In "New Kotlin File/Class" dialog, enter the name of class
   *      and choose "Class" from the dropdown list in Kind category, and click on OK.
   *   5. Click on the configure pop up on the top right corner or bottom right corner.
   *   6. Select all modules containing Kotlin files option from "Configure kotlin pop up".
   *   7. Continue this with File,interface,enum class and verify 1 & 2
   *   Verify:
   *   1. Observe the code in Kotlin language.
   *   2. Build and deploy on the emulator.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/72164080
  public void linkNoKotlinSupportProjectWithKotlin() throws Exception {
    createKotlinFileAndClassAndVerify(PROJECT_DIR_NAME, PACKAGE_NAME, false);
  }

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 64230371-979d-4a17-86f4-7aa1213b93f6
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinSupportProject project, which does support Kotlin
   *      and wait for project sync to finish.
   *   2. Select Project view and expand directory to Java package and click on it.
   *   3. From menu, click on "File->New->Kotlin File/Class".
   *   4. In "New Kotlin File/Class" dialog, enter the name of class
   *      and choose "Class" from the dropdown list in Kind category, and click on OK.
   *   5. Continue this with File,interface,enum class and verify 1 & 2
   *   Verify:
   *   1. Observe the code in Kotlin language.
   *   2. Build and deploy on the emulator.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void createKotlinFileAndClassInKotlinSupportProject() throws Exception {
    createKotlinFileAndClassAndVerify(KOTLIN_SUPPORT_PROJECT_DIR_NAME, KOTLIN_SUPPORT_PACKAGE_NAME, true);
  }

  private void createKotlinFileAndClassAndVerify(@NotNull String projectDirName,
                                                 @NotNull String packageName,
                                                 boolean withKotlinSupport) throws Exception {
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

    // b/67846310 Plugin Error: NoReadAccessException. Test will fail during tearing down.
  }

  private void newKotlinFileAndClass(@NotNull ProjectViewFixture.PaneFixture projectPane,
                                     @NotNull IdeFrameFixture ideFrameFixture,
                                     @NotNull String projectName,
                                     @NotNull String packageName,
                                     @NotNull String name,
                                     @NotNull String type) {
    projectPane.clickPath(projectName, APP, SRC, MAIN, JAVA, packageName)
      .invokeMenuPath(MENU_FILE, MENU_NEW, KOTLIN_FILE_CLASS);

    NewKotlinClassDialogFixture.find(ideFrameFixture)
      .enterName(name)
      .selectType(type)
      .clickOk();

    String fileName = name + KOTLIN_EXTENSION;
    Wait.seconds(5).expecting(fileName + " file should be opened")
      .until(() -> fileName.equals(ideFrameFixture.getEditor().getCurrentFileName()));
  }
}
