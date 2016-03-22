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
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public class ManifestEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testManifestEditor() throws IOException {
    guiTest.importSimpleApplication();

    IdeFrameFixture projectFrame = guiTest.ideFrame();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    editor.selectEditorTab("Merged Manifest");

    MergedManifestFixture mergedManifestFixture = editor.getMergedManifestEditor();
    JTreeFixture tree = mergedManifestFixture.getTree();
    assertNotNull(tree);
  }
}
