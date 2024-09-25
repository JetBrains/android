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
package com.google.idea.blaze.base.lang.buildfile.search;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for BlazePackage
 */
@RunWith(JUnit4.class)
public class BlazePackageTest extends BuildFileIntegrationTestCase {

  @Test
  public void testFindPackage() {
    BuildFile packageFile = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    PsiFile subDirFile =
      workspace.createPsiFile(new WorkspacePath("java/com/google/tools/test.txt"));
    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage).isNotNull();
    assertThat(blazePackage.buildFile).isEqualTo(packageFile);
  }

  @Test
  public void testScopeDoesntCrossPackageBoundary() {
    BuildFile pkg = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    BuildFile subpkg = createBuildFile(new WorkspacePath("java/com/google/other/BUILD"));

    BlazePackage blazePackage = BlazePackage.getContainingPackage(pkg);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertThat(blazePackage.getSearchScope(false).contains(subpkg.getVirtualFile())).isFalse();
  }

  @Test
  public void testScopeIncludesSubdirectoriesWhichAreNotBlazePackages() {
    BuildFile pkg = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    createBuildFile(new WorkspacePath("java/com/google/foo/bar/BUILD"));
    PsiFile subDirFile = workspace.createPsiFile(new WorkspacePath("java/com/google/foo/test.txt"));

    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertThat(blazePackage.getSearchScope(false).contains(subDirFile.getVirtualFile())).isTrue();
  }

  @Test
  public void testScopeLimitedToBlazeFiles() {
    BuildFile pkg = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    createBuildFile(new WorkspacePath("java/com/google/foo/bar/BUILD"));
    PsiFile subDirFile = workspace.createPsiFile(new WorkspacePath("java/com/google/foo/test.txt"));

    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertThat(blazePackage.getSearchScope(true).contains(subDirFile.getVirtualFile())).isFalse();
  }

  @Test
  public void testGetPackageRelativePath() {
    BuildFile pkg = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    BlazePackage blazePackage = BlazePackage.getContainingPackage(pkg);
    assertThat(blazePackage.getPackageRelativePath(workspaceRoot.path().resolve("java/com/google/Some.java").toString()))
      .isEqualTo("Some.java");
    assertThat(blazePackage.getPackageRelativePath(workspaceRoot.path().resolve("java/com/google/foo/Some.java").toString()))
      .isEqualTo("foo/Some.java");
  }
}
