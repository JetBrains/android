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
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that references to external files (e.g. Java classes, text files) are found by the 'Find
 * Usages' action
 */
@RunWith(JUnit4.class)
public class ExternalFileUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testJavaClassUsagesFound() {
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/foo/JavaClass.java"),
            "package com.google.foo;",
            "public class JavaClass {}");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("com/google/foo/BUILD"),
            "java_library(name = \"lib\", srcs = [\"JavaClass.java\"])");

    PsiReference[] references = FindUsages.findAllReferences(javaFile);
    assertThat(references).hasLength(1);

    Argument.Keyword arg =
        buildFile.findChildByClass(FuncallExpression.class).getKeywordArgument("srcs");
    assertThat(arg).isNotNull();

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(PsiUtils.getParentOfType(ref, Argument.Keyword.class, true)).isEqualTo(arg);
  }

  @Test
  public void testTextFileUsagesFound() {
    PsiFile textFile = workspace.createPsiFile(new WorkspacePath("com/google/foo/data.txt"));

    createBuildFile(
        new WorkspacePath("com/google/foo/BUILD"),
        "filegroup(name = \"lib\", srcs = [\"data.txt\"])",
        "filegroup(name = \"lib2\", srcs = [\"//com/google/foo:data.txt\"])");

    PsiReference[] references = FindUsages.findAllReferences(textFile);
    assertThat(references).hasLength(2);
  }

  @Test
  public void testInvalidReferenceDoesntResolve() {
    createBuildFile(new WorkspacePath("com/google/foo/BUILD"));
    PsiFile textFileInFoo = workspace.createPsiFile(new WorkspacePath("com/google/foo/data.txt"));

    createBuildFile(
        new WorkspacePath("com/google/bar/BUILD"),
        "filegroup(name = \"lib\", srcs = [\":data.txt\"])");

    PsiReference[] references = FindUsages.findAllReferences(textFileInFoo);
    assertThat(references).isEmpty();
  }

  @Test
  public void testSkylarkExtensionUsagesFound() {
    BuildFile ext =
        createBuildFile(new WorkspacePath("com/google/foo/ext.bzl"), "def fn(): return");
    createBuildFile(
        new WorkspacePath("com/google/foo/BUILD"),
        "load(':ext.bzl', 'fn')",
        "load('ext.bzl', 'fn')",
        "load('//com/google/foo:ext.bzl', 'fn')");

    PsiReference[] references = FindUsages.findAllReferences(ext);
    assertThat(references).hasLength(3);
  }

  @Test
  public void testSkylarkExtensionInSubDirectoryUsagesFound() {
    BuildFile ext =
        createBuildFile(new WorkspacePath("com/google/foo/subdir/ext.bzl"), "def fn(): return");
    createBuildFile(
        new WorkspacePath("com/google/foo/BUILD"),
        "load(':subdir/ext.bzl', 'fn')",
        "load('subdir/ext.bzl', 'fn')",
        "load('//com/google/foo:subdir/ext.bzl', 'fn')");

    PsiReference[] references = FindUsages.findAllReferences(ext);
    assertThat(references).hasLength(3);
  }

  @Test
  public void testSkylarkExtensionInSubDirectoryOfDifferentPackage() {
    createBuildFile(new WorkspacePath("com/google/foo/BUILD"));
    BuildFile ext =
        createBuildFile(new WorkspacePath("com/google/foo/subdir/ext.bzl"), "def fn(): return");

    createBuildFile(
        new WorkspacePath("com/google/bar/BUILD"), "load('//com/google/foo:subdir/ext.bzl', 'fn')");

    PsiReference[] references = FindUsages.findAllReferences(ext);
    assertThat(references).hasLength(1);
  }

  @Test
  public void testSkylarkExtensionReferencedFromSubpackage() {
    createBuildFile(new WorkspacePath("com/google/foo/BUILD"));
    BuildFile ext1 =
        createBuildFile(new WorkspacePath("com/google/foo/subdir/testing.bzl"), "def fn(): return");
    createBuildFile(
        new WorkspacePath("com/google/foo/subdir/other.bzl"), "load(':subdir/testing.bzl', 'fn')");

    PsiReference[] references = FindUsages.findAllReferences(ext1);
    assertThat(references).hasLength(1);
  }

  @Test
  public void testFileReferencedFromDifferentPackage() {
    createBuildFile(new WorkspacePath("com/google/foo/BUILD"));
    PsiFile textFileInFoo = workspace.createPsiFile(new WorkspacePath("com/google/foo/data.txt"));

    createBuildFile(
        new WorkspacePath("com/google/bar/BUILD"),
        "filegroup(name = \"lib\", srcs = [\"//com/google/foo:data.txt\"])");

    PsiReference[] references = FindUsages.findAllReferences(textFileInFoo);
    assertThat(references).hasLength(1);
  }
}
