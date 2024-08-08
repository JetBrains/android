/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.quickfix;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.MockProblemDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link DeprecatedLoadQuickFix}. */
@RunWith(JUnit4.class)
public class DeprecatedLoadQuickFixTest extends BuildFileIntegrationTestCase {

  @Test
  public void testParentDirectoryHasNoBuildFile() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('/java/com/google/subdir/build_defs', 'symbol')");

    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(file, StringLiteral.class);
    applyQuickFix(string);

    assertThat(string.getStringContents()).isEqualTo("//java/com/google:subdir/build_defs.bzl");
  }

  @Test
  public void testBlazePackageIsParentDirectory() {
    workspace.createPsiFile(new WorkspacePath("foo/bar/BUILD"));
    workspace.createPsiFile(new WorkspacePath("foo/bar/build_defs.bzl"));

    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "load('/foo/bar/build_defs', 'symbol')");

    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(file, StringLiteral.class);
    applyQuickFix(string);

    assertThat(string.getStringContents()).isEqualTo("//foo/bar:build_defs.bzl");
  }

  @Test
  public void testNormalLoadStatementUntouched() {
    workspace.createPsiFile(new WorkspacePath("foo/bar/BUILD"));
    workspace.createPsiFile(new WorkspacePath("foo/bar/build_defs.bzl"));

    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//foo/bar:build_defs.bzl', 'symbol')");

    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(file, StringLiteral.class);
    String prevString = string.getStringContents();
    applyQuickFix(string);
    assertThat(string.getStringContents()).isEqualTo(prevString);
  }

  @Test
  public void testRelativeLoadStatementUntouched() {
    workspace.createPsiFile(new WorkspacePath("foo/bar/build_defs.bzl"));
    BuildFile file =
        createBuildFile(new WorkspacePath("foo/bar/BUILD"), "load(':build_defs.bzl', 'symbol')");

    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(file, StringLiteral.class);
    String prevString = string.getStringContents();
    applyQuickFix(string);
    assertThat(string.getStringContents()).isEqualTo(prevString);
  }

  private void applyQuickFix(StringLiteral string) {
    WriteCommandAction.runWriteCommandAction(
        getProject(),
        () -> DeprecatedLoadQuickFix.INSTANCE.applyFix(getProject(), descriptorForPsi(string)));
  }

  private static ProblemDescriptor descriptorForPsi(PsiElement psi) {
    return new MockProblemDescriptor(
        psi, "mock", ProblemHighlightType.LIKE_DEPRECATED, DeprecatedLoadQuickFix.INSTANCE);
  }
}
