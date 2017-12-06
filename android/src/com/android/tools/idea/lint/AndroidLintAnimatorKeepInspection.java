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

import com.android.tools.lint.checks.ObjectAnimatorDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.lint.checks.ObjectAnimatorDetector.KEEP_ANNOTATION;

public class AndroidLintAnimatorKeepInspection extends AndroidLintInspectionBase {
  public AndroidLintAnimatorKeepInspection() {
    super(AndroidBundle.message("android.lint.inspections.animator.keep"), ObjectAnimatorDetector.MISSING_KEEP);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    PsiMethod method = LintFix.getData(fixData, PsiMethod.class);
    if (method == null || !method.equals(PsiTreeUtil.getParentOfType(startElement, PsiMethod.class, false))) {
      return super.getQuickFixes(startElement, endElement, message, fixData);
    }
    return new AndroidLintQuickFix[]{
      new AndroidLintQuickFix() {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          PsiModifierListOwner container = PsiTreeUtil.getParentOfType(startElement, PsiModifierListOwner.class);
          if (container == null) {
            return;
          }

          final PsiModifierList modifierList = container.getModifierList();
          if (modifierList != null) {
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(container, KEEP_ANNOTATION.oldName());
            if (annotation == null) {
              annotation = AnnotationUtil.findAnnotation(container, KEEP_ANNOTATION.newName());
            }
            if (annotation == null) {
              Project project = startElement.getProject();
              new AddAnnotationFix(KEEP_ANNOTATION.defaultName(), container).invoke(project, null, container.getContainingFile());
            }
          }
        }

        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          return true;
        }

        @NotNull
        @Override
        public String getName() {
          return "Annotate with @Keep";
        }
      }
    };
  }
}
