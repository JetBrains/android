/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.completion.CommonMacroContributor.CommonMacros;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link CommonMacroCompletionContributor} */
@RunWith(JUnit4.class)
public class CommonMacroCompletionContributorTest extends BuildFileIntegrationTestCase {

  private final List<CommonMacros> macros = new ArrayList<>();

  @Before
  public final void before() {
    registerExtension(CommonMacroContributor.EP_NAME, () -> ImmutableList.copyOf(macros));
  }

  @After
  public final void after() {
    macros.clear();
  }

  private String[] getCompletionItems(BuildFile file, int line, int column) throws Throwable {
    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, line, column);
    return editorTest.getCompletionItemsAsStrings();
  }

  @Test
  public void completionResults_trivialExample_containsMacro() throws Throwable {
    macros.add(
        CommonMacros.builder()
            .setLocation("//foo/bar.bzl")
            .setFunctionNames(ImmutableList.of("bar", "baz"))
            .build());

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "");
    assertThat(getCompletionItems(file, 0, 0)).asList().containsAllOf("bar", "baz");
  }

  @Test
  public void completionResults_ignoreExistingSymbols() throws Throwable {
    macros.add(
        CommonMacros.builder()
            .setLocation("//foo/bar.bzl")
            .setFunctionNames(ImmutableList.of("bar", "baz"))
            .build());

    BuildFile file =
        createBuildFile(new WorkspacePath("BUILD"), "load(\"//somewhere:foo.bzl\", \"bar\"");

    String[] completionItems = getCompletionItems(file, 0, 0);
    assertThat(completionItems).asList().doesNotContain("bar");
    assertThat(completionItems).asList().contains("baz");
  }

  @Test
  public void finalResult_emptyFile_loadStatementAtTop() {
    macros.add(
        CommonMacros.builder()
            .setLocation("//foo/bar.bzl")
            .setFunctionNames(ImmutableList.of("bar123"))
            .build());

    PsiFile file = testFixture.configureByText("BUILD", "bar12<caret>");
    editorTest.completeIfUnique();

    assertFileContents(file, "load(\"//foo/bar.bzl\", \"bar123\")", "bar123()");
  }

  @Test
  public void finalResult_loadStatementAfterCommentsAndLoads() {
    macros.add(
        CommonMacros.builder()
            .setLocation("//foo/bar.bzl")
            .setFunctionNames(ImmutableList.of("bar123"))
            .build());

    List<String> contents =
        ImmutableList.of(
            "# comment",
            "# another comment",
            "",
            "load(\"//something:foo.bzl\", \"abc\")",
            "java_library(name = 'abc')",
            "bar12<caret>");

    PsiFile file = testFixture.configureByText("BUILD", Joiner.on('\n').join(contents));
    editorTest.completeIfUnique();

    List<String> expectedOutput =
        ImmutableList.of(
            "# comment",
            "# another comment",
            "",
            "load(\"//something:foo.bzl\", \"abc\")",
            "load(\"//foo/bar.bzl\", \"bar123\")",
            "java_library(name = 'abc')",
            "bar123()");

    assertFileContents(file, expectedOutput);
  }
}
