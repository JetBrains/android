/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;

/** Utility methods for finding all references to a PsiElement */
public class FindUsages {

  public static PsiReference[] findAllReferences(PsiElement element) {
    return findReferencesInScope(element, GlobalSearchScope.allScope(element.getProject()));
  }

  /**
   * Search scope taken from PsiSearchHelper::getUseScope, which incorporates UseScopeEnlarger /
   * UseScopeOptimizer EPs.
   */
  public static PsiReference[] findReferencesInElementScope(PsiElement element) {
    return findReferencesInScope(
        element, PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element));
  }

  public static PsiReference[] findReferencesInScope(PsiElement element, SearchScope scope) {
    return ReferencesSearch.search(element, scope, true).toArray(new PsiReference[0]);
  }
}
