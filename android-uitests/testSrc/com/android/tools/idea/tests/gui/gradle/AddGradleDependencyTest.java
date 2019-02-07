/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

@RunWith(GuiTestRemoteRunner.class)
public class AddGradleDependencyTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotsRule = new ScreenshotsDuringTest();

  @Test
  public void testNoModuleDependencyQuickfixFromJavaToAndroid() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("library3/src/main/java/com/example/MyLibrary.java");
    String classToImport = "com.android.multimodule.MainActivity";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);
    editor.waitForQuickfix();

    assertIntentionNotIncluded(editor, "Add dependency on module");
  }

  @Test
  public void testNoModuleDependencyQuickfixFromAndroidLibToApplication() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("library/src/main/java/com/android/library/MainActivity.java");
    String classToImport = "com.android.multimodule.MainActivity";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 2);
    moveCaretToClassName(editor, classToImport);
    editor.waitForQuickfix();

    assertIntentionNotIncluded(editor, "Add dependency on module");
  }

  private void assertIntentionNotIncluded(@NotNull EditorFixture editor, @NotNull String intention) {
    editor.invokeAction(SHOW_INTENTION_ACTIONS);
    Robot robot = guiTest.robot();
    JListFixture popup = new JListFixture(robot, waitForPopup(robot));
    String[] intentions = popup.contents();
    assertThat(intentions).asList().doesNotContain(intention);
  }

  private static void addImport(@NotNull EditorFixture editor, @NotNull String classFqn) {
    String importStatement = createImportStatement(classFqn);
    editor.moveBetween("", "package ");
    editor.invokeAction(EditorFixture.EditorAction.DOWN);
    editor.invokeAction(EditorFixture.EditorAction.DOWN);
    // Having the new line at the end, will trigger a code completion if the import line is later selected,
    // otherwise we would need a delay of 300ish ms to be safe.
    editor.enterText(importStatement + "\n");
  }

  private static void moveCaretToClassName(@NotNull EditorFixture editor, @NotNull String classFqn) {
    String importStatement = createImportStatement(classFqn);
    int statementLength = importStatement.length();
    int position = statementLength - 4;
    editor.moveBetween(importStatement.substring(0, position), importStatement.substring(position, statementLength));
  }

  @NotNull
  private static String createImportStatement(@NotNull String classFqn) {
    return "import " + classFqn + ';';
  }
}
