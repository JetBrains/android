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
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that glob references are resolved correctly. */
@RunWith(JUnit4.class)
public class GlobReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testSimpleGlobReferencingSingleFile() {
    PsiFile ref = workspace.createPsiFile(new WorkspacePath("java/com/google/Test.java"));
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  @Test
  public void testSimpleGlobReferencingSingleFile2() {
    PsiFile ref = workspace.createPsiFile(new WorkspacePath("java/com/google/Test.java"));
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  @Test
  public void testSimpleGlobReferencingSingleFile3() {
    PsiFile ref = workspace.createPsiFile(new WorkspacePath("java/com/google/Test.java"));
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['T*t.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  @Test
  public void testGlobReferencingMultipleFiles() {
    PsiFile ref1 = workspace.createPsiFile(new WorkspacePath("java/com/google/Test.java"));
    PsiFile ref2 = workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(ref1, ref2);
  }

  @Test
  public void testFindsSubDirectories() {
    PsiFile ref1 = workspace.createPsiFile(new WorkspacePath("java/com/google/test/Test.java"));
    PsiFile ref2 = workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(ref1, ref2);
  }

  @Test
  public void testGlobWithExcludes() {
    workspace.createPsiFile(new WorkspacePath("java/com/google/tests/Test.java"));
    PsiFile foo = workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(" + "  ['**/*.java']," + "  exclude = ['tests/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(foo);
  }

  @Test
  public void testIncludeDirectories() {
    workspace.createDirectory(new WorkspacePath("java/com/google/tests"));
    PsiFile test = workspace.createPsiFile(new WorkspacePath("java/com/google/tests/Test.java"));
    PsiFile foo = workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "glob(" + "  ['**/*']," + "  exclude = ['BUILD']," + "  exclude_directories = 0)");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(3);
    assertThat(references).containsExactly(foo, test, test.getParent());
  }

  @Test
  public void testExcludeDirectories() {
    workspace.createDirectory(new WorkspacePath("java/com/google/tests"));
    PsiFile test = workspace.createPsiFile(new WorkspacePath("java/com/google/tests/Test.java"));
    PsiFile foo = workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "glob(['**/*'],  exclude = ['BUILD'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(foo, test);
  }

  @Test
  public void testExcludeDirectories2() {
    workspace.createDirectory(new WorkspacePath("java/com/google/tests"));
    workspace.createPsiFile(new WorkspacePath("java/com/google/tests/Test.java"));
    workspace.createPsiFile(new WorkspacePath("java/com/google/Foo.java"));
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"), "glob(['**/*'],  exclude = ['**/*'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).isEmpty();
  }

  @Test
  public void testFilesInSubpackagesExcluded() {
    BuildFile pkg =
        createBuildFile(new WorkspacePath("java/com/google/BUILD"), "glob(['**/*.java'])");
    createBuildFile(new WorkspacePath("java/com/google/other/BUILD"));
    workspace.createFile(new WorkspacePath("java/com/google/other/Other.java"));

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(pkg, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).isEmpty();
  }

  private List<PsiElement> multiResolve(GlobExpression glob) {
    ResolveResult[] result = glob.getReference().multiResolve(false);
    return Arrays.stream(result)
        .map(ResolveResult::getElement)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
