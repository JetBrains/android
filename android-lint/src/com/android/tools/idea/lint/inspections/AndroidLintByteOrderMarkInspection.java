/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.lint.checks.ByteOrderMarkDetector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AndroidLintByteOrderMarkInspection extends AndroidLintInspectionBase {
  public AndroidLintByteOrderMarkInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.byte.order.mark"), ByteOrderMarkDetector.BOM);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new LintIdeQuickFix[]{
      new DefaultLintQuickFix("Remove byte order marks", true) {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          PsiFile file = startElement.getContainingFile();
          Document document = context.getDocument(file);
          if (document != null) {
            String text = document.getText();
            for (int i = text.length() - 1; i >= 0; i--) {
              char c = text.charAt(i);
              if (c == '\uFEFF') {
                document.deleteString(i, i + 1);
              }
            }
          }
        }
      }
    };
  }
}
