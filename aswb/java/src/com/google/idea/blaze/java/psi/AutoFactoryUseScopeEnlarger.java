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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import javax.annotation.Nullable;

/**
 * Find usages of @AutoFactory-annotated classes in project libraries.
 *
 * <p>Without implementing {@link UseScopeEnlarger}, IntelliJ will not search libraries for usages
 * of symbols defined in the project.
 */
class AutoFactoryUseScopeEnlarger extends UseScopeEnlarger {

  private static final String AUTO_FACTORY_ANNOTATION = "com.google.auto.factory.AutoFactory";

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(PsiElement element) {
    if (isAutoFactoryClass(element)) {
      return GlobalSearchScope.allScope(element.getProject());
    }
    return null;
  }

  @Nullable
  private static PsiClass getPsiClass(PsiElement element) {
    if (element instanceof PsiClass) {
      return (PsiClass) element;
    }
    if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
      return ((PsiMethod) element).getContainingClass();
    }
    return null;
  }

  static boolean isAutoFactoryClass(PsiElement element) {
    PsiClass psiClass = getPsiClass(element);
    if (psiClass == null || !psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    PsiModifierList modifiers = psiClass.getModifierList();
    return modifiers != null && modifiers.findAnnotation(AUTO_FACTORY_ANNOTATION) != null;
  }
}
