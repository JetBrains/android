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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiReference;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that funcall references and load statement contents are correctly resolved. */
@RunWith(JUnit4.class)
public class LoadedSkylarkExtensionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testStandardLoadReference() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google:build_defs.bzl\",",
            "\"function\"",
            ")");

    LoadStatement load = buildFile.firstChildOfClass(LoadStatement.class);
    assertThat(load.getImportPsiElement().getReferencedElement()).isEqualTo(extFile);

    FunctionStatement function = extFile.firstChildOfClass(FunctionStatement.class);
    assertThat(function).isNotNull();

    assertThat(load.getImportedSymbolElements()).hasLength(1);
    assertThat(load.getImportedSymbolElements()[0].getLoadedElement()).isEqualTo(function);
  }

  // TODO: If we want to support this deprecated format,
  // we should start by relaxing the ":" requirement in Label
  //public void testDeprecatedImportLabelFormat() {
  //  BuildFile extFile = createBuildFile(
  //    "java/com/google/build_defs.bzl",
  //    "def function(name, deps)");
  //
  //  BuildFile buildFile = createBuildFile(
  //    "java/com/google/tools/BUILD",
  //    "load(",
  //    "\"/java/com/google/build_defs.bzl\",",
  //    "\"function\"",
  //    ")");
  //
  //  LoadStatement load = buildFile.firstChildOfClass(LoadStatement.class);
  //  assertThat(load.getImportPsiElement().getReferencedElement()).isEqualTo(extFile);
  //}

  @Test
  public void testPackageLocalImportLabelFormat() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/BUILD"),
            "load(",
            "\":build_defs.bzl\",",
            "\"function\"",
            ")");

    LoadStatement load = buildFile.firstChildOfClass(LoadStatement.class);
    assertThat(load.getImportPsiElement().getReferencedElement()).isEqualTo(extFile);
  }

  @Test
  public void testMultipleImportedFunctions() {
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"),
            "def fn1(name, deps)",
            "def fn2(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google:build_defs.bzl\",",
            "\"fn1\"",
            "\"fn2\"",
            ")");

    LoadStatement load = buildFile.firstChildOfClass(LoadStatement.class);
    assertThat(load.getImportPsiElement().getReferencedElement()).isEqualTo(extFile);

    FunctionStatement[] functions = extFile.childrenOfClass(FunctionStatement.class);
    assertThat(functions).hasLength(2);
    assertThat(load.getImportedFunctionReferences()).isEqualTo(functions);
  }

  @Test
  public void testLoadedSymbolReference() {
    BuildFile extFile =
        createBuildFile(new WorkspacePath("java/com/google/tools/build_defs.bzl"), "CONSTANT = 1");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"CONSTANT\"",
            ")",
            "NEW_CONSTANT = CONSTANT");

    TargetExpression target =
        PsiUtils.findFirstChildOfClassRecursive(extFile, TargetExpression.class);
    ReferenceExpression ref =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, ReferenceExpression.class);
    LoadedSymbol loadElement =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, LoadedSymbol.class);

    assertThat(target).isNotNull();
    assertThat(ref.getReferencedElement()).isEqualTo(target);
    assertThat(loadElement.getImport().getReferencedElement()).isEqualTo(target);

    assertThat(
            Arrays.stream(FindUsages.findAllReferences(target))
                .map(PsiReference::getElement)
                .collect(Collectors.toList()))
        .containsExactly(ref, loadElement.getImport());
  }

  @Test
  public void testOverridenBuiltInSymbolReference() {
    setBuiltInRuleNames("java_library");
    BuildFile extFile =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/build_defs.bzl"), "java_library = rule()");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"java_library\"",
            ")",
            "java_library(name = 'name')");

    TargetExpression target =
        PsiUtils.findFirstChildOfClassRecursive(extFile, TargetExpression.class);
    FuncallExpression funcall = buildFile.firstChildOfClass(FuncallExpression.class);
    LoadedSymbol loadElement =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, LoadedSymbol.class);

    assertThat(target).isNotNull();
    assertThat(funcall.getReferencedElement()).isEqualTo(target);
    assertThat(loadElement.getImport().getReferencedElement()).isEqualTo(target);

    assertThat(
            Arrays.stream(FindUsages.findAllReferences(target))
                .map(PsiReference::getElement)
                .collect(Collectors.toList()))
        .containsExactly(funcall, loadElement.getImport());
  }

  @Test
  public void testFuncallReference() {
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

    FunctionStatement function = extFile.firstChildOfClass(FunctionStatement.class);
    FuncallExpression funcall = buildFile.firstChildOfClass(FuncallExpression.class);

    assertThat(function).isNotNull();
    assertThat(funcall.getReferencedElement()).isEqualTo(function);
  }

  @Test
  public void testAliasedFuncallReference() {
    createBuildFile(
        new WorkspacePath("java/com/google/tools/build_defs.bzl"), "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "load('//java/com/google/tools:build_defs.bzl', newName = 'function'),",
            "newName(name = \"name\", deps = []");

    LoadedSymbol loadedSymbol =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, LoadedSymbol.class);
    FuncallExpression funcall = buildFile.firstChildOfClass(FuncallExpression.class);

    assertThat(funcall.getReferencedElement()).isEqualTo(loadedSymbol.getVisibleElement());
  }

  // relative paths in skylark extensions which lie in subdirectories
  // are relative to the parent blaze package directory
  @Test
  public void testRelativePathInSubdirectory() {
    workspace.createFile(new WorkspacePath("java/com/google/BUILD"));
    BuildFile referencedFile =
        createBuildFile(
            new WorkspacePath("java/com/google/nonPackageSubdirectory/skylark.bzl"),
            "def function(): return");
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/nonPackageSubdirectory/other.bzl"),
            "load(" + "    ':nonPackageSubdirectory/skylark.bzl',",
            "    'function',",
            ")",
            "function()");

    FunctionStatement function = referencedFile.firstChildOfClass(FunctionStatement.class);
    FuncallExpression funcall = file.firstChildOfClass(FuncallExpression.class);

    assertThat(function).isNotNull();
    assertThat(funcall.getReferencedElement()).isEqualTo(function);
  }

  private void setBuiltInRuleNames(String... ruleNames) {
    ImmutableMap.Builder<String, RuleDefinition> rules = ImmutableMap.builder();
    for (String name : ruleNames) {
      rules.put(name, new RuleDefinition(name, ImmutableMap.of(), null));
    }
    MockBuildLanguageSpecProvider specProvider = new MockBuildLanguageSpecProvider();
    specProvider.setRules(rules.build());
    registerProjectService(BuildLanguageSpecProvider.class, specProvider);
    specProvider.setRules(rules.build());
  }

  private static class MockBuildLanguageSpecProvider implements BuildLanguageSpecProvider {

    BuildLanguageSpec languageSpec = new BuildLanguageSpec(ImmutableMap.of());

    void setRules(ImmutableMap<String, RuleDefinition> rules) {
      languageSpec = new BuildLanguageSpec(rules);
    }

    @Nullable
    @Override
    public BuildLanguageSpec getLanguageSpec() {
      return languageSpec;
    }
  }
}
