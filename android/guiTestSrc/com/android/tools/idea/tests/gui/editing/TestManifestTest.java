/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.intellij.lang.annotation.HighlightSeverity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Checks that a manifest in the test source set can access an activity defined in the same source set.
 */
@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class TestManifestTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testManifest() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ProjectWithUnitTests");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    guiTest.waitForBackgroundTasks(); // Needed to make sure nothing will interfere with the typing later

    editor.open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR);
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
    editor.select("(\\.MainActivity)");
    // TestActivity shouldn't be accessible from the main AndroidManifest.xml
    editor.enterText(".TestActivity");
    // Close the auto-completion window
    editor.invokeAction(EditorFixture.EditorAction.ESCAPE);
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);

    // but it should be from the test manifest
    editor.open("app/src/test/AndroidManifest.xml");
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
    editor.select("(\\.TestActivity)");
    editor.enterText(".MainActivity2");
    // Make sure we trigger the highlighting error
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);
    // Remove the number "2". That should bring the errors back to 0
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }
}
