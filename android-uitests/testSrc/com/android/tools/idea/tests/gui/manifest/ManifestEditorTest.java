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
package com.android.tools.idea.tests.gui.manifest;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MergedManifestFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiTask.execute;
import static org.junit.Assert.*;

@RunWith(GuiTestRemoteRunner.class)
public class ManifestEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/123580267
  @Test
  public void testManifestGoToSource() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor()
      .waitForLoadingToFinish();
    JTreeFixture tree = mergedManifestFixture.getTree();
    tree.clickPath("manifest/application/android:allowBackup = true");
    mergedManifestFixture.checkAllRowsColored();
    Color defaultBackgroundColor = mergedManifestFixture.getDefaultBackgroundColor();
    assertEquals(defaultBackgroundColor, mergedManifestFixture.getSelectedNodeColor());
    mergedManifestFixture.clickLinkText("app main manifest (this file), line 5");
    assertThat(editor.getCurrentLine().trim()).isEqualTo("android:allowBackup=\"true\"");
  }

  @Test
  public void testEditManifest() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor()
      .waitForLoadingToFinish();
    JTreeFixture tree = mergedManifestFixture.getTree();
    mergedManifestFixture.checkAllRowsColored();
    mergedManifestFixture.requireText("Manifest Sources \n" +
                                      "\n" +
                                      "app main manifest (this file)\n" +
                                      "\n" +
                                      "library manifest\n" +
                                      "\n" +
                                      "build.gradle injection", false);
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    editor.moveBetween("<application", "");
    editor.enterText(" android:isGame=\"true\"");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture
      .waitForLoadingToFinish();

    tree.clickPath("manifest/application/android:isGame = true");
    execute(() ->  assertEquals("android:isGame = true", tree.valueAt(tree.target().getLeadSelectionRow())));
  }

  @Test
  public void testRemoveFromManifest() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    String addedText = "        <activity\n" +
                       "            android:name=\"com.android.mylibrary.MainActivity\"\n" +
                       "            tools:remove=\"android:label\" />\n";
    assertThat(editor.getCurrentFileContents()).doesNotContain(addedText);
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor()
      .waitForLoadingToFinish();
    JTreeFixture tree = mergedManifestFixture.getTree();
    // row 28 = "manifest/application/activity/android:name = com.android.mylibrary.MainActivity"
    JPopupMenuFixture popup = tree.showPopupMenuAt(22);
    popup.menuItemWithPath("Remove").click();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    assertThat(editor.getCurrentFileContents()).contains(addedText);
  }

  @Test
  public void testNavigationSource() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("Navigation");
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();

    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor()
      .waitForLoadingToFinish();

    // row 22 is the first row of the intent-filter element generated from the nav-graph element
    mergedManifestFixture.getTree().clickRow(22);

    mergedManifestFixture.clickLinkText("main mobile_navigation.xml navigation file");
    assertThat(editor.getCurrentFileName()).isEqualTo("mobile_navigation.xml");
  }
}
