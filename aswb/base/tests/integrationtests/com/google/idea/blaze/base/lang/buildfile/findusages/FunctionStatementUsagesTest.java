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
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that usages of function declarations are found */
@RunWith(JUnit4.class)
public class FunctionStatementUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testLocalReferences() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"),
            "def function(name, srcs, deps):",
            "    # function body",
            "function(name = \"foo\")");

    FunctionStatement funcDef = buildFile.findChildByClass(FunctionStatement.class);

    PsiReference[] references = FindUsages.findAllReferences(funcDef);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(FuncallExpression.class);
  }

  @Test
  public void testLoadedFunctionReferences() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google:build_defs.bzl\",",
            "\"function\"",
            ")");

    FunctionStatement funcDef = extFile.findChildByClass(FunctionStatement.class);
    LoadStatement load = buildFile.firstChildOfClass(LoadStatement.class);

    PsiReference[] references = FindUsages.findAllReferences(funcDef);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getParent().getParent()).isEqualTo(load);
  }

  @Test
  public void testFuncallReference() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    FunctionStatement function = extFile.firstChildOfClass(FunctionStatement.class);
    FuncallExpression funcall = buildFile.firstChildOfClass(FuncallExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(function);
    assertThat(references).hasLength(2);

    assertThat(Arrays.stream(references).anyMatch(r -> r.getElement().equals(funcall))).isTrue();
  }
}
