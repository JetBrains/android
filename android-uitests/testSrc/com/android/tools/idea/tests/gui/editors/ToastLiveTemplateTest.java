/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editors;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class ToastLiveTemplateTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static String TOAST = ".*Toast\\.makeText\\(this, \"\",.*Toast\\.LENGTH_SHORT\\)\\.show\\(\\);" + LiveTemplatesTestUtil.STATEMENT + ".*";
  private static final Pattern TOAST_PATTERN = Pattern.compile(TOAST, Pattern.DOTALL);

  /**
   * Verifies that surround with shortcut can be used to wrap block of code in some structure, like if-else or for each.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: a809b947-2dae-475b-9a43-40422344a0b1
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project.
   *   2. In java class file, place the cursor at the beginning of any line, e.g. "setContentView(R.layout.activity_my);".
   *   3. Click on Code > Insert Live Template...
   *   4. Enter the keyboard shortcut for any of the available options, say Toast and press enter (Verify 3).
   *   Expectations:
   *   1. The template for a new toast is added to the class file.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAT_BAZEL)
  public void insertToastViaKeyboardShortcut() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120));
    EditorFixture editorFixture = ideFrame.getEditor().open(LiveTemplatesTestUtil.JAVA_FILE);

    LiveTemplatesTestUtil.clickOnCodeInsertLiveTemplate(guiTest, ideFrame, editorFixture);
    guiTest.robot().enterText("Toast\n");
    Wait.seconds(20).expecting("Toast statement to show in code.")
      .until(() -> new PatternTextMatcher(TOAST_PATTERN).isMatching(editorFixture.getCurrentFileContents()));
  }
}
