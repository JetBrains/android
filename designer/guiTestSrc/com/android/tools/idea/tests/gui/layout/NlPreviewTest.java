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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlPreviewFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForBackgroundTasks;
import static org.junit.Assert.*;

/**
 * UI test for the layout preview window
 */
@RunIn(TestGroup.LAYOUT)
@RunWith(GuiTestRunner.class)
public class NlPreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConfigurationMatching() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    NlPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    NlConfigurationToolbarFixture toolbar = preview.getConfigToolbar();
    toolbar.chooseDevice("Nexus 5");
    preview.waitForRenderToFinish();
    toolbar.requireDevice("Nexus 5");
    editor.requireFolderName("layout");
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 7");
    preview.waitForRenderToFinish();
    toolbar.requireDevice("Nexus 7 2013");
    editor.requireFolderName("layout-sw600dp");

    toolbar.chooseDevice("Nexus 10");
    preview.waitForRenderToFinish();
    toolbar.requireDevice("Nexus 10");
    editor.requireFolderName("layout-sw600dp");
    toolbar.requireOrientation("Landscape"); // Default orientation for Nexus 10

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    toolbar.requireDevice("Nexus 10"); // Since we switched to it most recently
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 7");
    preview.waitForRenderToFinish();
    toolbar.chooseDevice("Nexus 4");
    preview.waitForRenderToFinish();
    editor.open("app/src/main/res/layout-sw600dp/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    editor.requireFolderName("layout-sw600dp");
    toolbar.requireDevice("Nexus 7 2013"); // because it's the most recently configured sw600-dp compatible device
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    toolbar.requireDevice("Nexus 4"); // because it's the most recently configured small screen compatible device
  }

  @Test
  public void testEditCustomView() throws Exception {
    // Opens the LayoutTest project, opens a layout with a custom view, checks
    // that it can't render yet (because the project hasn't been built),
    // builds the project, checks that the render works, edits the custom view
    // source code, ensures that the render lists the custom view as out of date,
    // applies the suggested fix to build the project, and finally asserts that the
    // build is now successful.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    NlPreviewFixture preview = editor.getLayoutPreview(false);
    assertNotNull(preview);
    preview.waitForRenderToFinish();

    assertTrue(preview.hasRenderErrors());
    assertTrue(preview.errorPanelContains("The following classes could not be found"));
    assertTrue(preview.errorPanelContains("com.android.tools.tests.layout.MyButton"));
    assertTrue(preview.errorPanelContains("Change to android.widget.Button"));

    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Build completion should trigger re-render
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors());

    // Next let's edit the custom view source file
    editor.open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("extends Button {", null, true));
    editor.enterText(" // test");

    // Switch back; should trigger render:
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    assertTrue(preview.errorPanelContains("The MyButton custom view has been edited more recently than the last build"));
    preview.performSuggestion("Build");
    waitForBackgroundTasks(guiTest.robot());
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors());

    // Now make some changes to the file which updates the modification timestamp of the source. However,
    // also edit them back and save again (which still leaves a new modification timestamp). Gradle will
    // *not* rebuild if the file contents have not changed (it uses checksums rather than file timestamps).
    // Make sure that we don't get render errors in this scenario! (Regression test for http://b.android.com/76676)
    editor.open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("extends Button {", null, true));
    editor.enterText(" ");
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    waitForBackgroundTasks(guiTest.robot());
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    assertTrue(preview.errorPanelContains("The MyButton custom view has been edited more recently than the last build"));
    preview.performSuggestion("Build"); // this build won't do anything this time, since Gradle notices checksum has not changed
    waitForBackgroundTasks(guiTest.robot());
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors()); // but our build timestamp check this time will mask the out of date warning
  }
}
