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
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.common.editor.NlEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

@RunWith(GuiTestRunner.class)
public final class ScrollingActivityTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Ignore("b/66680171")
  @Test
  public void contentScrollingXmlOpensInLayoutEditor() {
    WizardUtils.createNewProject(myGuiTest, "Scrolling Activity");

    Path path = FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "content_scrolling.xml");
    myGuiTest.ideFrame().getEditor().open(path);

    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(myGuiTest.getProjectPath().toPath().resolve(path).toFile());

    assertTrue(FileEditorManager.getInstance(myGuiTest.ideFrame().getProject()).getSelectedEditor(file) instanceof NlEditor);
  }
}
