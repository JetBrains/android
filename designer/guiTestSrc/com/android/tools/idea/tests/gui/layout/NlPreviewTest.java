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

import static org.junit.Assert.assertNotNull;

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
    NlPreviewFixture preview = NlPreviewFixture.getNlPreview(editor, ideFrame, true);
    assertNotNull(preview);
    NlConfigurationToolbarFixture toolbar = preview.getToolbar();
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
}
