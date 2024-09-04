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
package com.google.idea.blaze.base;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EditorTestUtil.CaretAndSelectionState;
import com.intellij.testFramework.EditorTestUtil.CaretInfo;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.util.Arrays;
import javax.annotation.Nullable;

/** Helper methods for editor tests. */
public class EditorTestHelper {
  private final Project project;
  private final CodeInsightTestFixture testFixture;

  public EditorTestHelper(Project project, CodeInsightTestFixture testFixture) {
    this.project = project;
    this.testFixture = testFixture;
  }

  public Editor openFileInEditor(PsiFile file) throws Throwable {
    return openFileInEditor(file.getVirtualFile());
  }

  public Editor openFileInEditor(VirtualFile file) throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> testFixture.openFileInEditor(file));
    return testFixture.getEditor();
  }

  /** @return null if the only item was auto-completed */
  @Nullable
  public String[] getCompletionItemsAsStrings() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return null;
    }
    return Arrays.stream(completionItems)
        .map(LookupElement::getLookupString)
        .toArray(String[]::new);
  }

  /** @return null if the only item was auto-completed */
  @Nullable
  public String[] getCompletionItemsAsSuggestionStrings() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return null;
    }
    LookupElementPresentation presentation = new LookupElementPresentation();
    String[] strings = new String[completionItems.length];
    for (int i = 0; i < strings.length; i++) {
      completionItems[i].renderElement(presentation);
      strings[i] = presentation.getItemText();
    }
    return strings;
  }

  /** @return true if a LookupItem was inserted. */
  public boolean completeIfUnique() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return true;
    }
    if (completionItems.length != 1) {
      return false;
    }
    testFixture.getLookup().setCurrentItem(completionItems[0]);
    testFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    return true;
  }

  /** Simulates a user typing action, at current caret position of file. */
  public void performTypingAction(PsiFile file, char typedChar) throws Throwable {
    performTypingAction(openFileInEditor(file.getVirtualFile()), typedChar);
  }

  /** Simulates a user typing action, at current caret position of document. */
  public void performTypingAction(Editor editor, char typedChar) {
    EditorTestUtil.performTypingAction(editor, typedChar);
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }

  /**
   * Clicks the specified button in current document at the current caret position
   *
   * @param action which button to click (see {@link IdeActions})
   */
  public final void pressButton(final String action) {
    CommandProcessor.getInstance()
        .executeCommand(project, () -> testFixture.performEditorAction(action), "", null);
  }

  public void setCaretPosition(Editor editor, int lineNumber, int columnNumber) throws Throwable {
    final CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EdtTestUtil.runInEdtAndWait(
        () ->
            EditorTestUtil.setCaretsAndSelection(
                editor, new CaretAndSelectionState(ImmutableList.of(info), null)));
  }

  public void assertCaretPosition(Editor editor, int lineNumber, int columnNumber) {
    CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EditorTestUtil.verifyCaretAndSelectionState(
        editor, new CaretAndSelectionState(ImmutableList.of(info), null));
  }

  public void replaceStringContents(StringLiteral string, String newStringContents) {
    Runnable renameOp =
        () -> {
          ASTNode node = string.getNode();
          node.replaceChild(
              node.getFirstChildNode(),
              PsiUtils.createNewLabel(string.getProject(), newStringContents));
        };
    ApplicationManager.getApplication()
        .runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(renameOp));
  }
}
