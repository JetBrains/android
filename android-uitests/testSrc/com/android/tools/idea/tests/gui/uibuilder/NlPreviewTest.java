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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForBackgroundTasks;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static org.junit.Assert.*;

/**
 * UI test for the layout preview window
 */
@RunWith(GuiTestRunner.class)
public class NlPreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/63923598
  @Test
  public void testConfigurationMatching() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    NlPreviewFixture preview = editor.getLayoutPreview(true);
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().chooseDevice("Nexus 5");
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().requireDevice("Nexus 5");
    VirtualFile file = editor.getCurrentFile();
    assert file != null;
    assertThat(file.getParent().getName()).isEqualTo("layout");
    preview.getConfigToolbar().requireOrientation("Portrait");

    preview.getConfigToolbar().chooseDevice("Nexus 7");
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().requireDevice("Nexus 7 2013");
    assertThat(editor.getCurrentFile().getParent().getName()).isEqualTo("layout-sw600dp");

    preview.getConfigToolbar().chooseDevice("Nexus 10");
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().requireDevice("Nexus 10");
    assertThat(editor.getCurrentFile().getParent().getName()).isEqualTo("layout-sw600dp");
    preview.getConfigToolbar().requireOrientation("Landscape"); // Default orientation for Nexus 10

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().requireDevice("Nexus 10"); // Since we switched to it most recently
    preview.getConfigToolbar().requireOrientation("Portrait");

    preview.getConfigToolbar().chooseDevice("Nexus 7");
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().chooseDevice("Nexus 4");
    preview.waitForRenderToFinish();
    editor.open("app/src/main/res/layout-sw600dp/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    assertThat(editor.getCurrentFile().getParent().getName()).isEqualTo("layout-sw600dp");
    preview.getConfigToolbar().requireDevice("Nexus 7 2013"); // because it's the most recently configured sw600-dp compatible device
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    preview.getConfigToolbar().requireDevice("Nexus 4"); // because it's the most recently configured small screen compatible device
  }

  @Test
  public void editCustomView() throws Exception {
    // Opens the LayoutTest project, opens a layout with a custom view, checks
    // that it can't render yet (because the project hasn't been built),
    // builds the project, checks that the render works, edits the custom view
    // source code, ensures that the render lists the custom view as out of date,
    // applies the suggested fix to build the project, and finally asserts that the
    // build is now successful.

    EditorFixture editor = guiTest
      .importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    NlPreviewFixture preview = editor.getLayoutPreview(false);
    preview.waitForRenderToFinish();

    assertTrue(preview.hasRenderErrors());
    preview.waitForErrorPanelToContain("The following classes could not be found");
    preview.waitForErrorPanelToContain("com.android.tools.tests.layout.MyButton");
    preview.waitForErrorPanelToContain("Change to android.widget.Button");

    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Build completion should trigger re-render
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors());

    editor
      .open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR)
      .moveBetween("extends Button {", "")
      .enterText(" // test") // Next let's edit the custom view source file
      .open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR); // Switch back; should trigger render

    preview.waitForRenderToFinish();
    preview.waitForErrorPanelToContain("The MyButton custom view has been edited more recently than the last build");
    result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors());

    // Now make some changes to the file which updates the modification timestamp of the source. However,
    // also edit them back and save again (which still leaves a new modification timestamp). Gradle will
    // *not* rebuild if the file contents have not changed (it uses checksums rather than file timestamps).
    // Make sure that we don't get render errors in this scenario! (Regression test for http://b.android.com/76676)
    editor
      .open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR)
      .moveBetween("extends Button {", "")
      .enterText(" ")
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .invokeAction(EditorFixture.EditorAction.SAVE);
    waitForBackgroundTasks(guiTest.robot());
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();
    preview.waitForErrorPanelToContain("The MyButton custom view has been edited more recently than the last build");

    // this build won't do anything this time, since Gradle notices checksum has not changed
    result = guiTest.ideFrame().invokeProjectMake();

    assertTrue(result.isBuildSuccessful());
    preview.waitForRenderToFinish();
    assertFalse(preview.hasRenderErrors()); // but our build timestamp check this time will mask the out of date warning
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/63923598
  @Test
  public void testRenderingDynamicResources() throws Exception {
    // Opens a layout which contains dynamic resources (defined only in build.gradle)
    // and checks that the values have been resolved correctly (both that there are no
    // unresolved reference errors in the XML file, and that the rendered layout strings
    // matches the expected overlay semantics); also edits these in the Gradle file and
    // checks that the layout rendering is updated after a Gradle sync.

    String layoutFilePath = "app/src/main/res/layout/dynamic_layout.xml";
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest").getEditor();
    NlPreviewFixture preview = editor.open(layoutFilePath, EditorFixture.Tab.EDITOR)
      .getLayoutPreview(true)
      .waitForRenderToFinish();

    assertFalse(preview.hasRenderErrors());

    NlComponentFixture string1 = preview.findView("TextView", 0);
    assertThat(string1.getTextAttribute()).isEqualTo("@string/dynamic_string1");
    assertThat(string1.getViewObject().getClass().getName()).isEqualTo("android.support.v7.widget.AppCompatTextView");
    assertThat(string1.getText()).isEqualTo("String 1 defined only by defaultConfig");

    NlComponentFixture string2 = preview.findView("TextView", 1);
    assertThat(string2.getTextAttribute()).isEqualTo("@string/dynamic_string2");
    assertThat(string2.getText()).isEqualTo("String 1 defined only by defaultConfig");

    NlComponentFixture string3 = preview.findView("TextView", 2);
    assertThat(string3.getTextAttribute()).isEqualTo("@string/dynamic_string3");
    assertThat(string3.getText()).isEqualTo("String 3 defined by build type debug");

    NlComponentFixture string4 = preview.findView("TextView", 3);
    assertThat(string4.getTextAttribute()).isEqualTo("@string/dynamic_string4");
    assertThat(string4.getText()).isEqualTo("String 4 defined by flavor free");

    NlComponentFixture string5 = preview.findView("TextView", 4);
    assertThat(string5.getTextAttribute()).isEqualTo("@string/dynamic_string5");
    assertThat(string5.getText()).isEqualTo("String 5 defined by build type debug");

    // Ensure that all the references are properly resolved
    FileFixture file = guiTest.ideFrame().findExistingFileByRelativePath(layoutFilePath);
    file.waitForCodeAnalysisHighlightCount(ERROR, 0);

    editor.open("app/build.gradle", EditorFixture.Tab.EDITOR)
      .moveBetween("String 1 defined only by ", "defaultConfig")
      .enterText("edited ")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish();

    editor.open(layoutFilePath, EditorFixture.Tab.EDITOR);
    preview.waitForRenderToFinish();

    string1 = preview.findView("TextView", 0);
    assertThat(string1.getText()).isEqualTo("String 1 defined only by edited defaultConfig");

    file.waitForCodeAnalysisHighlightCount(ERROR, 0);
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/63923598
  @Test
  public void testCopyAndPaste() throws Exception {
    guiTest.importSimpleLocalApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);

    NlPreviewFixture layout = editor.getLayoutPreview(true);
    layout
      .dragComponentToSurface("Buttons", "Button")
      .dragComponentToSurface("Buttons", "CheckBox")
      .waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture checkBox = layout.findView("CheckBox", 0);
    checkBox.click();

    // It should be selected now
    assertThat(layout.getSelection()).containsExactly(checkBox.getComponent());
    assertEquals(4, layout.getAllComponents().size()); // 4 = root layout + 3 widgets

    ideFrame.invokeMenuPath("Edit", "Cut");
    assertEquals(3, layout.getAllComponents().size());

    layout.findView("Button", 0).click();
    ideFrame.invokeMenuPath("Edit", "Paste");
    layout.findView("CheckBox", 0).click();
    ideFrame.invokeMenuPath("Edit", "Copy");
    ideFrame.invokeMenuPath("Edit", "Paste");
    assertEquals(5, layout.getAllComponents().size());
  }

  @Test
  public void testPreviewingDrawable() throws Exception {
    // Regression test for http://b.android.com/221330
    guiTest.importSimpleLocalApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutPreview(true)
      .waitForRenderToFinish()
      .showOnlyBlueprintView()
      .waitForScreenMode(SceneMode.BLUEPRINT_ONLY);
    guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/res/drawable/vector.xml", EditorFixture.Tab.EDITOR)
      .getLayoutPreview(true)
      .waitForRenderToFinish()
      .waitForScreenMode(SceneMode.SCREEN_ONLY);
    guiTest.ideFrame()
      .getEditor()
      .switchToTab("activity_my.xml")
      .getLayoutPreview(false)
      .waitForRenderToFinish()
      .waitForScreenMode(SceneMode.BLUEPRINT_ONLY);
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/63923598
  @Test
  public void testNavigation() throws Exception {
    // Open 2 different layout files in a horizontal split view (both editors visible).
    // Open the preview for one of the files.
    // Navigate in the preview. Only 1 of the layouts should change its scroll position (regression test for b/62367302).
    // Navigate in the file. The preview selection should update.

    EditorFixture editor = guiTest.importSimpleLocalApplication().getEditor();
    editor
      .open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.EDITOR)
      .invokeAction(EditorFixture.EditorAction.SPLIT_HORIZONTALLY)
      .open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.EDITOR);

    TextEditorFixture absolute = editor.getVisibleTextEditor("absolute.xml");
    TextEditorFixture frames = editor.getVisibleTextEditor("frames.xml");

    // Navigation in absolute.xml
    navigateEditor(editor, absolute, 323, 1706, frames, 0);

    // Navigation in frames.xml
    frames.select();
    frames.focusAndWaitForFocusGain();
    navigateEditor(editor, frames, 345, 1572, absolute, 1706);
  }

  @Test
  public void deletePreviewedFile() throws Exception {
    guiTest.importSimpleLocalApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutPreview(true)
      .waitForRenderToFinish();
    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout", "activity_my.xml")
      .invokeMenuPath("Delete...");
    DeleteDialogFixture.find(guiTest.robot(), "Delete")
      .safe(false)
      .clickOk()
      .waitUntilNotShowing();
  }

  @Test
  public void closeLayoutShouldNotClosePreviewForAnotherLayout() throws Exception {
    // Regression test for b/64288544
    EditorFixture editor = guiTest.importSimpleLocalApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.EDITOR);
    editor
      .getLayoutPreview(true)
      .waitForRenderToFinish();
    int updateCountBeforeClose = editor.getPreviewUpdateCount();
    editor.closeFile("app/src/main/res/layout/frames.xml");

    Wait.seconds(2).expecting("preview to update")
      .until(() -> editor.getPreviewUpdateCount() > updateCountBeforeClose);
    assertTrue(editor.isPreviewShowing("activity_my.xml"));
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/63923598
  @Test
  public void closeSplitLayoutShouldMovePreviewToCorrectFile() throws Exception {
    // Regression test for b/64199946
    EditorFixture editor = guiTest.importSimpleLocalApplication()
      .getEditor()
      .open("app/src/main/res/drawable/ic_launcher.xml", EditorFixture.Tab.EDITOR)
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .invokeAction(EditorFixture.EditorAction.SPLIT_HORIZONTALLY);
    editor.getLayoutPreview(true);

    TextEditorFixture launcher = editor.getVisibleTextEditor("ic_launcher.xml");
    TextEditorFixture activity = editor.getVisibleTextEditor("activity_my.xml");

    launcher.select();
    activity.select();

    int updateCountBeforeClose = editor.getPreviewUpdateCount();
    activity.closeFile();
    Wait.seconds(2).expecting("preview to update")
      .until(() -> editor.getPreviewUpdateCount() > updateCountBeforeClose);
    assertTrue(editor.isPreviewShowing("ic_launcher.xml"));
  }

  private static void navigateEditor(@NotNull EditorFixture editor,
                                     @NotNull TextEditorFixture selectedEditor, int firstOffset, int lastOffset,
                                     @NotNull TextEditorFixture otherEditor, int expectedOffset) {
    NlPreviewFixture layout = editor.getLayoutPreview(true);
    layout.waitForRenderToFinish();
    layout.waitForRenderToFinishAndApplyComponentDimensions();

    List<NlComponentFixture> components = layout.getAllComponents();
    NlComponentFixture first = components.get(1);                     // First child of the top level Layout
    NlComponentFixture last = components.get(components.size() - 1);  // Last child of the top level layout
    assertThat(first).isNotEqualTo(last);

    // Double click on the first component should move the caret in the selected editor, and leave the other editor unchanged
    first.doubleClick();
    Wait.seconds(1).expecting("editor to be at offset " + firstOffset + " was " + selectedEditor.getOffset())
      .until(() -> selectedEditor.getOffset() == firstOffset);
    assertThat(otherEditor.getOffset()).isEqualTo(expectedOffset);

    // Double click on the last component should move the caret in the selected editor, and leave the other editor unchanged
    last.doubleClick();
    Wait.seconds(1).expecting("editor to be at offset " + lastOffset + " was " + selectedEditor.getOffset())
      .until(() -> selectedEditor.getOffset() == lastOffset);
    assertThat(otherEditor.getOffset()).isEqualTo(expectedOffset);

    // Double click on the first component should move the caret in the selected editor, and leave the other editor unchanged
    first.doubleClick();
    Wait.seconds(1).expecting("editor to be at offset " + firstOffset + " was " + selectedEditor.getOffset())
      .until(() -> selectedEditor.getOffset() == firstOffset);
    assertThat(otherEditor.getOffset()).isEqualTo(expectedOffset);

    // Double click on the last component should move the caret in the selected editor, and leave the other editor unchanged
    last.doubleClick();
    Wait.seconds(1).expecting("editor to be at offset " + lastOffset + " was " + selectedEditor.getOffset())
      .until(() -> selectedEditor.getOffset() == lastOffset);
    assertThat(otherEditor.getOffset()).isEqualTo(expectedOffset);

    // Move the caret to the first component in the selected editor should change the selection in the preview.
    selectedEditor.setOffset(firstOffset);
    assertThat(layout.getSelection()).containsExactly(first.getComponent());

    // Move the caret to the last component in the selected editor should change the selection in the preview.
    selectedEditor.setOffset(lastOffset);
    assertThat(layout.getSelection()).containsExactly(last.getComponent());
  }
}
