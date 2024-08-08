/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests ParameterCompletionContributor. */
@RunWith(JUnit4.class)
public class ParameterCompletionContributorTest extends BuildFileIntegrationTestCase {

  @Test
  public void testArgsCompletion() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "def function(arg1, *");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "def function(arg1, *".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "def function(arg1, *args");
  }

  @Test
  public void testKwargsCompletion() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "def function(arg1, **");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "def function(arg1, **".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "def function(arg1, **kwargs");
  }
}
