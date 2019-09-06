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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.fest.swing.fixture.JTreeFixture;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public final class ComponentTreeTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();
  @Rule
  public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/140560022
  @Test
  public void testDropThatOpensDialog() {
    WizardUtils.createNewProject(myGuiTest);
    Path activityMainXmlRelativePath = FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml");

    EditorFixture editor = myGuiTest.ideFrame().getEditor()
      // When we create a project using the wizard, files are open with the default editor before sync. After sync, close file in case it
      // before opening, to cover the case of HIDE_DEFAULT_EDITOR policy.
      .closeFile(activityMainXmlRelativePath.toString())
      .open(activityMainXmlRelativePath)
      .replaceText(
        "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "</android.support.constraint.ConstraintLayout>\n")
      .selectEditorTab(Tab.DESIGN);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getPalette().dragComponent("Widgets", "ImageView");
    // TODO This step takes around 10 s when this UI test does it (not when I do it manually). Make it faster.
    layoutEditor.getComponentTree().drop("android.support.constraint.ConstraintLayout");

    ResourceExplorerDialogFixture dialog = ResourceExplorerDialogFixture.find(myGuiTest.robot());
    // TODO Same here
    dialog.getResourceExplorer().selectTab("Mip Map");
    dialog.getResourceExplorer().selectResource("ic_launcher");
    dialog.clickOk();

    editor.open(activityMainXmlRelativePath, Tab.EDITOR);

    @Language("XML")
    String expected = "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "\n" +
                      "    <ImageView\n" +
                      "        android:id=\"@+id/imageView\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        app:srcCompat=\"@mipmap/ic_launcher\" />\n" +
                      "</android.support.constraint.ConstraintLayout>\n";

    assertEquals(expected, editor.getCurrentFileContents());
  }

  @Test
  public void multiSelectComponentDoNotJumpToXML() {
    EditorFixture editor = null;
    try {
      editor = myGuiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
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

  @Test
  public void dragDropFromTreeToSurfaceDoNotDelete() {
    EditorFixture editor = null;
    try {
      editor = myGuiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
                        .getEditor()
                        .open("app/src/main/res/layout/constraint.xml", Tab.DESIGN);
    }
    catch (IOException e) {
      fail(e.getMessage());
    }

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();

    JTreeFixture tree = layoutEditor.getComponentTree();
    DesignSurfaceFixture surface = layoutEditor.getSurface();
    assertEquals("Initial components count unexpected", 2, layoutEditor.getAllComponents().size());
    Point rootCenter = ((NlComponentFixture)surface.getAllComponents().get(0)).getMidPoint();
    Point childLocation = ((NlComponentFixture)surface.getAllComponents().get(1)).getMidPoint();
    tree.drag(1);
    surface.drop(rootCenter);
    layoutEditor.waitForRenderToFinish();
    assertEquals("Components count after drag unexpected", 2, layoutEditor.getAllComponents().size());
    assertNotEquals(childLocation, ((NlComponentFixture)surface.getAllComponents().get(1)).getMidPoint());
  }
}