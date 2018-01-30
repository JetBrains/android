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
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.GuiTestFileUtils;
import com.android.tools.idea.tests.util.WizardUtils;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public final class ComponentTreeTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/66912463
  @Test
  public void testDropThatOpensDialog() throws IOException {
    WizardUtils.createNewProject(myGuiTest);
    Path activityMainXmlRelativePath = FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml");

    GuiTestFileUtils.writeAndReloadDocument(
      myGuiTest.getProjectPath().toPath().resolve(activityMainXmlRelativePath),

      "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\">\n" +
      "\n" +
      "</android.support.constraint.ConstraintLayout>\n");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(activityMainXmlRelativePath);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getPalette().dragComponent("Widgets", "ImageView");
    // TODO This step takes around 10 s when this UI test does it (not when I do it manually). Make it faster.
    layoutEditor.getComponentTree().drop("android.support.constraint.ConstraintLayout");

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myGuiTest.robot());
    // TODO Same here
    dialog.expandList("Project").getList("Project").selectItem("ic_launcher");
    dialog.clickOK();

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
}