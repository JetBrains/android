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
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceDirectoryDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.FileSystems;

@RunWith(GuiTestRunner.class)
public final class CreateResourceDirectoryDialogTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void enterOrSelectQualifier() {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml"));

    NlEditorFixture layoutEditor = editor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getConfigToolbar().chooseLayoutVariant("Create Other...");

    CreateResourceDirectoryDialogFixture dialog = layoutEditor.getSelectResourceDirectoryDialog();
    dialog.clickOk();
    dialog.waitUntilErrorLabelFound("Enter or select a qualifier");
    dialog.clickCancel();
  }
}
