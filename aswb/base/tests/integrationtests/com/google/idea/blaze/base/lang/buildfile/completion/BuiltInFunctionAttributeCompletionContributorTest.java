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
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
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
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuiltInFunctionAttributeCompletionContributor}. */
@RunWith(JUnit4.class)
public class BuiltInFunctionAttributeCompletionContributorTest
    extends BuildFileIntegrationTestCase {

  private MockBuildLanguageSpecProvider specProvider;

  @Before
  public final void before() {
    specProvider = new MockBuildLanguageSpecProvider();
    registerProjectService(BuildLanguageSpecProvider.class, specProvider);
  }

  @Test
  public void testSimpleCompletion() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "sh_binary(".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("name", "deps", "srcs", "data");
  }

  @Test
  public void testSimpleSingleCompletion() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(", "    nam");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 1, "    nam".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertFileContents(file, "sh_binary(", "    name");
  }

  @Test
  public void testNoCompletionInUnknownRule() throws Throwable {
    ServiceHelper.unregisterLanguageExtensionPoint(
        "com.intellij.completion.contributor",
        BuiltInSymbolCompletionContributor.class,
        getTestRootDisposable());
    ServiceHelper.unregisterLanguageExtensionPoint(
        "com.intellij.completion.contributor",
        BuiltInFunctionCompletionContributor.class,
        getTestRootDisposable());

    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "java_binary(");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "java_binary(".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNoCompletionInComment() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(#");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "sh_binary(#".length());
    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testNoCompletionAfterInteger() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(testonly = 1,");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "sh_binary(testonly = 1".length());
    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testDoNotShowExistingAttributesInAutocompleteSuggestions() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(name = 'bin', )");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "sh_binary(name = 'bin', ".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("deps", "srcs", "data");
    assertThat(completionItems).asList().doesNotContain("name");
  }

  @Test
  public void testCompletionAtEndOfElement() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file =
        createBuildFile(
            new WorkspacePath("BUILD"),
            "sh_binary(",
            "    nam = 'bin',",
            "    srcs = ['source.sh', 'other_source.sh'],",
            ")");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 1, "    nam".length());
    assertThat(editorTest.getCompletionItemsAsStrings()).isNull();
    assertFileContents(
        file,
        "sh_binary(",
        "    name = 'bin',",
        "    srcs = ['source.sh', 'other_source.sh'],",
        ")");
  }

  @Test
  public void testCompletionInSkylarkExtension() throws Throwable {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile(new WorkspacePath("skylark.bzl"), "native.sh_binary(");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "native.sh_binary(".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("name", "deps", "srcs", "data");
  }

  private void setRuleAndAttributes(String ruleName, String... attributes) {
    ImmutableMap.Builder<String, AttributeDefinition> map = ImmutableMap.builder();
    for (String attr : attributes) {
      map.put(
          attr,
          new AttributeDefinition(attr, Build.Attribute.Discriminator.UNKNOWN, false, null, null));
    }
    RuleDefinition rule = new RuleDefinition(ruleName, map.build(), null);
    specProvider.setRules(ImmutableMap.of(ruleName, rule));
  }

  private static class MockBuildLanguageSpecProvider implements BuildLanguageSpecProvider {

    BuildLanguageSpec languageSpec;

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
