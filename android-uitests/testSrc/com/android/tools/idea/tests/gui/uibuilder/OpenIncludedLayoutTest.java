/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * UI test for the layout preview window
 */
@RunWith(GuiTestRunner.class)
public class OpenIncludedLayoutTest {

  public static final String INCLUDED_XML = "inner.xml";
  public static final String OUTER_XML = "app/src/main/res/layout/outer.xml";
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/73905271
  @Test
  public void testOpenIncludedLayoutFromComponentTree() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open(OUTER_XML, EditorFixture.Tab.DESIGN);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish();
    JTreeFixture tree = layoutEditor.getComponentTree();
    tree.click();
    tree.requireFocused();
    tree.doubleClickPath("LinearLayout/include");
    assertEquals(INCLUDED_XML, editor.getCurrentFileName());

    layoutEditor.getBackNavigationPanel().click();
    layoutEditor.waitForRenderToFinish();
    assertEquals("outer.xml", editor.getCurrentFileName());
  }

  @Test
  public void testOpenIncludedLayoutFromEditor() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open(OUTER_XML, EditorFixture.Tab.DESIGN);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getAllComponents()
      .stream()
      .filter(fixture -> fixture.getComponent().getTagName().equalsIgnoreCase(SdkConstants.VIEW_INCLUDE))
      .findFirst().get().doubleClick();
    assertEquals(INCLUDED_XML, editor.getCurrentFileName());
  }
}
