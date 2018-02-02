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
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;

@RunWith(GuiTestRunner.class)
public final class NavigationViewTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private EditorFixture myEditor;
  private NlEditorFixture myLayoutEditor;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(myGuiTest, "Navigation Drawer Activity");

    myEditor = myGuiTest.ideFrame().getEditor();
    myEditor.open(FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml"));

    myLayoutEditor = myEditor.getLayoutEditor(false);

    myLayoutEditor.waitForRenderToFinish();
    myLayoutEditor.showOnlyDesignView();
  }

  @Ignore("b/72574190")  // triggers IDE error
  @Test
  public void doubleClickHeaderLayout() {
    myLayoutEditor.getSurface().doubleClick(new Point(230, 170));
    waitUntilEditorCurrentFileEquals(FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "nav_header_main.xml"));
  }

  @Ignore("b/72574190")  // triggers IDE error
  @Test
  public void doubleClickMenu() {
    myLayoutEditor.getSurface().doubleClick(new Point(150, 410));
    waitUntilEditorCurrentFileEquals(FileSystems.getDefault().getPath("app", "src", "main", "res", "menu", "activity_main_drawer.xml"));
  }

  private void waitUntilEditorCurrentFileEquals(@NotNull Path path) {
    Object file = LocalFileSystem.getInstance().findFileByIoFile(myGuiTest.getProjectPath().toPath().resolve(path).toFile());
    Wait.seconds(2).expecting("editor current file to equal " + file).until(() -> Objects.equals(myEditor.getCurrentFile(), file));
  }
}
