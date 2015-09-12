/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.parser.ModuleDependencyElementTest.ExpectedProjectDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleRenameModuleTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testRenameModule() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = projectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication", "app");
    invokeRefactor(projectFrame);

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("app2");

    projectFrame.waitForBackgroundTasksToFinish();
    assertNotNull(projectFrame.findModule("app2"));
    assertNull("Module 'app' should not exist", projectFrame.findModule("app"));
  }

  @Test
  @IdeGuiTest
  public void testRenameModuleAlsoChangeReferencesInBuildFile() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");

    ProjectViewFixture.PaneFixture paneFixture = projectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "library");
    invokeRefactor(projectFrame);

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("newLibrary");

    projectFrame.waitForBackgroundTasksToFinish();
    assertNotNull(projectFrame.findModule("newLibrary"));

    // app module has two references to library module
    GradleBuildModelFixture buildModel = projectFrame.openAndParseBuildFileForModule("app");

    ExpectedProjectDependency expected = new ExpectedProjectDependency();
    expected.configurationName = "debugCompile";
    expected.path = ":newLibrary";
    buildModel.requireDependency(expected);

    expected.configurationName = "releaseCompile";
    buildModel.requireDependency(expected);
  }

  @Test
  @IdeGuiTest
  public void testCannotRenameRootModule() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = projectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication");
    invokeRefactor(projectFrame);

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("SimpleApplication2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, projectFrame.target(), "Rename Module");
    errorMessage.requireMessageContains("Can't rename root module");
  }

  @Test
  @IdeGuiTest
  public void testCannotRenameToExistedFile() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");

    ProjectViewFixture.PaneFixture paneFixture = projectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "app");
    invokeRefactor(projectFrame);

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("library2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, projectFrame.target(), "Rename Module");
    errorMessage.requireMessageContains("Rename folder failed");
  }

  private static void invokeRefactor(@NotNull IdeFrameFixture projectFrame) {
    projectFrame.invokeMenuPath("Refactor", "Rename...");
  }

}
