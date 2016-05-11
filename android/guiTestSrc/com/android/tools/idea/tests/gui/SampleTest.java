/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.ScreenshotsDuringTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class SampleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotsDuringTest = new ScreenshotsDuringTest();

  @Test
  public void testEditor() throws IOException {
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);

    assertEquals("strings.xml", editor.getCurrentFileName());

    editor.moveBetween("", "app_name");

    assertEquals("<string name=\"^app_name\">Simple Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.moveBetween("", "Simple Application");
    assertEquals("<string name=\"app_name\">^Simple Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.select("(Simple) Application");
    assertEquals("<string name=\"app_name\">|>^Simple<| Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.enterText("Tester");
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.enterText("d");
    assertEquals("<string name=\"app_name\">Tested^ Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    assertEquals("<string name=\"app_name\">Tester^ Application</string>", editor.getCurrentLineContents(true, true, 0));

    editor.invokeAction(EditorFixture.EditorAction.TOGGLE_COMMENT);
    assertEquals("    <!--<string name=\"app_name\">Tester Application</string>-->\n" +
                 "    <string name=\"hello_world\">Hello w^orld!</string>\n" +
                 "    <string name=\"action_settings\">Settings</string>", editor.getCurrentLineContents(false, true, 1));
    editor.moveBetween(" ", "<string name=\"action");
    editor.enterText("    ");
    editor.invokeAction(EditorFixture.EditorAction.FORMAT);

    // Test IME
    editor.enterImeText("デバッグ");
    assertEquals("    デバッグ^<string name=\"action_settings\">Settings</string>",
                 editor.getCurrentLineContents(false, true, 0));
  }
}
