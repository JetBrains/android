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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public final class GridLayoutTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void dragViewIntoEmptyGridLayout() throws Exception {
    IdeFrameFixture frame = myGuiTest.importSimpleApplication();
    frame.getProjectView().selectAndroidPane().clickPath("app");
    frame.openFromMenu(CreateResourceFileDialogFixture::find, "File", "New", "Android Resource File")
      .setFilename("gridlayout")
      .setType("layout")
      .setRootElement("GridLayout")
      .clickOk()
      .getEditor()
      .getLayoutEditor(false)
      .waitForRenderToFinish()
      .showOnlyDesignView()
      .dragComponentToSurface("Text", "TextView");

    @Language("XML")
    String expected = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "        android:id=\"@+id/textView\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:layout_row=\"0\"\n" +
                      "        android:layout_column=\"0\"\n" +
                      "        android:text=\"TextView\" />\n" +
                      "</GridLayout>";

    String contents = frame.getEditor().open("app/src/main/res/layout/gridlayout.xml", Tab.EDITOR).getCurrentFileContents();
    assertThat(contents).isEqualTo(expected);
  }
}
