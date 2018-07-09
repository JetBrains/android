/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture.ConflictsDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

/** Tests the editing flow of refactoring */
@RunWith(GuiTestRemoteRunner.class)
public class RefactoringFlowTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testResourceConflict() throws IOException {
    // Try to rename a resource to an existing resource; check that
    // you get a warning in the conflicts dialog first
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml");
    editor.moveBetween("hello", "_world");
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    // Rename as action_settings, which is already defined
    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("action_settings");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    assertThat(conflictsDialog.getText()).contains("Resource @string/action_settings already exists");
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }
}
