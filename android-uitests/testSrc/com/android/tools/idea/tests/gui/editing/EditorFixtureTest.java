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
package com.android.tools.idea.tests.gui.editing;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceFilesUtil;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.google.common.base.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class EditorFixtureTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void editLine() throws Exception {
    String line = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR)
      .select("(Simple) Application")
      .enterText("Tester")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .enterText("d")
      .invokeAction(EditorFixture.EditorAction.UNDO)
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .getCurrentLine();
    assertThat(line).contains("Test Application");
  }

  @Test
  public void moveBetween_scrollsWhenNeeded() throws Exception {
    int lineNumber = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 100))
      .moveBetween("", "")  // before the first character on the first line
      .getCurrentLineNumber();
    assertThat(lineNumber).isEqualTo(1);
  }

  @Test
  public void select_scrollsWhenNeeded() throws Exception {
    int lineNumber = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 99))
      .moveBetween("", "")  // before the first character on the first line
      .select("class (MyActivity) ")
      .getCurrentLineNumber();
    assertThat(lineNumber).isGreaterThan(100);
  }

  @Test
  public void open_selectsEditorTab() throws Exception {
    VirtualFile file = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getCurrentFile();
    assertThat(ResourceFilesUtil.getFolderType(file)).isEqualTo(ResourceFolderType.LAYOUT);
  }
}
