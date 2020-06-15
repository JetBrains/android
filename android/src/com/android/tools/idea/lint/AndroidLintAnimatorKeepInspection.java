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

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.lint.checks.ObjectAnimatorDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.psi.KtNamedFunction;

public class AndroidLintAnimatorKeepInspection extends AndroidLintInspectionBase {
  public AndroidLintAnimatorKeepInspection() {
    super(AndroidBundle.message("android.lint.inspections.animator.keep"), ObjectAnimatorDetector.MISSING_KEEP);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    PsiMethod method = LintFix.getData(fixData, PsiMethod.class);
    if (method instanceof KtLightMethod) {
      KtNamedFunction fun = PsiTreeUtil.getParentOfType(startElement, KtNamedFunction.class, false);
      if (fun == null || !Objects.equals(fun.getName(), method.getName())) {
        return super.getQuickFixes(startElement, endElement, message, fixData);
      }
    }
    else if (method == null || !method.equals(PsiTreeUtil.getParentOfType(startElement, PsiMethod.class, false))) {
      return super.getQuickFixes(startElement, endElement, message, fixData);
    }
    return new LintIdeQuickFix[]{new AddKeepAnnotationFix()};
  }
}
