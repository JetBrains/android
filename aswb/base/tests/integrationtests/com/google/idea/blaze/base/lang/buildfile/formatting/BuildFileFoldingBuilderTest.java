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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuildFileFoldingBuilder}. */
@RunWith(JUnit4.class)
public class BuildFileFoldingBuilderTest extends BuildFileIntegrationTestCase {

  @Test
  public void testEndOfFileFunctionDelcaration() throws Throwable {
    // bug 28618935: test no NPE in the case where there's no
    // statement list following the func-def colon
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "def function():");

    getFoldingRegions(file);
  }

  @Test
  public void testMultilineCommentFolded() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "# multi-line comment, folded",
            "# second line of comment",
            "def function(arg1, arg2):",
            "    stmt1",
            "    stmt2",
            "",
            "variable = 1");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(2);
    assertThat(foldingRegions[0].getElement().getPsi())
        .isEqualTo(file.firstChildOfClass(PsiComment.class));
  }

  @Test
  public void testMultilineCommentIncludingBlankLinesIsFolded() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "# multi-line comment, folded",
            "# second line of comment",
            "",
            "# another comment after a blank line");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
  }

  @Test
  public void testMultilineStringFoldedToFirstLine() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "\"\"\"First line of string",
            "Second line of string\"\"\"");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getPlaceholderText())
        .isEqualTo("\"\"\"First line of string...\"\"\"");
  }

  @Test
  public void testFuncDefStatementsFolded() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "# multi-line comment, folded",
            "# second line of comment",
            "def function(arg1, arg2):",
            "    stmt1",
            "    stmt2",
            "",
            "variable = 1");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(2);
    assertThat(foldingRegions[1].getElement().getPsi())
        .isEqualTo(file.findFunctionInScope("function"));
  }

  @Test
  public void testRulesFolded() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    srcs = glob(['*.java']),",
            ")");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getElement().getPsi()).isEqualTo(file.findRule("lib"));
  }

  @Test
  public void testLoadStatementFolded() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "   '//java/com/foo/build_defs.bzl',",
            "   'function1',",
            "   'function2',",
            ")");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getElement().getPsi())
        .isEqualTo(file.findChildByClass(LoadStatement.class));
  }

  private FoldingDescriptor[] getFoldingRegions(BuildFile file) throws Throwable {
    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    return new BuildFileFoldingBuilder().buildFoldRegions(file.getNode(), editor.getDocument());
  }
}
