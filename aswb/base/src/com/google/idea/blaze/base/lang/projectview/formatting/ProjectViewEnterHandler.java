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
package com.google.idea.blaze.base.lang.projectview.formatting;

import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiFile;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;

/** Inserts indents as appropriate when enter is pressed. */
public class ProjectViewEnterHandler extends EnterHandlerDelegateAdapter {

  @Override
  public Result preprocessEnter(
      PsiFile file,
      Editor editor,
      Ref<Integer> caretOffset,
      Ref<Integer> caretAdvance,
      DataContext dataContext,
      EditorActionHandler originalHandler) {
    int offset = caretOffset.get();
    if (editor instanceof EditorWindow) {
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      editor = InjectedLanguageUtil.getTopLevelEditor(editor);
      offset = editor.getCaretModel().getOffset();
    }
    if (!isApplicable(file, dataContext) || !insertIndent(file, offset)) {
      return Result.Continue;
    }
    int indent = SectionParser.INDENT;

    editor.getCaretModel().moveToOffset(offset);
    Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);

    originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    LogicalPosition position = editor.getCaretModel().getLogicalPosition();
    if (position.column < indent) {
      String spaces = StringUtil.repeatSymbol(' ', indent - position.column);
      doc.insertString(editor.getCaretModel().getOffset(), spaces);
    }
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.line, indent));
    return Result.Stop;
  }

  private static boolean isApplicable(PsiFile file, DataContext dataContext) {
    if (!(file instanceof ProjectViewPsiFile)) {
      return false;
    }
    Boolean isSplitLine =
        DataManager.getInstance().loadFromDataContext(dataContext, SplitLineAction.SPLIT_LINE_KEY);
    if (isSplitLine != null) {
      return false;
    }
    return true;
  }

  private static boolean insertIndent(PsiFile file, int offset) {
    if (offset == 0) {
      return false;
    }
    PsiElement element = file.findElementAt(offset - 1);
    while (element != null && element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (element == null || element.getText() != ":") {
      return false;
    }
    ASTNode prev = element.getNode().getTreePrev();
    return prev != null && prev.getElementType() == ProjectViewTokenType.LIST_KEYWORD;
  }
}
