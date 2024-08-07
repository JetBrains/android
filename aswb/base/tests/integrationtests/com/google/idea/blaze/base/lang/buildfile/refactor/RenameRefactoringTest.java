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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that BUILD file references are correctly updated when performing rename refactors. */
@RunWith(JUnit4.class)
public class RenameRefactoringTest extends BuildFileIntegrationTestCase {

  @Test
  public void testRenameJavaClass() {
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/foo/JavaClass.java"),
            "package com.google.foo;",
            "public class JavaClass {}");

    createBuildFile(
        new WorkspacePath("com/google/foo/BUILD"),
        "java_library(name = \"ref1\", srcs = [\"//com/google/foo:JavaClass.java\"])",
        "java_library(name = \"ref2\", srcs = [\"JavaClass.java\"])",
        "java_library(name = \"ref3\", srcs = [\":JavaClass.java\"])");

    List<StringLiteral> references =
        findAllReferencingElementsOfType(javaFile, StringLiteral.class);

    Set<String> oldStrings =
        references.stream().map(StringLiteral::getStringContents).collect(Collectors.toSet());

    assertThat(references).hasSize(3);

    testFixture.renameElement(javaFile, "NewName.java");

    Set<String> newStrings =
        references.stream().map(StringLiteral::getStringContents).collect(Collectors.toSet());

    Set<String> expectedNewStrings =
        oldStrings.stream()
            .map((s) -> s.replaceAll("JavaClass", "NewName"))
            .collect(Collectors.toSet());

    assertThat(newStrings).containsExactlyElementsIn(expectedNewStrings);
  }

  @Test
  public void testRenameRule() {
    BuildFile fooPackage =
        createBuildFile(
            new WorkspacePath("com/google/foo/BUILD"),
            "rule_type(name = \"target\")",
            "java_library(name = \"local_ref\", srcs = [\":target\"])");

    BuildFile barPackage =
        createBuildFile(
            new WorkspacePath("com/google/test/bar/BUILD"),
            "rule_type(name = \"ref\", arg = \"//com/google/foo:target\")",
            "top_level_ref = \"//com/google/foo:target\"");

    FuncallExpression targetRule =
        PsiUtils.findFirstChildOfClassRecursive(fooPackage, FuncallExpression.class);
    testFixture.renameElement(targetRule, "newTargetName");

    assertFileContents(
        fooPackage,
        "rule_type(name = \"newTargetName\")",
        "java_library(name = \"local_ref\", srcs = [\":newTargetName\"])");

    assertFileContents(
        barPackage,
        "rule_type(name = \"ref\", arg = \"//com/google/foo:newTargetName\")",
        "top_level_ref = \"//com/google/foo:newTargetName\"");
  }

  @Test
  public void testTargetRenameValidation() {
    BuildFile file =
        createBuildFile(new WorkspacePath("com/google/foo/BUILD"), "rule_type(name = \"target\")");
    FuncallExpression target =
        PsiUtils.findFirstChildOfClassRecursive(file, FuncallExpression.class);

    assertThat(RenameUtil.isValidName(getProject(), target, "name-with_allowed,meta=chars+-./@~"))
        .isTrue();
    assertThat(RenameUtil.isValidName(getProject(), target, "name:withColon")).isFalse();
    assertThat(RenameUtil.isValidName(getProject(), target, "/start-with-slash")).isFalse();
    assertThat(RenameUtil.isValidName(getProject(), target, "up-level-ref/../etc")).isFalse();
  }

  @Test
  public void testFunctionRenameValidation() {
    BuildFile file =
        createBuildFile(new WorkspacePath("com/google/foo/BUILD"), "def fn_name():", "  return");
    FunctionStatement fn = PsiUtils.findFirstChildOfClassRecursive(file, FunctionStatement.class);

    assertThat(RenameUtil.isValidName(getProject(), fn, "name-with-dash")).isFalse();
    assertThat(RenameUtil.isValidName(getProject(), fn, "name:withColon")).isFalse();
    assertThat(RenameUtil.isValidName(getProject(), fn, "return")).isFalse();
    assertThat(RenameUtil.isValidName(getProject(), fn, "name_with_underscore")).isTrue();
  }

  @Test
  public void testRenameSkylarkExtension() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google:tools/build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    testFixture.renameElement(extFile, "skylark.bzl");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google:tools/skylark.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", deps = []");
  }

  @Test
  public void testRenameLoadedFunction() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    FunctionStatement fn = extFile.findChildByClass(FunctionStatement.class);
    testFixture.renameElement(fn, "action");

    assertFileContents(extFile, "def action(name, deps)");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google/tools:build_defs.bzl\",",
        "\"action\"",
        ")",
        "action(name = \"name\", deps = []");
  }

  @Test
  public void testRenameLocalVariable() {
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "a = 1", "c = a");

    TargetExpression target = PsiUtils.findFirstChildOfClassRecursive(file, TargetExpression.class);
    assertThat(target.getText()).isEqualTo("a");

    testFixture.renameElement(target, "b");

    assertFileContents(file, "b = 1", "c = b");
  }

  // all references, including path fragments in labels, should be renamed.
  @Test
  public void testRenameDirectory() {
    createBuildFile(new WorkspacePath("java/com/baz/BUILD"));
    createBuildFile(new WorkspacePath("java/com/google/tools/BUILD"));
    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = [\"//java/com/baz:target\"]");

    renameDirectory(new WorkspacePath("java/com"), new WorkspacePath("java/alt"));

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/alt/google/tools:build_defs.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", deps = [\"//java/alt/baz:target\"]");
  }

  @Test
  public void testRenameFunctionParameter() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    FunctionStatement fn = extFile.findChildByClass(FunctionStatement.class);
    Parameter param = fn.getParameterList().findParameterByName("deps");
    testFixture.renameElement(param, "exports");

    assertFileContents(extFile, "def function(name, exports)");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google/tools:build_defs.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", exports = []");
  }

  @Test
  public void testRenameExternalWorkspaceTarget() {
    BuildFile workspaceFile =
        createBuildFile(
            new WorkspacePath("WORKSPACE"),
            "maven_jar(",
            "    name = \"javax\",",
            "    artifact = \"javax.inject:javax.inject:1\",",
            "    sha1 = \"6975da39a7040257bd51d21a231b76c915872d38\",",
            ")");
    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/foo/BUILD"),
            "java_library(name = \"javax_inject\", exports = [\"@javax//jar\"])");

    FuncallExpression targetRule =
        PsiUtils.findFirstChildOfClassRecursive(workspaceFile, FuncallExpression.class);
    testFixture.renameElement(targetRule, "v2_lib");

    assertFileContents(
        workspaceFile,
        "maven_jar(",
        "    name = \"v2_lib\",",
        "    artifact = \"javax.inject:javax.inject:1\",",
        "    sha1 = \"6975da39a7040257bd51d21a231b76c915872d38\",",
        ")");

    assertFileContents(
        referencingFile, "java_library(name = \"javax_inject\", exports = [\"@v2_lib//jar\"])");
  }

  @Test
  public void testRenameSuggestionForBuildFile() {
    BuildFile buildFile = createBuildFile(new WorkspacePath("java/com/google/BUILD"));
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(buildFile);
    RenameDialog dialog = processor.createRenameDialog(getProject(), buildFile, buildFile, null);
    String[] suggestions = dialog.getSuggestedNames();
    assertThat(suggestions[0]).isEqualTo("BUILD");
  }

  @Test
  public void testRenameSuggestionForSkylarkFile() {
    BuildFile buildFile =
        createBuildFile(new WorkspacePath("java/com/google/tools/build_defs.bzl"));
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(buildFile);
    RenameDialog dialog = processor.createRenameDialog(getProject(), buildFile, buildFile, null);
    String[] suggestions = dialog.getSuggestedNames();
    assertThat(suggestions[0]).isEqualTo("build_defs.bzl");
  }

  private static <T> List<T> findAllReferencingElementsOfType(
      PsiElement target, Class<T> referenceType) {
    return Arrays.stream(FindUsages.findAllReferences(target))
        .map(PsiReference::getElement)
        .filter(referenceType::isInstance)
        .map(referenceType::cast)
        .collect(Collectors.toList());
  }

  private PsiDirectory renameDirectory(WorkspacePath oldPath, WorkspacePath newPath) {
    try {
      VirtualFile original = fileSystem.findFile(workspaceRoot.fileForPath(oldPath).getPath());
      PsiDirectory originalPsi = PsiManager.getInstance(getProject()).findDirectory(original);
      assertThat(originalPsi).isNotNull();

      VirtualFile destination =
          fileSystem.findOrCreateDirectory(workspaceRoot.fileForPath(newPath).getPath());
      PsiDirectory destPsi = PsiManager.getInstance(getProject()).findDirectory(destination);
      assertThat(destPsi).isNotNull();

      new MoveDirectoryWithClassesProcessor(
              getProject(), new PsiDirectory[] {originalPsi}, destPsi, true, true, false, null)
          .run();
      return destPsi;

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
