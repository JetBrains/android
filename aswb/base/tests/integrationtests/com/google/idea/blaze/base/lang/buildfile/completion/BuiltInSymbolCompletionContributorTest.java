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
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link BuiltInSymbolCompletionContributor} */
@RunWith(JUnit4.class)
public class BuiltInSymbolCompletionContributorTest extends BuildFileIntegrationTestCase {

  @Before
  public final void before() {
    registerProjectService(
        BuildLanguageSpecProvider.class,
        new BuildLanguageSpecProvider() {
          @Nullable
          @Override
          public BuildLanguageSpec getLanguageSpec() {
            return null;
          }
        });
  }

  @Test
  public void testSimpleTopLevelCompletion() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 0);

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAnyOf("PACKAGE_NAME", "len", "dict", "struct");
    assertFileContents(file, "");
  }

  @Test
  public void testUniqueTopLevelCompletion() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "PACKAGE_N");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "PACKAGE_N".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "PACKAGE_NAME");
    editorTest.assertCaretPosition(editor, 0, "PACKAGE_NAME".length());
  }

  @Test
  public void testNoCompletionInComment() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "#PACK");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "#PACK".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testNoCompletionAfterInteger() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "123");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "123".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testNoCompletionInFuncall() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "java_library()");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "java_library(".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }
}
