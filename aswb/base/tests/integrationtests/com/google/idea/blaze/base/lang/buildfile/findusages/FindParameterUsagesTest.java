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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that usages of function parameters (i.e. by named args in funcall expressions) are found
 */
@RunWith(JUnit4.class)
public class FindParameterUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testLocalReferences() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"),
            "def function(arg1, arg2)",
            "function(arg1 = 1, arg2 = \"name\")");

    FunctionStatement fn = buildFile.findChildByClass(FunctionStatement.class);
    ParameterList params = fn.getParameterList();

    PsiReference[] references = FindUsages.findAllReferences(params.findParameterByName("arg1"));
    assertThat(references).hasLength(1);

    references = FindUsages.findAllReferences(params.findParameterByName("arg2"));
    assertThat(references).hasLength(1);
  }

  @Test
  public void testNonLocalReferences() {
    BuildFile foo =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"), "def function(arg1, arg2)");

    BuildFile bar =
        createBuildFile(
            new WorkspacePath("java/com/google/other/BUILD"),
            "load(\"//java/com/google:build_defs.bzl\", \"function\")",
            "function(arg1 = 1, arg2 = \"name\", extra = x)");

    FunctionStatement fn = foo.findChildByClass(FunctionStatement.class);
    ParameterList params = fn.getParameterList();

    PsiReference[] references = FindUsages.findAllReferences(params.findParameterByName("arg1"));
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement().getContainingFile()).isEqualTo(bar);

    references = FindUsages.findAllReferences(params.findParameterByName("arg2"));
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement().getContainingFile()).isEqualTo(bar);
  }
}
