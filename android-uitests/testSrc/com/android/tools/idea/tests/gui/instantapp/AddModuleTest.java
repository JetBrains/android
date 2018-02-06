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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;

@RunWith(GuiTestRunner.class)
public class AddModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies that user is able to add a feature module through the
   * new module wizard.
   *
   * <p>TT ID: d239df75-a7fc-4327-a5af-d6b2f6caba11
   *
   * <pre>
   *   Test steps:
   *   1. Import simple instant application project
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new feature module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new feature module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/64950482
  public void addFeatureModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleInstantApp");

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleDialogFixture newModDialog = NewModuleDialogFixture.find(ideFrame);

    newModDialog.chooseModuleType("Feature Module")
      .clickNextToStep("Creates a new Android module.")
      .clickNextToStep("Add an Activity to Mobile")
      .clickNextToStep("Configure Activity")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ProjectViewFixture.PaneFixture androidPane = ideFrame.getProjectView().selectAndroidPane();
    androidPane.clickPath("mylibrary");
  }

  /**
   * Verifies that gradle sync works fine with library having shrinkResources enabled on debug and release build variants
   *
   * <p>TT ID: 2604a622-87f1-47dc-bc2d-55c62859b3a5
   *
   * <pre>
   *   Test steps:
   *   1. Create a Project with any Activity
   *   2. Create a Android Library module
   *   3. Right click on app module | Module settings | Dependencies | Add Library module as dependency to app
   *   4. Open mylibrary | build.gradle
   *   5. Add "shrinkResources true" to release variant
   *   6. Gradle sync (Verify)
   *   Verify:
   *   Error should show up that shrinker is not allowed for library modules
   *   "Error:Resource shrinker cannot be used for libraries."
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY)
  public void generateApkWithReleaseVariant() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleDialogFixture.find(ideFrame)
      .chooseModuleType("Android Library")
      .clickNextToStep("Configure the new module")
      .clickFinish();
    ideFrame.waitForGradleProjectSyncToFinish();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON,"app")
      .openFromMenu(ProjectStructureDialogFixture::find, "Open Module Settings")
      .selectDependenciesTab()
      .addModuleDependency(":mylibrary")
      .clickOk();

    ideFrame.getEditor()
      .open("mylibrary/build.gradle")
      .moveBetween("release {", "")
      .enterText("\nshrinkResources true");
    ideFrame.requestProjectSync();

    ideFrame.waitForGradleProjectSyncToFail(Wait.seconds(20));

    ideFrame.getMessagesToolWindow()
      .getGradleSyncContent()
      .findMessage(ERROR, firstLineStartingWith("Resource shrinker cannot be used for libraries"));
  }

  /**
   * Verifies that user is able to add a instant app module through the
   * new module wizard.
   *
   * <p>TT ID: 6da70326-4b89-4f9b-9e08-573939bebfe5
   *
   * <pre>
   *   Test steps:
   *   1. Import simple application project
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new instant module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new instant module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void addInstantModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProject("SimpleLocalApplication");
    ideFrame.waitForGradleProjectSyncToFinish(Wait.seconds(60));

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleDialogFixture.find(ideFrame)
      .chooseModuleType("Instant App")
      .clickNextToStep("Configure your new module")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("instantapp");
  }
}
