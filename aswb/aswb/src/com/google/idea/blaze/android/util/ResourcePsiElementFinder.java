/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.util;

import com.google.common.collect.ImmutableMap;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;

/** Finder to locate the closest full resource reference. */
public interface ResourcePsiElementFinder {
  ImmutableMap<Language, ResourcePsiElementFinder> LANGUAGE_TO_FINDERS =
      ImmutableMap.of(
          JavaLanguage.INSTANCE, new JavaResourcePsiElementFinder(),
          KotlinLanguage.INSTANCE, new KotlinResourcePsiElementFinder());

  @Nullable
  static PsiElement getFullExpression(PsiElement psiElement) {
    ResourcePsiElementFinder finder = LANGUAGE_TO_FINDERS.get(psiElement.getLanguage());
    if (finder == null) {
      return null;
    }
    return finder.getResourceElement(psiElement);
  }

  /**
   * Attempts to find the smallest psiElement that contains the full resource accessor string of
   * type R.abc.xyz without resolving the element.
   *
   * @param psiElement element to find the resource string from.
   * @return null if no Resource Element was found, or the closest parent containing the entire
   *     resource references.
   */
  @Nullable
  PsiElement getResourceElement(PsiElement psiElement);
}
