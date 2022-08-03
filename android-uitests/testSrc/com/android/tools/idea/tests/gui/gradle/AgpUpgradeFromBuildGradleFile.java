/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AgpUpgradeFromBuildGradleFile {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(8, TimeUnit.MINUTES);

  /**
   * Verifies automatic update of gradle version
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b320c139-38f1-4384-bed3-2ea198b140c9
   *   <pre>
   *   Test Steps:
   *   1.Import Simple project
   *   2.Open module level build.gradle file
   *   3.Update the gradle version in build.gradle file. (Verify 1)
   *   4.Invoke gradle sync
   *   Verify:
   *   1.AGP classpath updated in build.gradle file
   *   2.Gradle sync successful.
   *
   *   </pre>
   */

  @RunIn(com.android.tools.idea.tests.gui.framework.TestGroup.SANITY_BAZEL)
  @Test
  public void testAgpUpgradeAssistant() throws Exception {

    File projectDir = guiTest.setUpProject("SimpleApplication", null, "7.1.0", null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    editor.open("build.gradle")
      .select("gradle\\:(\\d+\\.\\d+\\.\\d+)")
      .enterText("7.2.0");

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(editor.open("build.gradle").getCurrentFileContents()).contains("gradle:7.2.0");
    assertThat(editor.open("build.gradle").getCurrentFileContents()).doesNotContain("gradle:7.1.0");
  }
}