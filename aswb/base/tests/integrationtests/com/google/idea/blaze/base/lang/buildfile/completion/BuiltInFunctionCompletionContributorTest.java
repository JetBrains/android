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
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute.Discriminator;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.AttributeDefinition;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.testing.ServiceHelper;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link BuiltInFunctionCompletionContributor} */
@RunWith(JUnit4.class)
public class BuiltInFunctionCompletionContributorTest extends BuildFileIntegrationTestCase {

  private MockBuildLanguageSpecProvider specProvider;

  @Before
  public final void before() {
    specProvider = new MockBuildLanguageSpecProvider();
    registerProjectService(BuildLanguageSpecProvider.class, specProvider);
  }

  @Test
  public void testSimpleTopLevelCompletion() throws Throwable {
    setRules("rule_one", "rule_two");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 0);

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("rule_one", "rule_two");
    assertFileContents(file, "");
  }

  @Test
  public void testUniqueTopLevelCompletion() throws Throwable {
    setRules("rule_one", "rule_two");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "rule_o");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "rule_o".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();
  }

  @Test
  public void testSkylarkNativeCompletion() throws Throwable {
    setRules("rule_one", "rule_two");

    BuildFile file =
        createBuildFile(new WorkspacePath("build_defs.bzl"), "def function():", "  native.rule_o");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 1, "  native.rule_o".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "def function():", "  native.rule_one()");
    editorTest.assertCaretPosition(editor, 1, "  native.rule_one(".length());
  }

  @Test
  public void testNoCompletionInsideRule() throws Throwable {
    ServiceHelper.unregisterLanguageExtensionPoint(
        "com.intellij.completion.contributor",
        BuiltInSymbolCompletionContributor.class,
        getTestRootDisposable());

    setRules("rule_one", "rule_two");

    String[] contents = {"rule_one(", "    name = \"lib\"", ""};

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), contents);

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 2, 0);

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
    assertFileContents(file, contents);
  }

  @Test
  public void testNoCompletionInComment() throws Throwable {
    setRules("rule_one", "rule_two");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "#rule");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "#rule".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testNoCompletionAfterInteger() throws Throwable {
    setRules("rule_one", "rule_two");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "1");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "1".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testGlobalFunctions() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "licen");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 5);

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "licenses()");
    editorTest.assertCaretPosition(editor, 0, "licenses(".length());
  }

  @Test
  public void testMandatoryAttributesTemplate() {
    setRuleWithAttributes(
        "rule_with_attrs",
        ImmutableMap.of(
            "name", new AttributeDefinition("name", Discriminator.STRING, true, null, null),
            "a", new AttributeDefinition("a", Discriminator.BOOLEAN, true, null, null),
            "b", new AttributeDefinition("b", Discriminator.STRING_LIST, true, null, null),
            "c", new AttributeDefinition("c", Discriminator.STRING_DICT, true, null, null),
            "opt", new AttributeDefinition("opt", Discriminator.INTEGER, false, null, null)));

    PsiFile file = testFixture.configureByText("BUILD", "rule_with<caret>");
    editorTest.completeIfUnique();

    assertFileContents(
        file,
        "rule_with_attrs(",
        "    name = \"\",",
        "    a = ,",
        "    b = [],",
        "    c = {},",
        ")");
  }

  @Test
  public void testSrcsDepsTemplate() {
    setRuleWithAttributes(
        "haskell_binary",
        ImmutableMap.of(
            "name", new AttributeDefinition("name", Discriminator.STRING, true, null, null),
            "srcs", new AttributeDefinition("srcs", Discriminator.STRING_LIST, false, null, null),
            "deps", new AttributeDefinition("deps", Discriminator.STRING_LIST, false, null, null)));

    PsiFile file = testFixture.configureByText("BUILD", "haskell_bin<caret>");
    editorTest.completeIfUnique();

    assertFileContents(
        file, "haskell_binary(", "    name = \"\",", "    srcs = [],", "    deps = [],", ")");
  }

  @Test
  public void testParenthesesCompletionWhenTemplatesNotApplicable() {
    setRules("abc_rule");

    PsiFile file = testFixture.configureByText("BUILD", "abc_<caret>");
    editorTest.completeIfUnique();

    assertFileContents(file, "abc_rule()");
  }

  private void setRules(String... ruleNames) {
    ImmutableMap.Builder<String, RuleDefinition> rules = ImmutableMap.builder();
    for (String name : ruleNames) {
      rules.put(name, new RuleDefinition(name, ImmutableMap.of(), null));
    }
    specProvider.setRules(rules.build());
  }

  private void setRuleWithAttributes(String name, ImmutableMap<String, AttributeDefinition> attrs) {
    specProvider.setRules(ImmutableMap.of(name, new RuleDefinition(name, attrs, null)));
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
