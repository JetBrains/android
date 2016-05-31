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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class SampleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotsDuringTest = new ScreenshotsDuringTest();

  @Ignore("go/studio-builder/builders/ubuntu-studio-master-dev-uitests/builds/290")
  @Test
  public void testEditor() throws IOException {
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);

    assertEquals("strings.xml", editor.getCurrentFileName());

    editor.select("(Simple) Application");
    editor.enterText("Tester");
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.enterText("d");
    assertThat(editor.getCurrentLine()).contains("Tested Application");
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    assertThat(editor.getCurrentLine()).contains("Tester Application");

    editor.invokeAction(EditorFixture.EditorAction.TOGGLE_COMMENT);  // also moves caret to next line
    editor.moveBetween("Tester", " Application");
    assertThat(editor.getCurrentLine().trim()).matches("^<!--.*-->$");
    editor.moveBetween(" ", "<string name=\"action");
    editor.enterText("    ");
    editor.invokeAction(EditorFixture.EditorAction.FORMAT);

    // Test IME
    editor.enterImeText("デバッグ");
    assertThat(editor.getCurrentLine()).contains("デバッグ");
  }
}
