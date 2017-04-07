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

import com.android.tools.lint.checks.ApiDetector;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintObsoleteSdkIntInspection extends AndroidLintInspectionBase {
  public AndroidLintObsoleteSdkIntInspection() {
    super(AndroidBundle.message("android.lint.inspections.obsolete.sdk.int"), ApiDetector.OBSOLETE_SDK);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    Boolean constant = ApiDetector.getVersionCheckConstant(message, RAW);
    if (constant != null) {
      PsiBinaryExpression subExpression = PsiTreeUtil.getParentOfType(startElement, PsiBinaryExpression.class, false);
      if (subExpression != null) {
        return new AndroidLintQuickFix[]{
          new AndroidLintQuickFix.LocalFixWrappee(new SimplifyBooleanExpressionFix(subExpression, constant))
        };
      }
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
