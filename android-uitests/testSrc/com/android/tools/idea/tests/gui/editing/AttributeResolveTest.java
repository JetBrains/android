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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.junit.Assert.assertEquals;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class AttributeResolveTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testResolveNewlyAddedTag() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();

    // TODO add dependency using new parser API
    File appBuildFile = new File(guiTest.ideFrame().getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertAbout(file()).that(appBuildFile).isFile();
    appendToFile(appBuildFile, "\ndependencies { compile 'com.android.support:cardview-v7:22.1.1' }\n");
    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    editor.moveBetween("", "<TextView");
    editor.enterText("<android.support.v7.widget.CardView android:onClick=\"onCreate\" /\n");
    editor.moveBetween("on", "Create");

    guiTest.waitForBackgroundTasks();

    editor.invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    assertEquals("MyActivity.java", editor.getCurrentFileName());
  }
}
