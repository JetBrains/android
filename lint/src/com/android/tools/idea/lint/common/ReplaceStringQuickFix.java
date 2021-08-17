/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lint.common;

import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_BEGINNING;
import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_END;

import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.core.ShortenReferences;
import org.jetbrains.kotlin.psi.KtElement;

/**
 * Generic lint quickfix which replaces text somewhere in the range from [startElement,endElement] by matching
 * a regular expression and replacing the first group with a specified value. The regular expression can be null
 * in which case the entire text range is replaced (this is used where lint's error range corresponds exactly to
 * the portion we want to replace.)
 */
public class ReplaceStringQuickFix implements LintIdeQuickFix {
  private final String myName;
  private final String myFamilyName;
  @RegExp private final String myRegexp;
  private final String myNewValue;
  private boolean myShortenNames;
  private boolean myFormat;
  private SmartPsiFileRange myRange;
  private String myExpandedNewValue;
  private String mySelectPattern;

  /**
   * Creates a new lint quickfix which can replace string contents at the given PSI element
   *
   * @param name       the quickfix description, which is optional (if not specified, it will be Replace with X)
   * @param familyName the name to use for this fix <b>if</b> it is safe to apply along with all other fixes of the same family name
   * @param regexp     the regular expression, or {@link ReplaceString#INSERT_BEGINNING} or {@link ReplaceString#INSERT_END}
   * @param newValue   the value to write into the document
   */
  public ReplaceStringQuickFix(@Nullable String name,
                               @Nullable String familyName,
                               @Nullable @RegExp String regexp,
                               @NotNull String newValue) {
    myName = name;
    myFamilyName = familyName;
    myNewValue = newValue;
    if (regexp != null && regexp.indexOf('(') == -1 && !regexp.equals(INSERT_BEGINNING) && !regexp.equals(INSERT_END)) {
      regexp = "(" + Pattern.quote(regexp) + ")";
    }
    myRegexp = regexp;
  }

  /**
   * Sets whether the replace operation should attempt to shorten class names after the replacement
   */
  public ReplaceStringQuickFix setShortenNames(boolean shortenNames) {
    myShortenNames = shortenNames;
    return this;
  }

  /**
   * Sets whether the replace operation should attempt to shorten class names after the replacement
   */
  public ReplaceStringQuickFix setFormat(boolean format) {
    myFormat = format;
    return this;
  }

  /**
   * Sets a pattern to select; if it contains parentheses, group(1) will be selected.
   * To just set the caret, use an empty group.
   */
  public ReplaceStringQuickFix setSelectPattern(String selectPattern) {
    mySelectPattern = selectPattern;
    return this;
  }

  /**
   * Sets a range override to use when searching for replacement text
   */
  public void setRange(SmartPsiFileRange range) {
    myRange = range;
  }

  @NotNull
  @Override
  public String getName() {
    if (myName == null) {
      if (myNewValue.isEmpty()) {
        return "Delete";
      }
      return "Replace with " + myNewValue;
    }
    return myName;
  }

  @Nullable
  @Override
  public String getFamilyName() {
    return myFamilyName;
  }

  @Nullable
  protected String getNewValue() {
    return myExpandedNewValue != null ? myExpandedNewValue : myNewValue;
  }

  @SuppressWarnings("EmptyMethod")
  protected void editBefore(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    PsiFile file = startElement.getContainingFile();
    if (myRange != null) {
      file = myRange.getContainingFile();
      if (file == null) {
        return;
      }
    }
    Project project = startElement.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    if (document != null) {
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      editBefore(document);
      TextRange range = getRange(startElement, endElement, true);
      if (range != null) {
        String newValue = getNewValue();
        if (newValue == null) {
          newValue = "";
        }
        if (whitespaceOnly(newValue)) {
          // If we're replacing a text segment with just whitespace,
          // and the line begins and ends with whitespace after making
          // the adjustment, delete the whole line
          range = includeFullLineIfOnlySpace(document, range);
        }
        int startOffset = range.getStartOffset();
        int endOffset = range.getEndOffset();
        document.replaceString(startOffset, endOffset, newValue);
        endOffset = startOffset + newValue.length();
        editAfter(document);
        if (myShortenNames || myFormat) {
          documentManager.commitDocument(document);
          PsiElement element = file.findElementAt(startOffset);
          if (element == null) {
            return;
          }
          if (myShortenNames) {
            PsiElement end = file.findElementAt(endOffset);
            PsiElement parent = end != null ? PsiTreeUtil.findCommonParent(element.getParent(), end) : element.getParent();
            if (parent == null) {
              parent = element.getParent();
            }

            if (element.getLanguage() == JavaLanguage.INSTANCE) {
              parent = JavaCodeStyleManager.getInstance(project).shortenClassReferences(parent);
            }
            else if (element.getLanguage() == KotlinLanguage.INSTANCE && parent instanceof KtElement) {
              parent = ShortenReferences.DEFAULT.process((KtElement)parent);
            }
            else {
              parent = null;
            }
            if (myFormat && parent != null) {
              CodeStyleManager.getInstance(project).reformat(parent);
            }
            else {
              CodeStyleManager.getInstance(project).reformatRange(element, startOffset, endOffset);
            }
          }
          else if (myFormat) {
            CodeStyleManager.getInstance(project).reformatRange(element, startOffset, endOffset);
          }
        }

        if (mySelectPattern != null && context instanceof AndroidQuickfixContexts.EditorContext) {
          Pattern pattern = Pattern.compile(mySelectPattern);
          Matcher matcher = pattern.matcher(document.getText());
          if (matcher.find(startOffset)) {
            int selectStart;
            int selectEnd;
            if (matcher.groupCount() > 0) {
              selectStart = matcher.start(1);
              selectEnd = matcher.end(1);
            }
            else {
              selectStart = matcher.start();
              selectEnd = matcher.end();
            }
            Editor editor = ((AndroidQuickfixContexts.EditorContext)context).getEditor();
            editor.getSelectionModel().setSelection(selectStart, selectEnd);
          }
        }
      }
    }
  }

  private static boolean whitespaceOnly(@NotNull String text) {
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  private static TextRange includeFullLineIfOnlySpace(@NotNull Document document, @NotNull TextRange range) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();

    // See if there's nothing left on the line; if so, delete the whole line
    int lineStart = DocumentUtil.getLineStartOffset(startOffset, document);
    int lineEnd = DocumentUtil.getLineEndOffset(startOffset, document);
    if (lineEnd < endOffset) {
      return range;
    }

    String prefix = document.getText(new TextRange(lineStart, startOffset));
    String suffix = document.getText(new TextRange(endOffset, lineEnd));

    if (whitespaceOnly(prefix) && whitespaceOnly(suffix)) {
      return new TextRange(lineStart, lineEnd + 1);
    }
    else {
      return range;
    }
  }

  @Nullable
  private TextRange getRange(PsiElement startElement, PsiElement endElement, boolean computeReplacement) {
    if (!startElement.isValid() || !endElement.isValid()) {
      return null;
    }
    int start;
    int end;
    if (myRange != null) {
      PsiFile file = myRange.getContainingFile();
      if (file == null) {
        return null;
      }
      Segment segment = myRange.getRange();
      if (segment != null) {
        start = segment.getStartOffset();
        end = segment.getEndOffset();
        if (myRegexp != null && !INSERT_BEGINNING.equals(myRegexp) && !INSERT_END.equals(myRegexp)) {
          startElement = file.findElementAt(start);
          endElement = file.findElementAt(end);
          if (startElement == null || endElement == null) {
            return null;
          }
        }
      } else {
        return null;
      }
    } else {
      start = startElement.getTextOffset();
      end = endElement.getTextOffset() + endElement.getTextLength();
    }
    if (myRegexp != null) {
      if (INSERT_BEGINNING.equals(myRegexp)) {
        return new TextRange(start, start);
      }
      else if (INSERT_END.equals(myRegexp)) {
        return new TextRange(end, end);
      }
      try {
        Pattern pattern = Pattern.compile(myRegexp, Pattern.MULTILINE);
        String sequence;
        PsiElement parent = PsiTreeUtil.findCommonParent(startElement, endElement);
        if (parent != null && parent.getTextRange().containsRange(start, end)) {
          TextRange parentRange = parent.getTextRange();
          int offset = parentRange.getStartOffset();
          sequence = parent.getText().substring(start - offset, end - offset);
        }
        else {
          String text = startElement.getContainingFile().getText();
          sequence = text.substring(start, end);
        }
        Matcher matcher = pattern.matcher(sequence);
        if (matcher.find()) {

          end = start;

          if (matcher.groupCount() > 0) {
            if (myRegexp.contains("target")) {
              try {
                start += matcher.start("target");
                end += matcher.end("target");
              }
              catch (IllegalArgumentException ignore) {
                // Occurrence of "target" not actually a named group
                start += matcher.start(1);
                end += matcher.end(1);
              }
            }
            else {
              start += matcher.start(1);
              end += matcher.end(1);
            }
          }
          else {
            start += matcher.start();
            end += matcher.end();
          }

          if (computeReplacement && myExpandedNewValue == null) {
            myExpandedNewValue = ReplaceString.expandBackReferences(myNewValue, matcher);
          }
        }
        else {
          return null;
        }
      }
      catch (Exception e) {
        // Invalid regexp
        Logger.getInstance(ReplaceStringQuickFix.class).warn("Invalid regular expression " + myRegexp);
        return null;
      }
    }
    return new TextRange(start, end);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return getRange(startElement, endElement, false) != null;
  }
}
