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
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
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
    assertEquals("^android:allowBackup=\"true\"", editor.getCurrentLineContents(true, true, 0));
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
                                      "library manifest", false);
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    editor.moveBetween("<application", "");
    editor.enterText(" android:isGame=\"true\"");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);

    tree.clickPath("manifest/application/android:isGame = true");
    assertEquals("android:isGame = true", tree.valueAt(tree.target().getLeadSelectionRow()));
  }

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
    assertEquals(defaultBackgroundColor, mergedManifestFixture.getSelectedNodeColor());
    mergedManifestFixture.getTree().clickRow(2);

    mergedManifestFixture.requireText(
      "Manifest Sources \n" +
      "\n" +
      "main manifest (this file)\n" +
      "\n" +
      "play-services-base:7.3.0 manifest\n" +
      " Other Manifest Files (Included in merge, but did not contribute any elements)\n" +
      "debug manifest, flavor1 manifest, support-v13:22.1.1 manifest, support-v4:22.1.1\n" +
      "manifest, play-services-ads:7.3.0 manifest, play-services-analytics:7.3.0\n" +
      "manifest, play-services-appindexing:7.3.0 manifest,\n" +
      "play-services-appinvite:7.3.0 manifest, play-services-appstate:7.3.0 manifest,\n" +
      "play-services-cast:7.3.0 manifest, play-services-drive:7.3.0 manifest,\n" +
      "play-services-fitness:7.3.0 manifest, play-services-games:7.3.0 manifest,\n" +
      "play-services-gcm:7.3.0 manifest, play-services-identity:7.3.0 manifest,\n" +
      "play-services-location:7.3.0 manifest, play-services-maps:7.3.0 manifest,\n" +
      "play-services-nearby:7.3.0 manifest, play-services-panorama:7.3.0 manifest,\n" +
      "play-services-plus:7.3.0 manifest, play-services-safetynet:7.3.0 manifest,\n" +
      "play-services-wallet:7.3.0 manifest, play-services-wearable:7.3.0 manifest,\n" +
      "play-services:7.3.0 manifest  Merging Log Value provided by Gradle Added from\n" +
      "the main manifest (this file), line 1 Value provided by Gradle\n", true);


    editor.open("src/debug/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(1);
    assertNotEquals(defaultBackgroundColor, mergedManifestFixture.getSelectedNodeColor());

    editor.open("src/flavor1/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(1);
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
    popup.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Remove".equals(component.getText());
      }
    }).click();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    assertThat(editor.getCurrentFileContents()).contains(addedText);
  }
}
