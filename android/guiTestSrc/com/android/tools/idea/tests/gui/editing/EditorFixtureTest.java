/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class EditorFixtureTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testEditorScrolling() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/java/google/simpleapplication/MyActivity.java");

    editor.moveTo(0).enterText("\n").moveTo(0);
    editor.enterText("// firstLine");

    final int numLines = 100;
    for (int i = 0; i < numLines; i++) {
      editor.enterText("\n");
    }

    editor.enterText("// lastLine");

    editor.moveTo(0);
    assertEquals(editor.getCurrentLineNumber(), 1);

    editor.moveToLine(numLines+1);
    assertEquals(editor.getCurrentLineNumber(), numLines+1);

    editor.moveToLine(1);
    assertEquals(editor.getCurrentLineNumber(), 1);
  }
}
