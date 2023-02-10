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
package com.android.tools.idea.tests.gui.lint;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

@RunWith(GuiTestRemoteRunner.class)
public class LintTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame;

  @Before
  public void setUp() throws Exception {

    File projectDir = guiTest.setUpProject("LintTest", null, ANDROID_GRADLE_PLUGIN_VERSION, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir, Wait.seconds(540));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame = guiTest.ideFrame();
  }

  /**
   * Verifies that obsolete SDK_INT checks in conditional statements
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 58ac4d63-21bd-4e0a-b057-c03a8cb66429
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open LintTest project
   *   2. Open MainActivity class
   *   </pre>
   *   Verify:
   *   Verify that unnecessary conditional statements are detected by Lint.
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void obsoleteSdkIntLintCheck() throws Exception {
    EditorFixture editor = ideFrame
      .getEditor()
      .open("app/src/main/java/com/example/nishanthkumarg/myapplication/MainActivity.java", EditorFixture.Tab.EDITOR)
      .waitUntilErrorAnalysisFinishes();

    Wait.seconds(5)
      .expecting("Unnecessary conditional statements are detected")
      .until(() -> editor.getHighlights(WARNING).contains("Unnecessary; SDK_INT is always >= 21"));
  }
}
