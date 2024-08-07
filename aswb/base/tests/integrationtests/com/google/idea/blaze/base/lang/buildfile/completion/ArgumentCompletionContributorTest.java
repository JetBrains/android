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
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for code completion of funcall arguments. */
@RunWith(JUnit4.class)
public class ArgumentCompletionContributorTest extends BuildFileIntegrationTestCase {

  private CompletionAutoPopupTester completionTester;

  @Before
  public final void before() {
    completionTester = new CompletionAutoPopupTester(testFixture);
  }

  /** Completion UI testing can't be run on the EDT. */
  @Override
  protected boolean runTestsOnEdt() {
    return false;
  }

  @Test
  public void testIncompleteFuncall() throws Throwable {
    completionTester.runWithAutoPopupEnabled(
            () -> {
              BuildFile file =
                  createBuildFile(
                      new WorkspacePath("BUILD"),
                      "def function(name, deps, srcs):",
                      "  # empty function",
                      "function(de");

              Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
              editorTest.setCaretPosition(editor, 2, "function(de".length());

              completionTester.typeWithPauses("p");
              LookupElement[] completionItems = testFixture.completeBasic();
              assertThat(completionItems).isNull();

              assertFileContents(
                  file, "def function(name, deps, srcs):", "  # empty function", "function(deps");
            });
  }

  @Test
  public void testExistingKeywordArg() throws Throwable {
    completionTester.runWithAutoPopupEnabled(
            () -> {
              BuildFile file =
                  createBuildFile(
                      new WorkspacePath("BUILD"),
                      "def function(name, deps, srcs):",
                      "  # empty function",
                      "function(name = \"lib\")");

              Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
              editorTest.setCaretPosition(editor, 2, "function(".length());

              completionTester.joinAutopopup();
              completionTester.joinCompletion();
              String[] completionItems = editorTest.getCompletionItemsAsStrings();
              assertThat(completionItems)
                  .asList()
                  .containsAllOf("name", "deps", "srcs", "function");
            });
  }

  @Test
  public void testNoArgumentCompletionInComment() throws Throwable {
    completionTester.runWithAutoPopupEnabled(
            () -> {
              BuildFile file =
                  createBuildFile(
                      new WorkspacePath("BUILD"),
                      "def function(name, deps, srcs):",
                      "  # empty function",
                      "function(#");

              Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
              editorTest.setCaretPosition(editor, 2, "function(#".length());

              completionTester.typeWithPauses("n");
              assertThat(testFixture.getLookup()).isNull();
            });
  }
}
