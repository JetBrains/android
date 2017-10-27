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
package com.android.tools.idea.tests.gui.integration;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ConfirmUninstallServiceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith (GuiTestRunner.class)
public class GoogleApiIntegrationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String REG_EXP =
      "(.*'com.google.android.gms:play-services-ads:.*'.*\n" +
      ".*'com.google.android.gms:play-services-auth:.*'.*\n" +
      ".*'com.google.firebase:firebase-messaging:.*'.*)";

  /**
   * To verify that Developer Services dependencies can be added to a module.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 3c3c9998-d715-41ca-a8a2-0ef4156c0325
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project.
   *   2. Open File > Project Structure.
   *   3. Enable all the services. Click OK
   *   4. Open module/build.gradle (Verify 1)
   *   5. Open Project Structure and remove all developer services dependencies, Click OK
   *   6. Check module/build.gradle (Verify 2)
   *   Verification:
   *   1. Dependencies are added for each of the enabled service Eg. For Ads, the line will be
   *      "compile 'com.google.android.gms:play-services-ads:7.8.0'").
   *   2. All dependencies are removed from module/build.gradle
   *   </pre>
   */
  @Test
  @RunIn (TestGroup.QA)
  public void testGoogleApiIntegration() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();

    ProjectStructureDialogFixture projectStructureDialog =
        ideFrame.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...");
    projectStructureDialog.selectAdsDeveloperService()
      .toggleCheckBox();
    projectStructureDialog.selectAuthenticationDeveloperService()
      .toggleCheckBox();
    projectStructureDialog.selectNotificationsDeveloperService()
      .toggleCheckBox();
    projectStructureDialog.clickOk();
    ideFrame.waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor().open("/app/build.gradle");
    String gradleFileContents = editor.getCurrentFileContents();
    assertThat(gradleFileContents).containsMatch(REG_EXP);

    projectStructureDialog =
        ideFrame.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...");
    projectStructureDialog.selectAdsDeveloperService()
      .toggleCheckBox();
    ConfirmUninstallServiceDialogFixture.find(ideFrame).clickYes();
    ideFrame.waitForGradleProjectSyncToFinish();
    projectStructureDialog.selectAuthenticationDeveloperService()
      .toggleCheckBox();
    ConfirmUninstallServiceDialogFixture.find(ideFrame).clickYes();
    ideFrame.waitForGradleProjectSyncToFinish();
    projectStructureDialog.selectNotificationsDeveloperService()
      .toggleCheckBox();
    ConfirmUninstallServiceDialogFixture.find(ideFrame).clickYes();
    projectStructureDialog.clickOk();
    ideFrame.waitForGradleProjectSyncToFinish();

    gradleFileContents = editor.getCurrentFileContents();
    assertThat(gradleFileContents).doesNotContainMatch(REG_EXP);
  }
}
