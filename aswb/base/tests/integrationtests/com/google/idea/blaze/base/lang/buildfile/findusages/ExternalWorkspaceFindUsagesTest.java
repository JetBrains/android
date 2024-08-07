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
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test that references to external workspaces appear in 'find usages' results. */
@RunWith(JUnit4.class)
public class ExternalWorkspaceFindUsagesTest extends BuildFileIntegrationTestCase {

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @Test
  public void testFindUsagesFromWorkspaceFile() {
    BuildFile workspaceBuildFile =
        createBuildFile(
            new WorkspacePath("WORKSPACE"),
            "maven_jar(",
            "    name = 'javax_inject',",
            "    artifact = 'javax.inject:javax.inject:1',",
            "    sha1 = '6975da39a7040257bd51d21a231b76c915872d38',",
            ")");
    BuildFile refFile1 =
        createBuildFile(
            new WorkspacePath("java/com/foo/BUILD"),
            "java_library(name = 'javax_inject', exports = ['@javax_inject//jar'])");

    BuildFile refFile2 =
        createBuildFile(
            new WorkspacePath("java/com/bar/build_defs.bzl"),
            "DEP = '@javax_inject//invalid:nonsense'");

    FuncallExpression target = workspaceBuildFile.findRule("javax_inject");
    StringLiteral ref1 =
        PsiUtils.findFirstChildOfClassRecursive(
            refFile1.findRule("javax_inject").getKeywordArgument("exports"), StringLiteral.class);
    StringLiteral ref2 = PsiUtils.findFirstChildOfClassRecursive(refFile2, StringLiteral.class);

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(2);
    assertThat(Arrays.stream(references).map(PsiReference::getElement).collect(Collectors.toList()))
        .containsExactly(ref1, ref2);
  }

  @Test
  public void testFindUsagesFromExternalWorkspaceFile() {
    BuildFile workspaceBuildFile =
        createBuildFile(
            new WorkspacePath("BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    exports = ['@junit//:jar'],",
            ")");
    BuildFile externalFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'jar',",
                "    jars = ['junit-4.11.jar'],",
                ")");

    FuncallExpression target = externalFile.findRule("jar");
    assertThat(target).isNotNull();

    Argument.Keyword arg = workspaceBuildFile.findRule("lib").getKeywordArgument("exports");
    StringLiteral label = PsiUtils.findFirstChildOfClassRecursive(arg, StringLiteral.class);
    assertThat(label).isNotNull();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(label);
  }

  @Test
  public void testFindUsagesFromExternalWorkspaceFileShortFormLabel() {
    BuildFile workspaceBuildFile =
        createBuildFile(
            new WorkspacePath("BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    exports = ['@junit'],",
            ")");
    BuildFile externalFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'junit',",
                "    jars = ['junit-4.11.jar'],",
                ")");

    FuncallExpression target = externalFile.findRule("junit");
    assertThat(target).isNotNull();

    Argument.Keyword arg = workspaceBuildFile.findRule("lib").getKeywordArgument("exports");
    StringLiteral label = PsiUtils.findFirstChildOfClassRecursive(arg, StringLiteral.class);
    assertThat(label).isNotNull();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(label);
  }

  private PsiFile createFileInExternalWorkspace(
      String workspaceName, WorkspacePath path, String... contents) {
    String filePath =
        Paths.get(getExternalSourceRoot().getPath(), workspaceName, path.relativePath()).toString();
    return fileSystem.createPsiFile(filePath, contents);
  }

  private File getExternalSourceRoot() {
    return WorkspaceHelper.getExternalSourceRoot(
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData());
  }
}
