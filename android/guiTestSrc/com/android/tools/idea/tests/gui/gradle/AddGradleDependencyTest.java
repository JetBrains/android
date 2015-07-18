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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.vcsUtil.VcsUtil.getFileContent;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class AddGradleDependencyTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testAddProdModuleDependency() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/java/com/android/multimodule/MainActivity.java");

    typeImportAndInvokeAction(projectFrame, "com.android.multimodule;\n^", "import com.example.MyLib^rary;",
                              "Add dependency on module 'library3'");

    assertBuildFileContains(projectFrame, "app/build.gradle", "compile project(':library3')");
  }

  @Test @IdeGuiTest
  public void testAddTestModuleDependency() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/androidTest/java/com/android/multimodule/ApplicationTest.java");

    typeImportAndInvokeAction(projectFrame, "com.android.multimodule;\n^", "import com.example.MyLib^rary;",
                              "Add dependency on module 'library3'");

    assertBuildFileContains(projectFrame, "app/build.gradle", "androidTestCompile project(':library3')");
  }

  @Test @IdeGuiTest
  public void testAddLibDependencyDeclaredInJavaProject() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    File buildFile = new File(projectFrame.getProjectPath(), join("library3", FN_BUILD_GRADLE));
    assertThat(buildFile).isFile();
    appendToFile(buildFile, "dependencies { compile 'com.google.guava:guava:18.0' }");
    projectFrame.requestProjectSync();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/androidTest/java/com/android/multimodule/ApplicationTest.java");
    typeImportAndInvokeAction(projectFrame, "com.android.multimodule;\n^","import com.google.common.base.Obje^cts;",
                              "Add library 'com.google.guava:guava:18.0' to classpath");

    assertBuildFileContains(projectFrame, "app/build.gradle", "compile 'com.google.guava:guava:18.0'");
  }

  @Test @IdeGuiTest
  public void testAddLibDependencyDeclaredInAndroidProject() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    File buildFile = new File(projectFrame.getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(buildFile).isFile();
    appendToFile(buildFile, "dependencies { compile 'com.google.guava:guava:18.0' }");
    projectFrame.requestProjectSync();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("library3/src/main/java/com/example/MyLibrary.java");
    typeImportAndInvokeAction(projectFrame, "package com.example;\n^", "import com.google.common.base.Obje^cts;",
                              "Add library 'com.google.guava:guava:18.0' to classpath");

    assertBuildFileContains(projectFrame, "app/build.gradle", "compile 'com.google.guava:guava:18.0'");
  }

  private static void typeImportAndInvokeAction(@NotNull IdeFrameFixture projectFrame, @NotNull String lineToType,
                                                @NotNull String testImportStatement, @NotNull String intention) {
    EditorFixture editor = projectFrame.getEditor();
    editor.moveTo(editor.findOffset(lineToType));
    editor.enterText("\n" + testImportStatement.replace("^", ""));

    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);

    editor.moveTo(editor.findOffset(testImportStatement));
    editor.invokeIntentionAction(intention);

    projectFrame.waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }
  private static void assertBuildFileContains(@NotNull IdeFrameFixture projectFrame, @NotNull String relativePath,
                                              @NotNull String content) {
    String newBuildFileContent = getFileContent(new File(projectFrame.getProjectPath(), relativePath).getPath());
    assertTrue(newBuildFileContent.contains(content));
  }
}
