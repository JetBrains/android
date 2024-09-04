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
package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.nio.file.Paths;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that labels referencing external workspaces are correctly resolved. */
@RunWith(JUnit4.class)
public class ExternalWorkspaceReferenceTest extends BuildFileIntegrationTestCase {

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @Test
  public void testExternalWorkspaceTargetReference() {
    BuildFile workspaceBuildFile =
        createBuildFile(
            new WorkspacePath("BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    exports = ['@junit//:jar'],",
            ")");
    BuildFile externalBuildFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'jar',",
                "    jars = ['junit-4.11.jar'],",
                ")");

    FuncallExpression target = externalBuildFile.findRule("jar");
    assertThat(target).isNotNull();

    Argument.Keyword arg = workspaceBuildFile.findRule("lib").getKeywordArgument("exports");
    StringLiteral label = PsiUtils.findFirstChildOfClassRecursive(arg, StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testExternalWorkspaceTargetReferenceShortForm() {
    BuildFile workspaceBuildFile =
        createBuildFile(
            new WorkspacePath("BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    exports = ['@junit'],",
            ")");
    BuildFile externalBuildFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'junit',",
                "    jars = ['junit-4.11.jar'],",
                ")");

    FuncallExpression target = externalBuildFile.findRule("junit");
    assertThat(target).isNotNull();

    Argument.Keyword arg = workspaceBuildFile.findRule("lib").getKeywordArgument("exports");
    StringLiteral label = PsiUtils.findFirstChildOfClassRecursive(arg, StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testLocalTargetReferenceWithinExternalWorkspaceResolves() {
    BuildFile externalFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'jar',",
                "    jars = ['junit-4.11.jar'],",
                ")",
                "java_library(",
                "    name = 'lib',",
                "    srcs = [':jar'],",
                ")");
    FuncallExpression target = externalFile.findRule("jar");
    assertThat(target).isNotNull();

    Argument.Keyword arg = externalFile.findRule("lib").getKeywordArgument("srcs");
    StringLiteral label = PsiUtils.findFirstChildOfClassRecursive(arg, StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testFileReferenceWithinExternalWorkspaceResolves() {
    BuildFile externalFile =
        (BuildFile)
            createFileInExternalWorkspace(
                "junit",
                new WorkspacePath("BUILD"),
                "java_import(",
                "    name = 'target',",
                "    jars = ['junit-4.11.jar'],",
                ")");
    PsiFile jarFile = createFileInExternalWorkspace("junit", new WorkspacePath("junit-4.11.jar"));
    FuncallExpression target = externalFile.findRule("target");
    StringLiteral label =
        PsiUtils.findFirstChildOfClassRecursive(
            target.getKeywordArgument("jars"), StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(jarFile);
  }

  @Test
  public void testReferenceToWorkspaceFileContents() {
    BuildFile workspaceFile =
        createBuildFile(
            new WorkspacePath("WORKSPACE"),
            "maven_jar(",
            "    name = 'w3c_css_sac',",
            "    artifact = 'org.w3c.css:sac:1.3',",
            "    sha1 = 'cdb2dcb4e22b83d6b32b93095f644c3462739e82',",
            ")");
    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/pkg/BUILD"),
            "rule(",
            "    name = 'other',",
            "    dep = '@w3c_css_sac//jar'",
            ")");
    FuncallExpression target = workspaceFile.findRule("w3c_css_sac");
    assertThat(target).isNotNull();

    FuncallExpression other = referencingFile.findRule("other");
    StringLiteral label =
        PsiUtils.findFirstChildOfClassRecursive(
            other.getKeywordArgument("dep"), StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(target);
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
