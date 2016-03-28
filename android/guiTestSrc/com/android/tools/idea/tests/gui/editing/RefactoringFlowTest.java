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
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture.ConflictsDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.regex.Pattern;

/** Tests the editing flow of refactoring */
@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class RefactoringFlowTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String VALUE_REGEX =
    "(appcompat-v7/\\d+\\.\\d+\\.\\d+/res/values-\\p{Lower}\\p{Lower}(-r\\p{Upper}\\p{Upper})?/values.xml\\n)+";

  @Test
  public void testResourceConflict() throws IOException {
    // Try to rename a resource to an existing resource; check that
    // you get a warning in the conflicts dialog first
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml");
    editor.moveTo(editor.findOffset("hello^_world"));
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    // Rename as action_settings, which is already defined
    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("action_settings");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    conflictsDialog.requireMessageTextContains("Resource @string/action_settings already exists");
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }

  @Test()
  public void testWarnOverridingExternal() throws Exception {
    // Try to override a resource that is only defined in an external
    // library; check that we get an error message. Then try to override
    // a resource that is both overridden locally and externally, and
    // check that we get a warning dialog. And finally try to override
    // a resource that is only defined locally and make sure there is
    // no dialog.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/override.xml");
    // <string name="abc_searchview_description_submit">@string/abc_searchview_description_voice</string>
    editor.moveTo(editor.findOffset("abc_searchview_^description_voice")); // only defined in appcompat
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("a");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    conflictsDialog.requireMessageTextMatches(
      Pattern.quote("Resource is also only defined in external libraries and\n" +
                    "cannot be renamed.\n" +
                    "\n" +
                    "Unhandled references:\n") +
      VALUE_REGEX +
      Pattern.quote("...\n" +
                    "(Additional results truncated)"));
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();

    // Now try to rename @string/abc_searchview_description_submit which is defined in *both* appcompat and locally
    editor.moveTo(editor.findOffset("abc_searchview_^description_submit")); // only defined in appcompat
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("a");
    refactoringDialog.clickRefactor();

    conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    conflictsDialog.requireMessageTextMatches(
      Pattern.quote("The resource @string/abc_searchview_description_submit is\n" +
                    "defined outside of the project (in one of the libraries) and\n" +
                    "cannot be updated. This can change the behavior of the\n" +
                    "application.\n" +
                    "\n" +
                    "Are you sure you want to do this?\n" +
                    "\n" +
                    "Unhandled references:\n") +
      VALUE_REGEX +
      Pattern.quote("...\n" +
                    "(Additional results truncated)"));
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }
}
