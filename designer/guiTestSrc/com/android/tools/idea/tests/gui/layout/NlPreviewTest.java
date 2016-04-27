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

import com.android.repository.Revision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForBackgroundTasks;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

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

  @Test
  public void testRenderingDynamicResources() throws Exception {
    // Opens a layout which contains dynamic resources (defined only in build.gradle)
    // and checks that the values have been resolved correctly (both that there are no
    // unresolved reference errors in the XML file, and that the rendered layout strings
    // matches the expected overlay semantics); also edits these in the Gradle file and
    // checks that the layout rendering is updated after a Gradle sync.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    AndroidGradleModel androidModel = guiTest.ideFrame().getAndroidModel("app");
    String modelVersion = androidModel.getAndroidProject().getModelVersion();
    assertNotNull(modelVersion);
    Revision version = Revision.parseRevision(modelVersion);
    assertNotNull("Could not parse version " + modelVersion, version);
    assumeTrue("This test tests behavior that starts working in 0.14.+", version.getMajor() != 0 || version.getMinor() >= 14);

    EditorFixture editor = guiTest.ideFrame().getEditor();
    String layoutFilePath = "app/src/main/res/layout/dynamic_layout.xml";
    editor.open(layoutFilePath, EditorFixture.Tab.EDITOR);
    NlPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForRenderToFinish();

    assertFalse(preview.hasRenderErrors());

    NlComponentFixture string1 = preview.findView("TextView", 0);
    string1.requireAttribute(ANDROID_URI, ATTR_TEXT, "@string/dynamic_string1");
    string1.requireViewClass("android.widget.TextView");
    string1.requireActualText("String 1 defined only by defaultConfig");

    NlComponentFixture string2 = preview.findView("TextView", 1);
    string2.requireAttribute(ANDROID_URI, ATTR_TEXT, "@string/dynamic_string2");
    string2.requireActualText("String 1 defined only by defaultConfig");

    NlComponentFixture string3 = preview.findView("TextView", 2);
    string3.requireAttribute(ANDROID_URI, ATTR_TEXT, "@string/dynamic_string3");
    string3.requireActualText("String 3 defined by build type debug");

    NlComponentFixture string4 = preview.findView("TextView", 3);
    string4.requireAttribute(ANDROID_URI, ATTR_TEXT, "@string/dynamic_string4");
    string4.requireActualText("String 4 defined by flavor free");

    NlComponentFixture string5 = preview.findView("TextView", 4);
    string5.requireAttribute(ANDROID_URI, ATTR_TEXT, "@string/dynamic_string5");
    string5.requireActualText("String 5 defined by build type debug");

    // Ensure that all the references are properly resolved
    FileFixture file = guiTest.ideFrame().findExistingFileByRelativePath(layoutFilePath);
    file.waitForCodeAnalysisHighlightCount(ERROR, 0);

    String buildGradlePath = "app/build.gradle";
    editor.open(buildGradlePath, EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("String 1 defined only by |defaultConfig"));
    editor.enterText("edited ");
    guiTest.ideFrame().requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    editor.open(layoutFilePath, EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();

    string1 = preview.findView("TextView", 0);
    string1.requireActualText("String 1 defined only by edited defaultConfig");

    file.waitForCodeAnalysisHighlightCount(ERROR, 0);
  }
}
