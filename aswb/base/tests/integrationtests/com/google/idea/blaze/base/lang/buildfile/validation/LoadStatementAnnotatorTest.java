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
package com.google.idea.blaze.base.lang.buildfile.validation;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.sdkcompat.BaseSdkTestCompat;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LoadStatementAnnotator}. */
@RunWith(JUnit4.class)
public class LoadStatementAnnotatorTest extends BuildFileIntegrationTestCase {

  @Test
  public void testNoWarningsInNormalLoad() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//tools/ide/build_test.bzl', 'build_test')",
            "load(':local_file.bzl', 'symbol')");
    assertNoAnnotations(file);
  }

  @Test
  public void testNoWarningsInExternalLoad() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('@tools//ide:build_test.bzl', 'build_test')");
    assertNoAnnotations(file);
  }

  @Test
  public void testNoWarningsWhenTyping() {
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "load('/')");
    assertNoAnnotations(file);
  }

  @Test
  public void testWarningForDeprecatedFormat() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('/tools/ide/build_test.bzl', 'build_test')");
    assertHasAnnotation(
        file,
        "Deprecated load syntax; loaded Starlark module should be in label format.",
        HighlightSeverity.WARNING);
  }

  @Test
  public void testErrorForUnrecognizedFormat() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "load('not a skylark label', 'symbol')");
    assertHasAnnotation(
        file, "Invalid load syntax: missing Starlark module.", HighlightSeverity.ERROR);
  }

  @Test
  public void testErrorForPrivateSymbols() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "load(':skylark.bzl', '_local_symbol')");
    assertHasAnnotation(
        file, "Symbol '_local_symbol' is private and cannot be imported.", HighlightSeverity.ERROR);
  }

  private void assertNoAnnotations(BuildFile file) {
    assertThat(validateFile(file)).isEmpty();
  }

  private void assertHasAnnotation(BuildFile file, String message, HighlightSeverity type) {
    assertThat(
            validateFile(file)
                .stream()
                .filter(ann -> ann.getSeverity().equals(type))
                .map(Annotation::getMessage)
                .collect(Collectors.toList()))
        .contains(message);
  }

  private List<Annotation> validateFile(BuildFile file) {
    return BaseSdkTestCompat.testAnnotator(
        new LoadStatementAnnotator(),
        PsiUtils.findAllChildrenOfClassRecursive(file, BuildElement.class)
            .toArray(BuildElement[]::new));
  }
}
