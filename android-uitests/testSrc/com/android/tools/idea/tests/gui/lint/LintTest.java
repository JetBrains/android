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
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

@RunWith(GuiTestRunner.class)
public class LintTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

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
  @RunIn(TestGroup.QA)
  @Test
  public void obsoleteSdkIntLintCheck() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LintTest");

    EditorFixture editor = ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/java/com/example/nishanthkumarg/myapplication/MainActivity.java", EditorFixture.Tab.EDITOR)
      .waitUntilErrorAnalysisFinishes();

    Wait.seconds(5)
      .expecting("Unnecessary conditional statements are detected")
      .until(() -> editor.getHighlights(WARNING).contains("Unnecessary; SDK_INT is always >= 21"));
  }
}
