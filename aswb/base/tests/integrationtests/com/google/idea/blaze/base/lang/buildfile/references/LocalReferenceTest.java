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
package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.AssignmentStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that local references (to TargetExpressions within a given file) are correctly resolved.
 */
@RunWith(JUnit4.class)
public class LocalReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testCreatesReference() {
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "a = 1", "c = a");

    AssignmentStatement[] stmts = file.childrenOfClass(AssignmentStatement.class);
    assertThat(stmts).hasLength(2);
    assertThat(stmts[1].getAssignedValue()).isInstanceOf(ReferenceExpression.class);

    ReferenceExpression ref = (ReferenceExpression) stmts[1].getAssignedValue();
    assertThat(ref.getReference()).isInstanceOf(LocalReference.class);
  }

  @Test
  public void testReferenceResolves() {
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "a = 1", "c = a");

    AssignmentStatement[] stmts = file.childrenOfClass(AssignmentStatement.class);
    ReferenceExpression ref = (ReferenceExpression) stmts[1].getAssignedValue();

    PsiElement referencedElement = ref.getReferencedElement();
    assertThat(referencedElement).isEqualTo(stmts[0].getLeftHandSideExpression());
  }

  @Test
  public void testTargetInOuterScope() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "a = 1", "function(c = a)");

    TargetExpression target =
        file.findChildByClass(AssignmentStatement.class).getLeftHandSideExpression();
    FuncallExpression funcall = file.findChildByClass(FuncallExpression.class);
    ReferenceExpression ref =
        funcall.getKeywordArgument("c").firstChildOfClass(ReferenceExpression.class);
    assertThat(ref.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testReferenceInsideFuncallExpression() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "a = 1", "a.function(c)");

    TargetExpression target =
        file.findChildByClass(AssignmentStatement.class).getLeftHandSideExpression();
    FuncallExpression funcall = file.findChildByClass(FuncallExpression.class);
    ReferenceExpression ref = funcall.firstChildOfClass(ReferenceExpression.class);
    assertThat(ref.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testReferenceToFunctionArg() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/defs.bzl"),
            "def function(arg1, arg2):",
            "  arg1(arg2)");

    FunctionStatement def = file.findFunctionInScope("function");
    FuncallExpression call = PsiUtils.findFirstChildOfClassRecursive(file, FuncallExpression.class);
    Parameter fnParam = def.getParameterList().findParameterByName("arg1");
    assertThat(fnParam).isNotNull();
    assertThat(call.getReference().resolve()).isEqualTo(fnParam);
  }
}
