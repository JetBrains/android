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
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MergedManifestFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public class ManifestEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testManifestGoToSource() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();
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
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();
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

    tree.clickPath("manifest/application/android:isGame = true");
    assertEquals("android:isGame = true", tree.valueAt(tree.target().getLeadSelectionRow()));
  }

  @Ignore("fails with Gradle plugin 2.3.0-dev")
  @Test
  public void testNonPrimaryManifest() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("Flavoredapp");
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();

    editor.open("src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();

    Color defaultBackgroundColor = mergedManifestFixture.getDefaultBackgroundColor();
    mergedManifestFixture.getTree().clickRow(1);
    assertThat(mergedManifestFixture.getSelectedNodeColor()).isNotEqualTo(defaultBackgroundColor);
    mergedManifestFixture.getTree().clickRow(3);
    assertThat(mergedManifestFixture.getSelectedNodeColor()).isEqualTo(defaultBackgroundColor);
    mergedManifestFixture.getTree().clickRow(2);

    mergedManifestFixture.requireText(
      "Manifest Sources \n" +
      "\n" +
      "Flavoredapp main manifest (this file)\n" +
      "\n" +
      "myaarlibrary manifest\n" +
      "\n" +
      "build.gradle injection\n" +
      " Other Manifest Files (Included in merge, but did not contribute any elements)\n" +
      "locallib manifest, Flavoredapp debug manifest, Flavoredapp flavor1 manifest,\n" +
      "support-compat:25.0.0 manifest, support-core-ui:25.0.0 manifest,\n" +
      "support-core-utils:25.0.0 manifest, support-fragment:25.0.0 manifest,\n" +
      "support-media-compat:25.0.0 manifest, support-v4:25.0.0 manifest  Merging Log\n" +
      "Value provided by Gradle Added from the Flavoredapp main manifest (this file),\n" +
      "line 1 Value provided by Gradle\n", true);

    editor.open("src/debug/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(3);
    assertNotEquals(defaultBackgroundColor, mergedManifestFixture.getSelectedNodeColor());

    editor.open("src/flavor1/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(3);
    assertNotEquals(defaultBackgroundColor, mergedManifestFixture.getSelectedNodeColor());
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
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();
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
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();

    // row 22 is the first row of the intent-filter element generated from the nav-graph element
    mergedManifestFixture.getTree().clickRow(22);

    mergedManifestFixture.clickLinkText("main mobile_navigation.xml navigation file");
    assertThat(editor.getCurrentFileName()).isEqualTo("mobile_navigation.xml");

    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);

    // row 23 is a sub-element of the intent-filter element generated from the nav-graph element
    mergedManifestFixture.getTree().clickRow(23);

    mergedManifestFixture.clickLinkText("main mobile_navigation.xml navigation file");
    assertThat(editor.getCurrentFileName()).isEqualTo("mobile_navigation.xml");
  }
}
