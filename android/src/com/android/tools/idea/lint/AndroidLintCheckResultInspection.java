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

import com.android.tools.lint.checks.SupportAnnotationDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintCheckResultInspection extends AndroidLintInspectionBase {
  public AndroidLintCheckResultInspection() {
    super(AndroidBundle.message("android.lint.inspections.check.result"), SupportAnnotationDetector.CHECK_RESULT);
  }

  @Override
  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    String suggest = LintFix.getData(fixData, String.class);
    if (suggest != null) {
      return new AndroidLintQuickFix[]{ new ReplaceCallFix(suggest) };
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }

  static class ReplaceCallFix extends DefaultLintQuickFix {
    private final String mySuggest;

    public ReplaceCallFix(@NotNull String suggest) {
      super(null);
      mySuggest = suggest;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return String.format("Call %1$s instead", getMethodName());
    }

    private String getMethodName() {
      assert mySuggest.startsWith("#");
      int start = 1;
      int parameters = mySuggest.indexOf('(', start);
      if (parameters == -1) {
        parameters = mySuggest.length();
      }
      return mySuggest.substring(start, parameters).trim();
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      if (!startElement.isValid()) {
        return;
      }
      PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(startElement, PsiMethodCallExpression.class, false);
      if (methodCall == null) {
        return;
      }
      final PsiFile file = methodCall.getContainingFile();
      if (file == null) {
        return;
      }

      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      if (document != null) {
        PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
        if (referenceNameElement != null) {
          TextRange range = referenceNameElement.getTextRange();
          if (range != null) {
            // Also need to insert a message parameter
            // Currently hardcoded for the check*Permission to enforce*Permission code path. It's
            // tricky to figure out in general how to map existing parameters to new
            // parameters. Consider using MethodSignatureInsertHandler.
            String name = getMethodName();
            if (name.startsWith("enforce") && methodExpression.getReferenceName() != null
                && methodExpression.getReferenceName().startsWith("check")) {
              PsiExpressionList argumentList = methodCall.getArgumentList();
              int offset = argumentList.getTextOffset() + argumentList.getTextLength() - 1;
              document.insertString(offset, ", \"TODO: message if thrown\"");
            }

            // Replace method call
            document.replaceString(range.getStartOffset(), range.getEndOffset(), name);
          }
        }
      }
    }
  }
}