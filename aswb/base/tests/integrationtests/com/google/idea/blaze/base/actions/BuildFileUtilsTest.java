/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.idea.blaze.base.actions.BuildFileUtils} */
@RunWith(JUnit4.class)
public class BuildFileUtilsTest extends BuildFileIntegrationTestCase {
  @Before
  public void setupMacroDefinitionFile() {
    workspace.createPsiFile(new WorkspacePath("foo/bar/BUILD"));
    workspace.createPsiFile(new WorkspacePath("foo/bar/build_defs.bzl"));
  }

  @Test
  public void findMacroWithMatchingPrefix_onlyMatchMacros() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//foo/bar:build_defs.bzl', 'symbol')",
            "java_test(",
            "    name = \"my_lib_test\"",
            ")",
            "symbol(",
            "    name = \"my_lib\"",
            ")");

    Label foundMacro =
        BuildFileUtils.findMacroWithMatchingPrefix(
            buildFile, Label.create("//java/com/google:my_lib_test_some_suffix"));

    assertThat(foundMacro).isEqualTo(Label.create("//java/com/google:my_lib"));
  }

  @Test
  public void findMacroWithMatchingPrefix_delimitedByDash() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//foo/bar:build_defs.bzl', 'symbol')",
            "symbol(",
            "    name = \"my_lib\"",
            ")");

    Label foundMacro =
        BuildFileUtils.findMacroWithMatchingPrefix(
            buildFile, Label.create("//java/com/google:my_lib-test_some_suffix"));

    assertThat(foundMacro).isEqualTo(Label.create("//java/com/google:my_lib"));
  }

  @Test
  public void findMacroWithMatchingPrefix_notDelimitedByDashOrUnderscore() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//foo/bar:build_defs.bzl', 'symbol')",
            "symbol(",
            "    name = \"my_lib\"",
            ")");

    Label foundMacro =
        BuildFileUtils.findMacroWithMatchingPrefix(
            buildFile, Label.create("//java/com/google:my_lib.some_suffix"));

    assertThat(foundMacro).isNull();
  }

  @Test
  public void findMacroWithMatchingPrefix_noMatchingMacro() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_test(",
            "    name = \"my_lib_test\"",
            ")");

    Label foundMacro =
        BuildFileUtils.findMacroWithMatchingPrefix(
            buildFile, Label.create("//java/com/google:my_lib_test_some_suffix"));

    assertThat(foundMacro).isNull();
  }
}
