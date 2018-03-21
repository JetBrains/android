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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.uibuilder.Assert.assertPathExists;

@RunWith(GuiTestRunner.class)
public final class DragMenuItemsFromPaletteToComponentTreeTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void dragAndDrop() throws IOException {
    myGuiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/menu/menu.xml");

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();

    JListFixture list = layoutEditor.getPalette().getItemList("");
    list.replaceCellReader(new ItemTitleListCellReader());

    JTreeFixture componentTree = layoutEditor.getComponentTree();

    list.drag("Group");
    componentTree.drop("menu");

    list.drag("Menu Item");
    componentTree.drop("menu/group");

    list.drag("Menu");
    componentTree.drop("menu/group/item");

    assertPathExists(componentTree, "menu/group/item/menu");
  }
}
