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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(GuiTestRunner.class)
public final class ComponentTreeTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void multiSelectComponentDoNotJumpToXML() {
    EditorFixture editor = null;
    try {
      editor = myGuiTest.importProjectAndWaitForProjectSyncToFinish("LayoutLocalTest")
        .getEditor()
        .open("app/src/main/res/layout/constraint.xml", Tab.DESIGN);
    }
    catch (IOException e) {
      fail(e.getMessage());
    }

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();

    JTreeFixture tree = layoutEditor.getComponentTree();
    tree.click();
    tree.pressKey(KeyEvent.VK_CONTROL);
    tree.clickRow(0);
    tree.clickRow(1);
    tree.releaseKey(KeyEvent.VK_CONTROL);
    assertTrue(tree.target().getSelectionModel().isRowSelected(0));
    assertTrue(tree.target().getSelectionModel().isRowSelected(1));

    tree.requireVisible();
  }
}