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

import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public class ManifestEditorTest {

  private static final Color CURRENT_MANIFEST_COLOR = new Color(1f, 0f, 0f, 0.2f);

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
    assertEquals(CURRENT_MANIFEST_COLOR, mergedManifestFixture.getSelectedNodeColor());
    mergedManifestFixture.clickLinkAtOffset(15);
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
    mergedManifestFixture.getInfoPane().requireText("<html>\n" +
                                                    "  <head>\n" +
                                                    "    \n" +
                                                    "  </head>\n" +
                                                    "  <body>\n" +
                                                    "  </body>\n" +
                                                    "</html>\n");
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("<application", null, true));
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
    mergedManifestFixture.getTree().clickRow(1);
    assertEquals(CURRENT_MANIFEST_COLOR, mergedManifestFixture.getSelectedNodeColor());

    editor.open("src/debug/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(1);
    assertNotEquals(CURRENT_MANIFEST_COLOR, mergedManifestFixture.getSelectedNodeColor());

    editor.open("src/flavor1/AndroidManifest.xml");
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    mergedManifestFixture = editor.getMergedManifestEditor();
    mergedManifestFixture.getTree().clickRow(1);
    assertNotEquals(CURRENT_MANIFEST_COLOR, mergedManifestFixture.getSelectedNodeColor());
  }

  @Test
  public void testRemoveFromManifest() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    String text = editor.getCurrentFileContents(false);
    assertNotNull(text);
    String addedText = "<activity\n" +
                       "            android:name=\"com.android.mylibrary.MainActivity\"\n" +
                       "            tools:node=\"remove\" />";
    assertFalse(text.contains(addedText));
    editor.selectEditorTab(EditorFixture.Tab.MERGED_MANIFEST);
    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();
    JTreeFixture tree = mergedManifestFixture.getTree();
    // row 28 = "manifest/application/activity/android:name = com.android.mylibrary.MainActivity"
    JPopupMenuFixture popup = tree.showPopupMenuAt(28);
    popup.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Remove".equals(component.getText());
      }
    }).click();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    text = editor.getCurrentFileContents(false);
    assertNotNull(text);
    assertTrue(text.contains(addedText));
  }
}
