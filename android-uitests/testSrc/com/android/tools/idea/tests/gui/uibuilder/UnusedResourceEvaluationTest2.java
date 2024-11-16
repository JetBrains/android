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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RefactorToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RefactoringDialogFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class UnusedResourceEvaluationTest2 {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();
  private Path myStringsXmlPath;

  protected static final String BASIC_VIEW_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION;

  private IdeFrameFixture ideFrame;


  @Before
  public void setUp() {
    WizardUtils.createNewProject(guiTest, BASIC_VIEW_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Java);
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();

    //Clearing notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * Verifies no resources are removed from NoUnusedResourceApp since all
   * resources are used.
   * P0 ticket: https://b.corp.google.com/issues/230015328
   * <p>TT ID:
   *
   * <pre>
   *   Test steps:
   *   1. Create new project with Basic activity
   *   2. Go to Refactor->Remove Unused Resources
   *   3. Click on Preview button in Remove Unused Resources dialogue
   *   4. Click on Do Refactor buttonImport the UnusedResourceApp
   *
   *   Verify:
   *   1. All layout files should not delete if we do refactor
   * </pre>
   */

  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void keepLavoutFiles() throws Exception {
    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/res/layout/activity_main.xml",
      "app/src/main/res/layout/content_main.xml",
      "app/src/main/res/layout/fragment_first.xml",
      "app/src/main/res/layout/fragment_second.xml"
    );

    //Refactoring multiple times as sometimes refactoring list is coming empty
    doRefactor();

    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/res/layout/activity_main.xml",
      "app/src/main/res/layout/content_main.xml",
      "app/src/main/res/layout/fragment_first.xml",
      "app/src/main/res/layout/fragment_second.xml"
    );
  }

  public void doRefactor() throws InterruptedException {
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.invokeMenuPath("Refactor", "Remove Unused Resources...");

    RefactoringDialogFixture removeUnusedRes = RefactoringDialogFixture.find(ideFrame.robot(), "Remove Unused Resources");
    removeUnusedRes.getPreviewButton()
      .click();
    removeUnusedRes.waitForDialogToDisappear();

    RefactorToolWindowFixture refactoringWindow = new RefactorToolWindowFixture(ideFrame);
    refactoringWindow.clickRefactorButton();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
