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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.vcsUtil.VcsUtil.getFileContent;
import static org.junit.Assert.assertTrue;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class AddGradleDependencyTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testAddProdDependency() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/java/com/android/multimodule/MainActivity.java");

    typeImportAndInvokeAction(projectFrame);

    assertBuildFileContains(projectFrame, "app/build.gradle", "compile project(':library3')");
  }

  @Test @IdeGuiTest
  public void testAddTestDependency() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/androidTest/java/com/android/multimodule/ApplicationTest.java");

    typeImportAndInvokeAction(projectFrame);

    assertBuildFileContains(projectFrame, "app/build.gradle", "androidTestCompile project(':library3')");
  }

  private static void typeImportAndInvokeAction(@NotNull IdeFrameFixture projectFrame) {
    EditorFixture editor = projectFrame.getEditor();
    editor.moveTo(editor.findOffset("com.android.multimodule;\n^"));
    editor.enterText("\nimport com.example.MyLibrary;");

    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);

    editor.moveTo(editor.findOffset("com.example.MyLib^rary;"));
    editor.invokeIntentionAction("Add dependency on module 'library3'");

    projectFrame.waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  private static void assertBuildFileContains(@NotNull IdeFrameFixture projectFrame, @NotNull String relativePath,
                                              @NotNull String content) {
    String newBuildFileContent = getFileContent(new File(projectFrame.getProjectPath(), relativePath).getPath());
    assertTrue(newBuildFileContent.contains(content));
  }
}
