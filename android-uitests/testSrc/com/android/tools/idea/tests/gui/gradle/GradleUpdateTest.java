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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class GradleUpdateTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies automatic update of gradle version
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 8a0ddb63-18e2-4c9f-82f6-1e2fa7676bba
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. After a sync, a dialog forcing to this one: https://screenshot.googleplex.com/uTcvGRzLzpQ should appear
   *   3. Click update
   *   4. The IDE will automatically update the Gradle plugin version in build.gradle files, and trigger a sync
   *   Verify:
   *   Open build.gradle files and make sure that the version of the plugin was updated to <current gradle="" version="">
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/73645716
  @Test
  public void updateGradleTestProject() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    // The UI test framework will update gradle properties after import.
    // To fullfill this test, we have to restore those old gradle propreties first.
    EditorFixture editor = ideFrameFixture.getEditor()
      .open("build.gradle")
      .select("gradle:(.+)['\"]")
      .enterText("2.2.0")
      .select("(google\\(\\))")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .select("(google\\(\\))")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .open("gradle/wrapper/gradle-wrapper.properties")
      .moveBetween("", "distributionUrl");

    // The file URL may be so long that it does not fit on screen. This slightly less
    // obvious method for selecting the line to replace it should be able to handle such long lines.
    String distLine = editor.getCurrentLine();
    distLine = distLine.replaceAll("(?<=gradle-).+(?=\\.zip)", "2.14.1-bin");
    editor.selectCurrentLine()
      .enterText(distLine);

    ideFrameFixture.requestProjectSync();

    // Project sync can take a very long time
    GuiTests.findAndClickButtonWhenEnabled(
      ideFrameFixture.waitForDialog("Android Gradle Plugin Update Recommended", 120),
      "Update");
    guiTest.waitForBackgroundTasks();
    String contents = ideFrameFixture.getEditor()
      .open("build.gradle")
      .getCurrentFileContents();
    assertThat(contents).doesNotContain("gradle:2.2.0");
  }
}
