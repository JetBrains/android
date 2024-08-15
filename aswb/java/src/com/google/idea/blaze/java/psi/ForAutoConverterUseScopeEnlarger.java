/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import javax.annotation.Nullable;

/**
 * Find usages of @ForAutoConverter-annotated classes in project libraries.
 *
 * <p>Without implementing {@link UseScopeEnlarger}, IntelliJ will not search libraries for usages
 * of symbols defined in the project.
 */
class ForAutoConverterUseScopeEnlarger extends UseScopeEnlarger {

  private static final String FOR_AUTO_CONVERTER_ANNOTATION =
      "com.google.common.converter.auto.ForAutoConverter";

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(PsiElement element) {
    if (isForAutoConverterField(element) || isForAutoConverterMethod(element)) {
      return GlobalSearchScope.allScope(element.getProject());
    }
    return null;
  }

  static boolean isForAutoConverterField(PsiElement element) {
    if (!(element instanceof PsiField)) {
      return false;
    }
    PsiField psiField = (PsiField) element;
    if (psiField == null || psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    PsiModifierList modifiers = psiField.getModifierList();
    return modifiers != null && modifiers.findAnnotation(FOR_AUTO_CONVERTER_ANNOTATION) != null;
  }

  static boolean isForAutoConverterMethod(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    PsiModifierList modifiers = method.getModifierList();
    return modifiers != null && modifiers.findAnnotation(FOR_AUTO_CONVERTER_ANNOTATION) != null;
  }
}
