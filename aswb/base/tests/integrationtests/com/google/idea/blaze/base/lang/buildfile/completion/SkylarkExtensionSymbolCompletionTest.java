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
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests auto-complete of symbols loaded from skylark bzl files. */
@RunWith(JUnit4.class)
public class SkylarkExtensionSymbolCompletionTest extends BuildFileIntegrationTestCase {

  private VirtualFile createAndSetCaret(WorkspacePath workspacePath, String... fileContents) {
    VirtualFile file = workspace.createFile(workspacePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }

  @Test
  public void testGlobalVariable() {
    workspace.createFile(new WorkspacePath("skylark.bzl"), "VAR = []");
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'VAR')");
  }

  @Test
  public void testFunctionStatement() {
    workspace.createFile(new WorkspacePath("skylark.bzl"), "def fn(param):stmt");
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'fn')");
  }

  @Test
  public void testMultipleOptions() {
    workspace.createFile(new WorkspacePath("skylark.bzl"), "def fn(param):stmt", "VAR = []");
    createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    String[] options = editorTest.getCompletionItemsAsStrings();
    assertThat(options).asList().containsExactly("'fn'", "'VAR'");
  }

  @Test
  public void testRulesNotIncluded() {
    workspace.createFile(
        new WorkspacePath("skylark.bzl"),
        "java_library(name = 'lib')",
        "native.java_library(name = 'foo'");
    createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  @Test
  public void testLoadedSymbols() {
    workspace.createFile(new WorkspacePath("other.bzl"), "def function()");
    workspace.createFile(new WorkspacePath("skylark.bzl"), "load(':other.bzl', 'function')");
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'function')");
  }

  @Test
  public void testNotLoadedSymbolsAreNotIncluded() {
    workspace.createFile(
        new WorkspacePath("other.bzl"), "def function():stmt", "def other_function():stmt");
    workspace.createFile(new WorkspacePath("skylark.bzl"), "load(':other.bzl', 'function')");
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'function')");
  }

  @Test
  public void testPrivateSymbolsNotAutocompleted() {
    workspace.createFile(
        new WorkspacePath("skylark.bzl"),
        "_local = 1",
        "GLOBAL_VAR = 2",
        "def _local_fn():stmt",
        "def global_fn():stmt");
    createAndSetCaret(new WorkspacePath("BUILD"), "load(':skylark.bzl', '<caret>')");

    String[] options = editorTest.getCompletionItemsAsStrings();
    assertThat(options).asList().containsExactly("'GLOBAL_VAR'", "'global_fn'");
  }
}
