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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.util.WizardUtils;
import org.intellij.lang.annotations.Language;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.FileSystems;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public final class FormattingTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Ignore("See NewProjectModel.ReformattingGradleSyncListener")
  @Test
  public void templateGeneratedLayoutFileHasAndroidXmlFormatting() {
    WizardUtils.createNewProject(myGuiTest, "google.com", "Empty Activity");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml"), Tab.EDITOR);

    @Language("XML")
    String expected = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\"\n" +
                      "    tools:context=\"com.google.myapplication.MainActivity\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"Hello World!\"\n" +
                      "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                      "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                      "        app:layout_constraintRight_toRightOf=\"parent\"\n" +
                      "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
                      "\n" +
                      "</android.support.constraint.ConstraintLayout>\n";

    assertEquals(expected, editor.getCurrentFileContents());
  }
}
