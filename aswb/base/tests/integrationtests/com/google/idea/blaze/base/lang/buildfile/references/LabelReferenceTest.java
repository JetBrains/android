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
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that string literal references are correctly resolved. */
@RunWith(JUnit4.class)
public class LabelReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testExternalFileReference() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "exports_files([\"test.txt\", \"//java/com/google:plugin.xml\"])");

    PsiFile txtFile = workspace.createPsiFile(new WorkspacePath("java/com/google/test.txt"));
    PsiFile xmlFile = workspace.createPsiFile(new WorkspacePath("java/com/google/plugin.xml"));

    List<StringLiteral> strings =
        PsiUtils.findAllChildrenOfClassRecursive(file, StringLiteral.class);
    assertThat(strings).hasSize(2);
    assertThat(strings.get(0).getReferencedElement()).isEqualTo(txtFile);
    assertThat(strings.get(1).getReferencedElement()).isEqualTo(xmlFile);
  }

  @Test
  public void testLocalRuleReference() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = \"lib\")",
            "java_library(name = \"foo\", deps = [\":lib\"])",
            "java_library(name = \"bar\", deps = [\"//java/com/google:lib\"])");

    FuncallExpression lib = file.findRule("lib");
    FuncallExpression foo = file.findRule("foo");
    FuncallExpression bar = file.findRule("bar");

    assertThat(lib).isNotNull();

    StringLiteral label =
        PsiUtils.findFirstChildOfClassRecursive(
            foo.getKeywordArgument("deps"), StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(lib);

    label =
        PsiUtils.findFirstChildOfClassRecursive(
            bar.getKeywordArgument("deps"), StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(lib);
  }

  @Test
  public void testTargetInAnotherPackageResolves() {
    BuildFile targetFile =
        createBuildFile(new WorkspacePath("java/com/google/foo/BUILD"), "rule(name = \"target\")");

    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "rule(name = \"other\", dep = \"//java/com/google/foo:target\")");

    FuncallExpression target = targetFile.findRule("target");
    assertThat(target).isNotNull();

    Argument.Keyword depArgument = referencingFile.findRule("other").getKeywordArgument("dep");

    assertThat(depArgument.getValue().getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testRuleNameDoesntCrossPackageBoundaries() {
    BuildFile targetFile =
        createBuildFile(
            new WorkspacePath("java/com/google/pkg/subpkg/BUILD"), "rule(name = \"target\")");

    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/pkg/BUILD"),
            "rule(name = \"other\", dep = \":subpkg/target\")");

    Argument.Keyword depArgument = referencingFile.findRule("other").getKeywordArgument("dep");

    LabelReference ref = (LabelReference) depArgument.getValue().getReference();
    assertThat(ref.resolve()).isNull();

    editorTest.replaceStringContents(ref.getElement(), "//java/com/google/pkg/subpkg:target");
    assertThat(ref.resolve()).isNotNull();
    assertThat(ref.resolve()).isEqualTo(targetFile.findRule("target"));
  }

  @Test
  public void testLabelWithImplicitRuleName() {
    BuildFile targetFile =
        createBuildFile(new WorkspacePath("java/com/google/foo/BUILD"), "rule(name = \"foo\")");

    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "rule(name = \"other\", dep = \"//java/com/google/foo\")");

    FuncallExpression target = targetFile.findRule("foo");
    assertThat(target).isNotNull();

    Argument.Keyword depArgument = referencingFile.findRule("other").getKeywordArgument("dep");

    assertThat(depArgument.getValue().getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testAbsoluteLabelInSkylarkExtension() {
    BuildFile targetFile =
        createBuildFile(new WorkspacePath("java/com/google/foo/BUILD"), "rule(name = \"foo\")");

    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/foo/skylark.bzl"),
            "LIST = ['//java/com/google/foo:foo']");

    FuncallExpression target = targetFile.findRule("foo");
    assertThat(target).isNotNull();

    StringLiteral label =
        PsiUtils.findFirstChildOfClassRecursive(referencingFile, StringLiteral.class);
    assertThat(label.getReferencedElement()).isEqualTo(target);
  }

  @Test
  public void testRulePreferredOverFile() {
    BuildFile targetFile =
        createBuildFile(new WorkspacePath("java/com/foo/BUILD"), "java_library(name = 'lib')");

    workspace.createDirectory(new WorkspacePath("java/com/foo/lib"));

    BuildFile referencingFile =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "java_library(",
            "    name = 'bar',",
            "    src = glob(['**/*.java'])," + "    deps = ['//java/com/foo:lib'],",
            ")");

    FuncallExpression target = targetFile.findRule("lib");
    assertThat(target).isNotNull();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);

    PsiElement element = references[0].getElement();
    FuncallExpression rule = PsiUtils.getParentOfType(element, FuncallExpression.class, true);
    assertThat(rule.getName()).isEqualTo("bar");
    assertThat(rule.getContainingFile()).isEqualTo(referencingFile);
  }

  @Test
  public void testNoTargetFoundFromNameAttributeLabel() throws Throwable {
    // we want 'GoToDeclaration' to trigger 'find usages' from a name attribute, which won't happen
    // if it's pointing to a target element different from the 'find usages target'
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = \"lib\")",
            "java_library(name = \"foo\", deps = [\":lib\"])");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "java_library(name = \"li".length());

    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNull();
  }
}
