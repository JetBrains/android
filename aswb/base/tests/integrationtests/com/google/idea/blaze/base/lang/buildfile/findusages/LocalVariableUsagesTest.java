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
import com.google.idea.blaze.base.lang.buildfile.psi.AssignmentStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.LocalReference;
import com.google.idea.blaze.base.lang.buildfile.references.TargetReference;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that references to local variables are found by the 'Find Usages' action TODO: Support
 * comprehension suffix, and add test for it
 */
@RunWith(JUnit4.class)
public class LocalVariableUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testLocalFuncallReference() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "localVar = 5", "funcall(localVar)");

    TargetExpression target =
        buildFile.findChildByClass(AssignmentStatement.class).getLeftHandSideExpression();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);

    FuncallExpression funcall = buildFile.findChildByClass(FuncallExpression.class);
    assertThat(funcall).isNotNull();

    PsiElement ref = references[0].getElement();
    assertThat(PsiUtils.getParentOfType(ref, FuncallExpression.class, true)).isEqualTo(funcall);
  }

  @Test
  public void testLocalNestedReference() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "localVar = 5",
            "def function(name):",
            "    tempVar = localVar");

    TargetExpression target =
        buildFile.findChildByClass(AssignmentStatement.class).getLeftHandSideExpression();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);

    FunctionStatement function = buildFile.findChildByClass(FunctionStatement.class);
    assertThat(function).isNotNull();

    PsiElement ref = references[0].getElement();
    assertThat(ref.getParent()).isInstanceOf(AssignmentStatement.class);
    assertThat(PsiUtils.getParentOfType(ref, FunctionStatement.class, true)).isEqualTo(function);
  }

  // the case where a symbol is the target of multiple assignment statements
  @Test
  public void testMultipleAssignments() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "var = 5", "var += 1", "var = 0");

    TargetExpression target =
        buildFile.findChildByClass(AssignmentStatement.class).getLeftHandSideExpression();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(2);

    // We cannot guarantee order of references.
    List<Class<?>> referenceClasses =
        Arrays.stream(references).map(Object::getClass).collect(Collectors.toList());
    assertThat(referenceClasses).containsExactly(TargetReference.class, LocalReference.class);
  }
}
