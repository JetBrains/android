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
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that all references to a blaze package (including in the package components of labels) are
 * found by the 'Find Usages' action.
 */
@RunWith(JUnit4.class)
public class BlazePackageFindUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testDirectReferenceFound() {
    BuildFile foo = createBuildFile(new WorkspacePath("java/com/google/foo/BUILD"));

    BuildFile bar =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "package_group(name = \"grp\", packages = [\"//java/com/google/foo\"])");

    PsiReference[] references = FindUsages.findAllReferences(foo);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getContainingFile()).isEqualTo(bar);
  }

  @Test
  public void testLabelFragmentReferenceFound() {
    BuildFile foo =
        createBuildFile(
            new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = \"lib\")");

    BuildFile bar =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "java_library(name = \"lib2\", exports = [\"//java/com/google/foo:lib\"])");

    PsiReference[] references = FindUsages.findAllReferences(foo);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getContainingFile()).isEqualTo(bar);
  }

  /** If these don't resolve, directory rename refactoring won't update all labels correctly */
  @Test
  public void testInternalReferencesResolve() {
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = \"lib\")",
            "java_library(name = \"other\", deps = [\"//java/com/google:lib\"])");

    PsiReference[] references = FindUsages.findAllReferences(buildFile);
    assertThat(references).hasLength(1);
  }
}
