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
package com.google.idea.blaze.base.lang.buildfile.editor;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildListType;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.lang.buildfile.psi.PassStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ReturnStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StatementListContainer;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.text.CharArrayUtil;
import javax.annotation.Nullable;

/**
 * Inserts indents as appropriate when enter is pressed.
 *
 * <p>This is a substitute for implementing a full FormattingModel for the BUILD language. If we
 * ever decide to do that, this code should be removed.
 *
 * <p>By now, there's a better way to implement custom indent handling: {@link
 * com.intellij.psi.codeStyle.lineIndent.LineIndentProvider}. If we need to rework this class in the
 * future, we should switch to this other API.
 */
public class BuildEnterHandler extends EnterHandlerDelegateAdapter {

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
    if (!isApplicable(file, dataContext)) {
      return Result.Continue;
    }

    // Previous enter handler's (e.g. EnterBetweenBracesHandler) can introduce a mismatch
    // between the editor's caret model and the offset we've been provided with.
    editor.getCaretModel().moveToOffset(offset);

    Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);

    CommonCodeStyleSettings settings =
        CodeStyle.getLanguageSettings(file, BuildFileLanguage.INSTANCE);
    Integer indent = determineIndent(file, editor, offset, settings);
    if (indent == null) {
      return Result.Continue;
    }

    removeTrailingWhitespace(doc, file, offset);
    originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    LogicalPosition position = editor.getCaretModel().getLogicalPosition();
    if (position.column == indent) {
      return Result.Stop;
    }
    if (position.column > indent) {
      // default enter handler has added too many spaces -- remove them
      int excess = position.column - indent;
      doc.deleteString(
          editor.getCaretModel().getOffset() - excess, editor.getCaretModel().getOffset());
    } else if (position.column < indent) {
      String spaces = StringUtil.repeatSymbol(' ', indent - position.column);
      doc.insertString(editor.getCaretModel().getOffset(), spaces);
    }
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.line, indent));
    return Result.Stop;
  }

  private static void removeTrailingWhitespace(Document doc, PsiFile file, int offset) {
    CharSequence chars = doc.getCharsSequence();
    int start = offset;
    while (offset < chars.length() && chars.charAt(offset) == ' ') {
      PsiElement element = file.findElementAt(offset);
      if (element == null || !(element instanceof PsiWhiteSpace)) {
        break;
      }
      offset++;
    }
    if (start != offset) {
      doc.deleteString(start, offset);
    }
  }

  private static boolean isApplicable(PsiFile file, DataContext dataContext) {
    if (!(file instanceof BuildFile)) {
      return false;
    }
    Boolean isSplitLine =
        DataManager.getInstance().loadFromDataContext(dataContext, SplitLineAction.SPLIT_LINE_KEY);
    return isSplitLine == null;
  }

  /**
   * Returns null if an appropriate indent cannot be found. In that case we do nothing, and pass it
   * along to the next EnterHandler.
   */
  @Nullable
  private static Integer determineIndent(
      PsiFile file, Editor editor, int offset, CommonCodeStyleSettings settings) {
    if (offset == 0) {
      return null;
    }
    Document doc = editor.getDocument();
    PsiElement element = getRelevantElement(file, doc, offset);
    PsiElement parent = element != null ? element.getParent() : null;
    if (parent == null) {
      return null;
    }

    IndentOptions indentOptions = settings.getIndentOptions();
    if (endsBlock(element)) {
      // current line indent subtract block indent
      return Math.max(
          0, getIndent(doc, element) - (indentOptions != null ? indentOptions.INDENT_SIZE : 0));
    }

    if (parent instanceof BuildListType) {
      BuildListType<?> list = (BuildListType<?>) parent;
      if (endsList(list, element) && element.getTextOffset() < offset) {
        return null;
      }
      int listOffset = list.getStartOffset();
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      LogicalPosition listStart = editor.offsetToLogicalPosition(listOffset);
      if (listStart.line != caretPosition.line) {
        // take the minimum of the current line's indent and the current caret position
        return indentOfLineUpToCaret(doc, caretPosition.line, offset);
      }
      BuildElement firstChild = ((BuildListType<?>) parent).getFirstElement();
      if (firstChild != null && firstChild.getNode().getStartOffset() < offset) {
        return getIndent(doc, firstChild);
      }
      return lineIndent(doc, listStart.line) + additionalIndent(parent, indentOptions);
    }
    if (parent instanceof StatementListContainer && afterColon(doc, offset)) {
      return getIndent(doc, parent) + additionalIndent(parent, indentOptions);
    }
    return null;
  }

  private static int additionalIndent(PsiElement parent, IndentOptions indentOptions) {
    if (parent instanceof ParameterList) {
      return indentOptions.DECLARATION_PARAMETER_INDENT;
    }
    return parent instanceof StatementListContainer
        ? indentOptions.INDENT_SIZE
        : indentOptions.CONTINUATION_INDENT_SIZE;
  }

  private static int lineIndent(Document doc, int line) {
    int startOffset = doc.getLineStartOffset(line);
    int indentOffset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
    return indentOffset - startOffset;
  }

  private static int getIndent(Document doc, PsiElement element) {
    int offset = element.getNode().getStartOffset();
    int lineNumber = doc.getLineNumber(offset);
    return offset - doc.getLineStartOffset(lineNumber);
  }

  private static int indentOfLineUpToCaret(Document doc, int line, int caretOffset) {
    int startOffset = doc.getLineStartOffset(line);
    int indentOffset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
    return Math.min(indentOffset, caretOffset) - startOffset;
  }

  private static boolean endsList(BuildListType<?> list, PsiElement element) {
    String text = element.getText();
    return text.length() == 1 && list.getEndChars().contains(text.charAt(0));
  }

  private static boolean endsBlock(PsiElement element) {
    return element instanceof ReturnStatement || element instanceof PassStatement;
  }

  private static PsiElement getBlockEndingParent(PsiElement element) {
    while (element != null && !(element instanceof PsiFileSystemItem)) {
      if (endsBlock(element)) {
        return element;
      }
      element = element.getParent();
    }
    return null;
  }

  @Nullable
  private static PsiElement getRelevantElement(PsiFile file, Document doc, int offset) {
    if (offset == 0) {
      return null;
    }
    if (offset == doc.getTextLength()) {
      offset--;
    }
    PsiElement element = file.findElementAt(offset);
    while (element != null && isWhiteSpace(element)) {
      element = PsiUtils.getPreviousNodeInTree(element);
    }
    PsiElement blockTerminator = getBlockEndingParent(element);
    if (blockTerminator != null
        && blockTerminator.getTextRange().getEndOffset() == element.getTextRange().getEndOffset()) {
      return blockTerminator;
    }
    while (element != null && skipElement(element, offset)) {
      element = element.getParent();
    }
    return element;
  }

  private static boolean isWhiteSpace(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return true;
    }
    return BuildToken.WHITESPACE_AND_NEWLINE.contains(element.getNode().getElementType());
  }

  private static boolean skipElement(PsiElement element, int offset) {
    PsiElement parent = element.getParent();
    if (parent == null || parent.getNode() == null || parent instanceof PsiFileSystemItem) {
      return false;
    }
    TextRange childRange = element.getNode().getTextRange();
    return childRange.equals(parent.getNode().getTextRange())
        || childRange.getStartOffset() == offset
            && (parent instanceof Argument || parent instanceof Parameter);
  }

  private static boolean afterColon(Document doc, int offset) {
    CharSequence text = doc.getCharsSequence();
    int previousOffset = CharArrayUtil.shiftBackward(text, offset - 1, " \t");
    return text.charAt(previousOffset) == ':';
  }
}
