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
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.InputDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleRenameModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testRenameModule() throws IOException {
    guiTest.importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = guiTest.ideFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(guiTest.robot());
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(guiTest.robot(), "Rename Module");
    renameModuleDialog.enterTextAndClickOk("app2");
    renameModuleDialog.waitUntilNotShowing();

    assertNotNull(guiTest.ideFrame().findModule("app2"));
    assertNull("Module 'app' should not exist", guiTest.ideFrame().findModule("app"));
  }

  @Test
  public void testRenameModuleAlsoChangeReferencesInBuildFile() throws IOException {
    guiTest.importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = guiTest.ideFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "library");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(guiTest.robot());
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(guiTest.robot(), "Rename Module");
    renameModuleDialog.enterTextAndClickOk("newLibrary");

    guiTest.waitForBackgroundTasks();
    assertNotNull(guiTest.ideFrame().findModule("newLibrary"));

    // app module has two references to library module
    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app");

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "debugCompile";
    expected.path = ":newLibrary";
    buildModel.requireDependency(expected);

    expected.configurationName = "releaseCompile";
    buildModel.requireDependency(expected);
  }

  @Test
  public void testCannotRenameRootModule() throws IOException {
    guiTest.importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = guiTest.ideFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication");
    invokeRefactor();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(guiTest.robot(), "Rename Module");
    renameModuleDialog.enterTextAndClickOk("SimpleApplication2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(guiTest.robot(), guiTest.ideFrame().target(), "Rename Module");
    errorMessage.requireMessageContains("Can't rename root module");

    errorMessage.clickOk();
  }

  @Test
  public void testCannotRenameToExistingFile() throws IOException {
    guiTest.importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = guiTest.ideFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(guiTest.robot());
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(guiTest.robot(), "Rename Module");
    renameModuleDialog.enterTextAndClickOk("library2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(guiTest.robot(), guiTest.ideFrame().target(), "Rename Module");
    errorMessage.requireMessageContains("Module named 'library2' already exist");

    errorMessage.clickOk();
    // In this case, the rename dialog will let you choose another name, click cancel to close the dialog
    renameModuleDialog.clickCancel();
  }

  private void invokeRefactor() {
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");
  }
}
