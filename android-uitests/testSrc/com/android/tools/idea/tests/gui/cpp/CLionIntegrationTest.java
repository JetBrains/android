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
package com.android.tools.idea.tests.gui.cpp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class CLionIntegrationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String NATIVE_C_FILE_PATH = "app/src/main/cpp/hello-jni.c";
  private static final String NATIVE_C_HEADER_FILE = "hello-jni.h";

  /**
   * Verifies that Gradle projects can contain C/C++ source code with full CLion editor support.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 555f9f6f-0ada-4d5b-8fef-a0c2b6f4129d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import CLionNdkHelloJni and wait for sync to finish.
   *   2. From menu, click on "Analyze->Inspect Code..." and wait for inspection results and verify1
   *   3. Open app/src/main/cpp/hello-jni.c file form editor, and enter "BUFFER" then 'Ctrl-space",
   *      and verify2
   *   4. Select "BUFFER_OFFSET" and invoke GOTO_DECLARATION, and  verify3
   *   5. Re-Open app/src/main/cpp/hello-jni.c, and enter an undefined method call after
   *      "BUFFER_OFFSET(kid_age);", and verify4
   *   Vefify:
   *   1. In inspection results: 1) it contains Uunused header #includes;
   *                             2) it doesn't contain any error
   *   2. Code completion should work: "BUFFER_OFFSET" is auto entered
   *   3. hello-jni.h file is opened and mouse cursor is in "BUFFER_OFFSET" line
   *   4. There should be an error indication.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void cLionIntegration() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/CLionNdkHelloJni");

    guiTest.waitForBackgroundTasks();

    // Check unused header import and no errors.
    String inspectionResults = ideFrame.openFromMenu(InspectCodeDialogFixture::find, "Code", "Inspect Code...")
      .clickButton("Analyze")
      .getResults();
    assertThat(inspectionResults).contains("Unused");

    EditorFixture editor = ideFrame.getEditor().open(NATIVE_C_FILE_PATH, EditorFixture.Tab.EDITOR);
    assertThat(editor.getHighlights(HighlightSeverity.ERROR)).isEmpty();

    // Check code completion.
    editor.moveBetween("int kid_age = 3;", "").typeText("\nBUFFER");
    Wait.seconds(10).expecting("Completion to show up").until(() -> editor.getAutoCompleteWindow().contents().length > 0);
    editor.invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);
    Wait.seconds(20).expecting("")
      .until(() -> editor.getCurrentLine().contains("BUFFER_OFFSET"));

    // Complete the new statement.
    editor.moveBetween("BUFFER_OFFSET(", "")
      .typeText("kid_age")
      .invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);

    // Check declaration.
    editor.moveBetween("BUFFER_", "OFFSET")
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);
    Wait.seconds(5).expecting("Native header file is opened for navigating to definition")
      .until(() -> NATIVE_C_HEADER_FILE.equals(ideFrame.getEditor().getCurrentFileName()));
    String currentLine = ideFrame.getEditor().getCurrentLine();
    assertThat(currentLine).isEqualTo("#define BUFFER_OFFSET(i) ((char*)NULL + (i))\n");

    // Check errors.
    List<String> errors = ideFrame.getEditor().open(NATIVE_C_FILE_PATH, EditorFixture.Tab.EDITOR)
      .moveBetween("BUFFER_OFFSET(kid_age);", "")
      .typeText("\nNOT_DEFINED_FUNC();")
      .getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("Implicit declaration of function 'NOT_DEFINED_FUNC' is invalid");

    // TODO: Syntax highlighting check.
  }
}
