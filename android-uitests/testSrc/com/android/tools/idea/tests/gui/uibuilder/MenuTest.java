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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.nio.file.FileSystems;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public final class MenuTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void dragMenuItemIntoActionBar() {
    WizardUtils.createNewProject(myGuiTest, "google.com", "Basic Activity");

    IdeFrameFixture frame = myGuiTest.ideFrame();
    EditorFixture editor = frame.getEditor();
    String path = FileSystems.getDefault().getPath("app", "src", "main", "res", "menu", "menu_main.xml").toString();

    editor.open(path);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getPaletteItemList(0).drag("Menu Item");
    layoutEditor.getSurface().drop(new Point(380, 120));

    editor.open(path, Tab.EDITOR);

    editor.invokeAction(EditorAction.SELECT_ALL);
    frame.invokeMenuPath("Code", "Reformat Code");

    @Language("XML")
    String expected = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                      "    tools:context=\"com.google.myapplication.MainActivity\">\n" +
                      "    <item\n" +
                      "        android:orderInCategory=\"100\"\n" +
                      "        android:title=\"Item\" />\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/action_settings\"\n" +
                      "        android:orderInCategory=\"101\"\n" +
                      "        android:title=\"@string/action_settings\"\n" +
                      "        app:showAsAction=\"never\" />\n" +
                      "</menu>\n";

    assertEquals(expected, editor.getCurrentFileContents());
  }
}
