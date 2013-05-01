/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.sdk.SdkVersionInfo;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.FQCN_TARGET_API;

/** Fix which adds a {@code @TargetApi} annotation at the nearest surrounding method or class */
class AddTargetApiQuickFix implements AndroidLintQuickFix {
  private int myApi;

  AddTargetApiQuickFix(int api) {
    myApi = api;
  }

  private String getAnnotationValue() {
    String codeName = SdkVersionInfo.getBuildCode(myApi);
    if (codeName == null) {
      return Integer.toString(myApi);
    } else {
      return "android.os.Build.VERSION_CODES." + codeName;
    }
  }

  @NotNull
  @Override
  public String getName() {
    String value = getAnnotationValue();
    String key = value.substring(value.lastIndexOf('.') + 1);
    return AndroidBundle.message("android.lint.fix.add.target.api", key);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, PsiModifierListOwner.class, false) != null;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    // Find nearest method or class; can't add @TargetApi on modifier list owners like variable declarations
    @SuppressWarnings("unchecked")
    PsiModifierListOwner container = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class, PsiClass.class);
    if (container == null) {
      return;
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) {
      return;
    }
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      Project project = startElement.getProject();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
      String annotationText = "@" + FQCN_TARGET_API + "(" + getAnnotationValue() + ")";
      PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText(annotationText, container);
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(container, FQCN_TARGET_API);
      if (annotation != null && annotation.isPhysical()) {
        annotation.replace(newAnnotation);
      } else {
        PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        AddAnnotationFix fix = new AddAnnotationFix(FQCN_TARGET_API, container, attributes);
        fix.invoke(project, null /*editor*/, container.getContainingFile());
      }
    }
  }
}
