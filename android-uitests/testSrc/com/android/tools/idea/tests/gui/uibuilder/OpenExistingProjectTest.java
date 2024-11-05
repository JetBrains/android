/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectionsFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class OpenExistingProjectTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "TestApp";
  protected static final String PACKAGE_NAME = "com.google.testapp";
  protected static final int MIN_SDK_API = SdkVersionInfo.HIGHEST_SUPPORTED_API;

  /**
   * Verifies that existing projects can be opened and build without errors.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f6a85b6d-cc94-4ff3-93b1-f8c7fb10e946
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project
   *   2. Close it
   *   3. Open the one just closed from the left
   *      (which is the most recently closed one), Verify 1
   *   4. Inspect code, and verify 2
   *   5. Build project, verify 3
   *   Verify:
   *   1. The Java file is opened
   *   2. No errors in Java code
   *   3. No errors
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testOpenExistingProject() throws InterruptedException {
    //Create a new project
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    //Clearing any notifications present on the frame
    ideFrame.clearNotificationsPresentOnIdeFrame();

    //Closing  and opening the new project.
    ideFrame = ideFrame.closeProject()
      .openTheMostRecentProject(guiTest);

    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    EditorFixture editorFixture = ideFrame.getEditor();
    editorFixture.waitForFileToActivate(90);

    Thread.sleep(10_000); // TODO(b/332920584): temporary workaround for IJPL-149431 (DumbService deadlock).

    InspectCodeDialogFixture inspectCodeDialog = ideFrame.invokeInspectCodeDialog();
    inspectCodeDialog.clickAnalyze();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    List<String> errors = editorFixture.getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).hasSize(0);

    //Rebuilding the project.x
    BuildStatus rebuildStatus = ideFrame.invokeRebuildProject(Wait.seconds(300));
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(rebuildStatus.isBuildSuccessful()).isTrue();
  }
}
