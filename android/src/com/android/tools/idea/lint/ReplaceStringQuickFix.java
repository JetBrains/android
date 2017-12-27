/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.tools.lint.detector.api.LintFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.RegEx;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic lint quickfix which replaces text somewhere in the range from [startElement,endElement] by matching
 * a regular expression and replacing the first group with a specified value. The regular expression can be null
 * in which case the entire text range is replaced (this is used where lint's error range corresponds exactly to
 * the portion we want to replace.)
 */
public class ReplaceStringQuickFix implements AndroidLintQuickFix {
  private final String myName;
  @RegEx private final String myRegexp;
  private final String myNewValue;
  private boolean myShortenNames;
  private boolean myFormat;
  private SmartPsiFileRange myRange;
  private String myExpandedNewValue;

  /**
   * Creates a new lint quickfix which can replace string contents at the given PSI element
   *
   * @param name the quickfix description, which is optional (if not specified, it will be Replace with X)
   * @param regexp the regular expression
   * @param newValue
   */
  public ReplaceStringQuickFix(@Nullable String name, @Nullable @RegEx String regexp, @NotNull String newValue) {
    myName = name;
    myNewValue = newValue;
    if (regexp != null && regexp.indexOf('(') == -1) {
      regexp = "(" + Pattern.quote(regexp) + ")";
    }
    myRegexp = regexp;
  }

  /** Sets whether the replace operation should attempt to shorten class names after the replacement */
  public ReplaceStringQuickFix setShortenNames(boolean shortenNames) {
    myShortenNames = shortenNames;
    return this;
  }

  /** Sets whether the replace operation should attempt to shorten class names after the replacement */
  public ReplaceStringQuickFix setFormat(boolean format) {
    myFormat = format;
    return this;
  }

  /** Sets a range override to use when searching for replacement text */
  public void setRange(SmartPsiFileRange range) {
    myRange = range;
  }

  @NotNull
  @Override
  public String getName() {
    if (myName == null) {
      return "Replace with " + myNewValue;
    }
    return myName;
  }

  @Nullable
  protected String getNewValue() {
    return myExpandedNewValue != null ? myExpandedNewValue : myNewValue;
  }

  protected void editBefore(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    PsiFile file = startElement.getContainingFile();
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    if (document != null) {
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
          Project project = startElement.getProject();
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
          documentManager.commitDocument(document);

          PsiElement element = file.findElementAt(startOffset);
          if (element == null) {
            return;
          }
          if (myShortenNames && element.getLanguage() == JavaLanguage.INSTANCE) {
            PsiElement end = file.findElementAt(endOffset);
            PsiElement parent = end != null ? PsiTreeUtil.findCommonParent(element.getParent(), end) : element.getParent();
            if (parent == null) {
              parent = element.getParent();
            }

            parent = JavaCodeStyleManager.getInstance(project).shortenClassReferences(parent);
            if (myFormat) {
              CodeStyleManager.getInstance(project).reformat(parent);
            }
          } else if (myFormat) {
            CodeStyleManager.getInstance(project).reformatRange(element, startOffset, endOffset);
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

    String prefix = document.getText(new TextRange(lineStart, startOffset));
    String suffix = document.getText(new TextRange(endOffset, lineEnd));

    if (whitespaceOnly(prefix) && whitespaceOnly(suffix)) {
      return new TextRange(lineStart, lineEnd + 1);
    } else {
      return range;
    }
  }

  @Nullable
  private TextRange getRange(PsiElement startElement, PsiElement endElement, boolean computeReplacement) {
    if (!startElement.isValid() || !endElement.isValid()) {
      return null;
    }
    int start = startElement.getTextOffset();
    int end = endElement.getTextOffset() + endElement.getTextLength();
    if (myRange != null) {
      Segment segment = myRange.getRange();
      if (segment != null) {
        start = segment.getStartOffset();
        end = segment.getEndOffset();
      }
    }
    if (myRegexp != null) {
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
              } catch (IllegalArgumentException ignore) {
                // Occurrence of "target" not actually a named group
                start += matcher.start(1);
                end += matcher.end(1);
              }
            } else {
              start += matcher.start(1);
              end += matcher.end(1);
            }
          } else {
            start += matcher.start();
            end += matcher.end();
          }

          if (computeReplacement && myExpandedNewValue == null) {
            myExpandedNewValue = LintFix.ReplaceString.expandBackReferences(myNewValue, matcher);
          }
        }
        else {
          return null;
        }
      } catch (Exception e) {
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
