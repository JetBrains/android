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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests code completion of rule target labels. */
@RunWith(JUnit4.class)
public class RuleTargetCompletionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testLocalTarget() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'test',",
            "    deps = [':']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 3, "    deps = [':".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).hasLength(1);
    assertThat(completionItems[0].toString()).isEqualTo("':lib'");
  }

  @Test
  public void testCustomRuleCompletion() throws Throwable {
    MockBuildLanguageSpecProvider specProvider = new MockBuildLanguageSpecProvider();
    setBuildLanguageSpecRules(specProvider, "java_library");
    registerProjectService(BuildLanguageSpecProvider.class, specProvider);

    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "custom_rule(name = 'lib')",
            "java_library(",
            "    name = 'test',",
            "    deps = [':']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 3, "    deps = [':".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).hasLength(1);
    assertThat(completionItems[0].toString()).isEqualTo("':lib'");
  }

  @Test
  public void testIgnoreContainingTarget() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(",
            "    name = 'lib',",
            "    deps = [':']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, "    deps = [':".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNotCodeCompletionInNameField() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'l'",
            ")");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, "    name = 'l".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNonLocalTarget() throws Throwable {
    createBuildFile(
        new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = 'foo_lib')");

    BuildFile bar =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "java_library(",
            "    name = 'bar_lib',",
            "    deps = '//java/com/google/foo:')");

    Editor editor = editorTest.openFileInEditor(bar);
    editorTest.setCaretPosition(editor, 2, "    deps = '//java/com/google/foo:".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsExactly("'//java/com/google/foo:foo_lib'");
  }

  @Test
  public void testNonLocalRulesNotCompletedWithoutColon() throws Throwable {
    createBuildFile(
        new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = 'foo_lib')");

    BuildFile bar =
        createBuildFile(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "java_library(",
            "    name = 'bar_lib',",
            "    deps = '//java/com/google/foo')");

    Editor editor = editorTest.openFileInEditor(bar);
    editorTest.setCaretPosition(editor, 2, "    deps = '//java/com/google/foo".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testPackageLocalRulesCompletedWithoutColon() throws Throwable {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'test',",
            "    deps = ['']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 3, "    deps = ['".length());

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(
        file,
        "java_library(name = 'lib')",
        "java_library(",
        "    name = 'test',",
        "    deps = ['lib']");
  }

  @Test
  public void testLocalPathIgnoredForNonLocalLabels() throws Throwable {
    createBuildFile(new WorkspacePath("java/BUILD"), "java_library(name = 'root_rule')");

    BuildFile otherPackage =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(",
            "java_library(name = 'other_rule')",
            "    name = 'lib',",
            "    deps = ['//java:']");

    Editor editor = editorTest.openFileInEditor(otherPackage);
    editorTest.setCaretPosition(editor, 3, "    deps = ['//java:".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().contains("'//java:root_rule'");
    assertThat(completionItems).asList().doesNotContain("'//java/com/google:other_rule'");
  }

  private static void setBuildLanguageSpecRules(
      MockBuildLanguageSpecProvider specProvider, String... ruleNames) {
    ImmutableMap.Builder<String, RuleDefinition> rules = ImmutableMap.builder();
    for (String name : ruleNames) {
      rules.put(name, new RuleDefinition(name, ImmutableMap.of(), null));
    }
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
