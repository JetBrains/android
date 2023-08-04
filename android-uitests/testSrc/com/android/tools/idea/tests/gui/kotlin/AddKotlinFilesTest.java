/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddKotlinFilesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private String PACKAGE_NAME = "com.google.myapplication";
  private String PROJECT_DIR_NAME = "MyApplication";

  /**
   * Create Kotlin file, interface and class
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 30f26a59-108e-49cc-bec0-586f518ea3cb
   * <p>
   * <pre>
   *   Test Steps:
   *    1. Configure a new project, Select Empty Activity and Kotlin from Language Dropdown.
   *    2. Right click on a Java package and invoke New - choose Kotlin Class/File
   *    3. Enter the name of the Class and choose class from the dropdown
   *    4. Continue this with File, interface, enum class. (Verify 1 & 2)
   *   Verify:
   *    1. Observe the code in Kotlin language
   *    2. Sync and Build the project
   *   </pre>
   * <p>
   */
  @Test
  public void addKotlinClass() throws Exception {
    WizardUtils.createNewProject(guiTest,
                                 "Empty Views Activity",
                                 Language.Kotlin,
                                 BuildConfigurationLanguageForNewProject.KTS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    ProjectViewFixture.PaneFixture projectPane = ideFrameFixture
      .getProjectView()
      .selectProjectPane();

    // Create Kotlin Class
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.CLASS_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_CLASS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Create Kotlin File
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.FILE_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_FILE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Create Kotlin Interface
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.INTERFACE_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_INTERFACE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Create Kotlin Enum Class
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.ENUM_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_ENUMCLASS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Create Kotlin object
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.OBJECT_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_OBJECT);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Sync and build the project.
    ideFrameFixture.requestProjectSyncAndWaitForSyncToFinish();
    assertThat(ideFrameFixture.invokeProjectMake(Wait.seconds(300)).isBuildSuccessful())
      .isTrue();
  }
}
