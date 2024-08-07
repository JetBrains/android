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
package com.google.idea.blaze.base.lang.buildfile.validation;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.sdkcompat.BaseSdkTestCompat;
import com.intellij.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests glob validation. */
@RunWith(JUnit4.class)
public class GlobValidationTest extends BuildFileIntegrationTestCase {

  @Test
  public void testNormalGlob() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'])");

    assertNoErrors(file);
  }

  @Test
  public void testNamedIncludeArgument() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "glob(include = ['**/*.java'])");

    assertNoErrors(file);
  }

  @Test
  public void testAllArguments() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(['**/*.java'], exclude = ['test/*.java'], exclude_directories = 0)");

    assertNoErrors(file);
  }

  @Test
  public void testEmptyExcludeList() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'], exclude = [])");

    assertNoErrors(file);
  }

  @Test
  public void testNoIncludesError() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(exclude = ['BUILD'])");

    assertHasError(file, "Glob expression must contain at least one included string");
  }

  @Test
  public void testSingletonExcludeArgumentError() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'], exclude = 'BUILD')");

    assertHasError(file, "Glob parameter 'exclude' must be a list of strings");
  }

  @Test
  public void testSingletonIncludeArgumentError() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(include = '**/*.java')");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testInvalidExcludeDirectoriesValue() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(['**/*.java'], exclude = ['test/*.java'], exclude_directories = true)");

    assertHasError(file, "exclude_directories parameter to glob must be 0 or 1");
  }

  @Test
  public void testUnrecognizedArgumentError() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(['**/*.java'], exclude = ['test/*.java'], extra = 1)");

    assertHasError(file, "Unrecognized glob argument");
  }

  @Test
  public void testInvalidListArgumentValue() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(include = foo)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testLocalVariableReference() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "foo = ['*.java']", "glob(include = foo)");

    assertNoErrors(file);
  }

  @Test
  public void testLoadedVariableReference() {
    createBuildFile(new WorkspacePath("java/com/foo/vars.bzl"), "LIST_VAR = ['*']");
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//java/com/foo:vars.bzl', 'LIST_VAR')",
            "glob(include = LIST_VAR)");

    assertNoErrors(file);
  }

  @Test
  public void testInvalidLoadedVariableReference() {
    createBuildFile(
        new WorkspacePath("java/com/foo/vars.bzl"), "LIST_VAR = ['*']", "def function()");
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//java/com/foo:vars.bzl', 'LIST_VAR', 'function')",
            "glob(include = function)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testUnresolvedReferenceExpression() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(include = ref)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testPossibleListExpressionFuncallExpression() {
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(include = fn.list)");

    assertNoErrors(file);
  }

  @Test
  public void testPossibleListExpressionParameter() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "def function(param1, param2):",
            "    glob(include = param1)");

    assertNoErrors(file);
  }

  @Test
  public void testNestedGlobs() {
    // blaze accepts nested globs
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(glob(['*.java']))");

    assertNoErrors(file);
  }

  @Test
  public void testKnownInvalidResolvedListExpression() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "bool_literal = True",
            "glob(bool_literal)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testKnownInvalidResolvedString() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "bool_literal = True",
            "glob([bool_literal])");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  @Test
  public void testPossibleStringLiteralIfStatement() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(include = ['*.java', if test : a else b])");

    // we don't know what the IfStatement evaluates to
    assertNoErrors(file);
  }

  @Test
  public void testPossibleStringLiteralParameter() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "def function(param1, param2):",
            "    glob(include = [param1])");

    assertNoErrors(file);
  }

  private void assertNoErrors(BuildFile file) {
    assertThat(validateFile(file)).isEmpty();
  }

  private void assertHasError(BuildFile file, String error) {
    assertHasError(validateFile(file), error);
  }

  private void assertHasError(List<Annotation> annotations, String error) {
    List<String> messages =
        annotations.stream().map(Annotation::getMessage).collect(Collectors.toList());

    assertThat(messages).contains(error);
  }

  private List<Annotation> validateFile(BuildFile file) {
    return BaseSdkTestCompat.testAnnotator(
        new GlobErrorAnnotator(),
        PsiUtils.findAllChildrenOfClassRecursive(file, GlobExpression.class)
            .toArray(GlobExpression[]::new));
  }

}
