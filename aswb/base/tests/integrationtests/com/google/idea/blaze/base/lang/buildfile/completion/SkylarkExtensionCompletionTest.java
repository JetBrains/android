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

/** Tests auto-complete of skylark bzl files in 'load' statements. */
@RunWith(JUnit4.class)
public class SkylarkExtensionCompletionTest extends BuildFileIntegrationTestCase {

  private VirtualFile createAndSetCaret(WorkspacePath workspacePath, String... fileContents) {
    VirtualFile file = workspace.createFile(workspacePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }

  @Test
  public void testSimpleCase() {
    workspace.createFile(new WorkspacePath("skylark.bzl"));
    VirtualFile file = createAndSetCaret(new WorkspacePath("BUILD"), "load(':<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl'");
  }

  @Test
  public void testSelfNotInResults() {
    workspace.createFile(new WorkspacePath("BUILD"));
    createAndSetCaret(new WorkspacePath("self.bzl"), "load(':<caret>'");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  @Test
  public void testSelfNotInResults2() {
    workspace.createFile(new WorkspacePath("skylark.bzl"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file = createAndSetCaret(new WorkspacePath("self.bzl"), "load(':<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl'");
  }

  @Test
  public void testNoRulesInResults() {
    workspace.createFile(new WorkspacePath("java/com/google/foo/skylark.bzl"));
    workspace.createFile(
        new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = 'foo')");
    VirtualFile file =
        createAndSetCaret(
            new WorkspacePath("java/com/google/bar/BUILD"), "load('//java/com/google/foo:<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load('//java/com/google/foo:skylark.bzl'");

    // now check that the rule would have been picked up outside of the 'load' context
    file =
        createAndSetCaret(
            new WorkspacePath("java/com/google/baz/BUILD"), "'//java/com/google/foo:<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//java/com/google/foo:foo'");
  }

  @Test
  public void testNonSkylarkFilesNotInResults() {
    workspace.createFile(new WorkspacePath("java/com/google/foo/text.txt"));

    createAndSetCaret(
        new WorkspacePath("java/com/google/bar/BUILD"), "load('//java/com/google/foo:<caret>'");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  @Test
  public void testLabelStartsWithColon() {
    workspace.createFile(new WorkspacePath("java/com/google/skylark.bzl"));
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("java/com/google/BUILD"), "load(':<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl'");
  }

  @Test
  public void testLabelStartsWithSlashes() {
    workspace.createFile(new WorkspacePath("java/com/google/skylark.bzl"));
    VirtualFile file =
        createAndSetCaret(
            new WorkspacePath("java/com/google/BUILD"), "load('//java/com/google:<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load('//java/com/google:skylark.bzl'");
  }

  @Test
  public void testLabelStartsWithSlashesWithoutColon() {
    workspace.createFile(new WorkspacePath("java/com/google/skylark.bzl"));
    VirtualFile file =
        createAndSetCaret(
            new WorkspacePath("java/com/google/BUILD"), "load('//java/com/google<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load('//java/com/google:skylark.bzl'");
  }

  @Test
  public void testDirectoryCompletionInLoadStatement() {
    workspace.createFile(new WorkspacePath("java/com/google/skylark.bzl"));
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("java/com/google/BUILD"), "load('//<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load('//java/com/google'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load('//java/com/google:skylark.bzl'");
  }

  @Test
  public void testMultipleFiles() {
    workspace.createFile(new WorkspacePath("java/com/google/skylark.bzl"));
    workspace.createFile(new WorkspacePath("java/com/google/other.bzl"));
    createAndSetCaret(
        new WorkspacePath("java/com/google/BUILD"), "load('//java/com/google:<caret>'");

    String[] strings = editorTest.getCompletionItemsAsStrings();
    assertThat(strings).hasLength(2);
    assertThat(strings)
        .asList()
        .containsExactly("'//java/com/google:other.bzl'", "'//java/com/google:skylark.bzl'");
  }

  // relative paths in skylark extensions which lie in subdirectories
  // are relative to the parent blaze package directory
  @Test
  public void testRelativePathInSubdirectory() {
    workspace.createFile(new WorkspacePath("java/com/google/BUILD"));
    workspace.createFile(
        new WorkspacePath("java/com/google/nonPackageSubdirectory/skylark.bzl"),
        "def function(): return");
    VirtualFile file =
        createAndSetCaret(
            new WorkspacePath("java/com/google/nonPackageSubdirectory/other.bzl"),
            "load(':n<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "load(':nonPackageSubdirectory/skylark.bzl'");
  }
}
