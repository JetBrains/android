/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.lang.build.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for code completion of funcall arguments. */
@RunWith(JUnit4.class)
public class JavaClassQualifiedNameCompletionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testCompleteClassName() throws Throwable {
    workspace.createPsiFile(
        new WorkspacePath("java/com/google/bin/Main.java"),
        "package com.google.bin;",
        "public class Main {",
        "  public void main() {}",
        "}");
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_binary(",
            "    name = 'binary',",
            "    main_class = 'com.google.bin.M',",
            ")");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 2, "    main_class = 'com.google.bin.M".length());

    LookupElement[] completionItems = testFixture.complete(CompletionType.CLASS_NAME);
    assertThat(completionItems).isNull();
    assertFileContents(
        file,
        "java_binary(",
        "    name = 'binary',",
        "    main_class = 'com.google.bin.Main',",
        ")");
  }

  @Test
  public void testNoCompletionForOtherAttributes() throws Throwable {
    workspace.createPsiFile(
        new WorkspacePath("java/com/google/bin/Main.java"),
        "package com.google.bin;",
        "public class Main {",
        "  public void main() {}",
        "}");
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_binary(",
            "    name = 'binary',",
            "    main_clazz = 'com.google.bin.M',",
            ")");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 2, "    main_clazz = 'com.google.bin.M".length());

    LookupElement[] completionItems = testFixture.complete(CompletionType.CLASS_NAME);
    assertThat(completionItems).isEmpty();
    assertFileContents(
        file, "java_binary(", "    name = 'binary',", "    main_clazz = 'com.google.bin.M',", ")");
  }
}
