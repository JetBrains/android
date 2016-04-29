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

import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyTest.ExpectedModuleDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.InputDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleRenameModuleTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testRenameModule() throws IOException {
    myProjectFrame = importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = myProjectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("app2");

    myProjectFrame.waitForBackgroundTasksToFinish();
    assertNotNull(myProjectFrame.findModule("app2"));
    assertNull("Module 'app' should not exist", myProjectFrame.findModule("app"));
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 but passed from IDEA")
  @Test @IdeGuiTest
  public void testRenameModuleAlsoChangeReferencesInBuildFile() throws IOException {
    myProjectFrame = importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = myProjectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "library");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("newLibrary");

    myProjectFrame.waitForBackgroundTasksToFinish();
    assertNotNull(myProjectFrame.findModule("newLibrary"));

    // app module has two references to library module
    GradleBuildModelFixture buildModel = myProjectFrame.parseBuildFileForModule("app", true);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "debugCompile";
    expected.path = ":newLibrary";
    buildModel.requireDependency(expected);

    expected.configurationName = "releaseCompile";
    buildModel.requireDependency(expected);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testCannotRenameRootModule() throws IOException {
    myProjectFrame = importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = myProjectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication");
    invokeRefactor();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("SimpleApplication2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, myProjectFrame.target(), "Rename Module");
    errorMessage.requireMessageContains("Can't rename root module");
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testCannotRenameToExistedFile() throws IOException {
    myProjectFrame = importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = myProjectFrame.getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("library2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, myProjectFrame.target(), "Rename Module");
    errorMessage.requireMessageContains("Rename folder failed");
  }

  private void invokeRefactor() {
    myProjectFrame.invokeMenuPath("Refactor", "Rename...");
  }
}
