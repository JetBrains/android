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
package com.android.tools.idea.tests.gui.gradle;

import static com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.AGPUpgradeAssistantToolWindowFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AgpUpgradeJava8ProjTest {

  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private IdeFrameFixture ideFrame;
  private File projectDir;
  private String projectName = "CodeGeneration";
  private String agpVersion = "7.4.1";

  @Before
  public void setUpProject() throws Exception {
    projectDir = guiTest.setUpProject(projectName, null, agpVersion, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame = guiTest.ideFrame();
  }

  /**
   * Verifies Gradle plugin update for Java 8 projects
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: e63e6338-944e-48b7-ac28-c2d9c5d9adab
   * <pre>
   * Test Steps:
   * 1) Download the relevant project based on above notes from Google Drive: go/agp_samples
   * 2) Import project in Studio
   * 3) Wait for the project to gradle sync (Verify 1 or 1.1)
   * 4) Open app:build.gradle file and make sure the "compileOptions" are set to Java8 &gt; Gradle Sync
   * compileOptions {
   *         sourceCompatibility JavaVersion.VERSION_1_8
   *         targetCompatibility JavaVersion.VERSION_1_8
   *     }
   * 5) Goto &gt; Tools &gt; AGP Upgrade Assistant (Verify 2)
   * 6) Select the "Upgrade" checkbox.
   * 7) Click on Run selected steps
   * 8) After update, open Application build.gradle file (Verify 3)
   *
   * Expected Results:
   * 1) There should not be any dialog box/ notifications on Studio asking to update AGP
   * 2) AGP upgrade assistant window should show up with project's AGP version selected in the dropdown
   * 3) AGP version should update to the current version selected in the dropdown
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testGradlePluginUpdateForJava8Project() {
    //Clearing the notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame();

    EditorFixture editor = ideFrame.getEditor();

    String gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertTrue(gradleContents.contains("sourceCompatibility JavaVersion.VERSION_1_8"));
    assertTrue(gradleContents.contains("targetCompatibility JavaVersion.VERSION_1_8"));

    //Testing AGP upgrade using upgrade assistant from latest AGP stable release version to latest agp version (dev, canary, beta, rc)
    AGPUpgradeAssistantToolWindowFixture upgradeAssistant = ideFrame.getUgradeAssistantToolWindow(true);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    upgradeAssistant.selectAGPVersion(ANDROID_GRADLE_PLUGIN_VERSION);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    upgradeAssistant.clickRunSelectedStepsButton();
    ideFrame.waitForGradleSyncToFinish(null);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String gradleContentsAfterUpgrade = editor.open("build.gradle")
      .getCurrentFileContents();
    assertTrue(gradleContentsAfterUpgrade.contains(ANDROID_GRADLE_PLUGIN_VERSION));
  }
}
