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
import com.android.tools.idea.tests.util.GuiTestFileUtils;
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
public final class GridLayoutTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void dragViewIntoEmptyGridLayout() throws IOException {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");
    Path activityMainXml = FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml");

    @Language("XML")
    String xml = "<GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "</GridLayout>\n";

    GuiTestFileUtils.writeAndReloadDocument(myGuiTest.getProjectPath().toPath().resolve(activityMainXml), xml);

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(activityMainXml);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.showOnlyDesignView();
    layoutEditor.dragComponentToSurface("Text", "TextView");

    @Language("XML")
    String expected = "<GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "        android:id=\"@+id/textView\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"TextView\" />\n" +
                      "</GridLayout>\n";

    editor.open(activityMainXml, Tab.EDITOR);
    assertEquals(expected, editor.getCurrentFileContents());
  }
}
