/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic lint quickfix which replaces text somewhere in the range from [startElement,endElement] by matching
 * a regular expression and replacing the first group with a specified value. The regular expression can be null
 * in which case the entire text range is replaced (this is used where lint's error range corresponds exactly to
 * the portion we want to replace.)
 */
class ReplaceStringQuickFix implements AndroidLintQuickFix {
  private final String myName;
  private final String myRegexp;
  private final String myNewValue;

  /**
   * Creates a new lint quickfix which can replace string contents at the given PSI element
   *
   * @param name the quickfix description, which is optional (if not specified, it will be Replace with X)
   * @param regexp the regular expression
   * @param newValue
   */
  ReplaceStringQuickFix(@Nullable String name, @Nullable String regexp, @NotNull String newValue) {
    myName = name;
    myNewValue = newValue;
    if (regexp != null && regexp.indexOf('(') == -1) {
      regexp = "(" + Pattern.quote(regexp) + ")";
    }
    myRegexp = regexp;
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
    return myNewValue;
  }

  protected void editBefore(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    Document document = FileDocumentManager.getInstance().getDocument(startElement.getContainingFile().getVirtualFile());
    String newValue = getNewValue();
    if (document != null && newValue != null) {
      editBefore(document);
      TextRange range = getRange(startElement, endElement);
      if (range != null) {
        document.replaceString(range.getStartOffset(), range.getEndOffset(), newValue);
        editAfter(document);
      }
    }
  }

  @Nullable
  private TextRange getRange(PsiElement startElement, PsiElement endElement) {
    if (!startElement.isValid() || !endElement.isValid()) {
      return null;
    }
    int start = startElement.getTextOffset();
    int end = endElement.getTextOffset() + endElement.getTextLength();
    if (myRegexp != null) {
      try {
        Pattern pattern = Pattern.compile(myRegexp, Pattern.MULTILINE);
        String text = startElement.getContainingFile().getText();
        String sequence = text.substring(start, end);
        Matcher matcher = pattern.matcher(sequence);
        if (matcher.find()) {
          end = start + matcher.end(1);
          start += matcher.start(1);
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
    return getRange(startElement, endElement) != null;
  }
}
