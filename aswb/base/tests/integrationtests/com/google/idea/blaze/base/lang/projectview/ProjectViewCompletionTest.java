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
package com.google.idea.blaze.base.lang.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.projectview.completion.ProjectViewKeywordCompletionContributor;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests auto-complete in project view files */
@RunWith(JUnit4.class)
public class ProjectViewCompletionTest extends ProjectViewIntegrationTestCase {

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText(".blazeproject", Joiner.on("\n").join(fileContents));
  }

  private void assertResult(String... resultingFileContents) {
    testFixture.getFile().getText();
    testFixture.checkResult(Joiner.on("\n").join(resultingFileContents));
  }

  @Test
  public void testSectionTypeKeywords() {
    setInput("<caret>");
    String[] keywords = editorTest.getCompletionItemsAsStrings();

    assertThat(keywords)
        .asList()
        .containsAllIn(
            Sections.getUndeprecatedParsers()
                .stream()
                .filter(ProjectViewKeywordCompletionContributor::handledSectionType)
                .map(SectionParser::getName)
                .collect(Collectors.toList()));
  }

  @Test
  public void testColonAndNewLineAndIndentInsertedAfterListSection() {
    setInput("sync_fla<caret>");
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertResult("sync_flags:", "  <caret>");
  }

  @Test
  public void testWhitespaceDividerInsertedAfterScalarSection() {
    setInput("impo<caret>");

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems[0].getLookupString()).isEqualTo("import");

    testFixture.getLookup().setCurrentItem(completionItems[0]);
    testFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

    assertResult("import <caret>");
  }

  @Test
  public void testColonDividerAndSpaceInsertedAfterScalarSection() {
    setInput("workspace_t<caret>");
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertResult("workspace_type: <caret>");
  }

  @Test
  public void testNoKeywordCompletionInListItem() {
    setInput("directories:", "  <caret>");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    if (completionItems == null) {
      Assert.fail("Spurious completion. New file contents: " + testFixture.getFile().getText());
    }
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNoKeywordCompletionAfterKeyword() {
    setInput("import <caret>");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    if (completionItems == null) {
      Assert.fail("Spurious completion. New file contents: " + testFixture.getFile().getText());
    }
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testWorkspaceTypeCompletion() {
    setInput("workspace_type: <caret>");

    String[] types = editorTest.getCompletionItemsAsStrings();

    assertThat(types)
        .asList()
        .containsAllIn(
            Arrays.stream(WorkspaceType.values())
                .map(WorkspaceType::getName)
                .collect(Collectors.toList()));
  }

  @Test
  public void testUniqueDirectoryCompleted() {
    setInput("import <caret>");

    workspace.createDirectory(new WorkspacePath("java"));

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertResult("import java<caret>");
  }

  @Test
  public void testUniqueMultiSegmentDirectoryCompleted() {
    setInput("import <caret>");

    workspace.createDirectory(new WorkspacePath("java/com/google"));

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertResult("import java/com/google<caret>");
  }

  @Test
  public void testNonDirectoriesIgnoredForDirectoryOnlySection() {
    setInput("directories:", "  <caret>");

    workspace.createDirectory(new WorkspacePath("java/com/google"));
    workspace.createFile(new WorkspacePath("java/IgnoredFile.java"));

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertResult("directories:", "  java/com/google<caret>");
  }

  @Test
  public void testNonDirectoriesIncludedForSectionsAcceptingFiles() {
    setInput("import java<caret>");

    workspace.createFile(new WorkspacePath("java/.blazeproject"));

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertResult("import java/.blazeproject<caret>");
  }

  @Test
  public void testMultipleDirectoryOptions() {
    workspace.createDirectory(new WorkspacePath("foo"));
    workspace.createDirectory(new WorkspacePath("bar"));
    workspace.createDirectory(new WorkspacePath("other"));
    workspace.createDirectory(new WorkspacePath("ostrich/foo"));
    workspace.createDirectory(new WorkspacePath("ostrich/fooz"));

    setInput("targets:", "  //o<caret>");

    String[] completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("other", "ostrich");

    editorTest.performTypingAction(testFixture.getEditor(), 's');

    completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertResult("targets:", "  //ostrich<caret>");
  }

  @Test
  public void testTargetCompletion() {
    workspace.createFile(new WorkspacePath("BUILD"), "java_library(name = 'lib')");

    setInput("targets:", "  //:<caret>");

    String[] completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).isNull();
    assertResult("targets:", "  //:lib<caret>");
  }
}
