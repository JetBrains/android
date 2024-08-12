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

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import javax.annotation.Nullable;

/**
 * Find usages of Dagger code in project libraries.
 *
 * <p>Without implementing {@link UseScopeEnlarger}, IntelliJ will not search libraries for usages
 * of symbols defined in the project.
 */
class DaggerUseScopeEnlarger extends UseScopeEnlarger {

  private static final ImmutableSet<String> IMPLICIT_METHOD_USAGE_ANNOTATIONS =
      ImmutableSet.of(
          "dagger.Provides", "dagger.Binds", "dagger.BindsOptionalOf", "dagger.producers.Produces");

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(PsiElement element) {
    if (isImplicitUsageMethod(element)) {
      return GlobalSearchScope.allScope(element.getProject());
    }
    return null;
  }

  /** Returns true if the method is called in generated code. */
  static boolean isImplicitUsageMethod(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    PsiModifierList modifiers = method.getModifierList();
    return IMPLICIT_METHOD_USAGE_ANNOTATIONS
        .stream()
        .anyMatch(s -> modifiers.findAnnotation(s) != null);
  }
}
