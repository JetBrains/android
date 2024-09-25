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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiQualifiedReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import javax.annotation.Nullable;

/** Java specific {@link ResourcePsiElementFinder} */
class JavaResourcePsiElementFinder implements ResourcePsiElementFinder {
  @Nullable
  @Override
  public PsiElement getResourceElement(PsiElement psiElement) {
    if (isResourceExpression(psiElement)) {
      return psiElement;
    }

    // if PsiElement was `R.abc`, full reference will give us `R.abc.xyz`
    // if psiElement was `R`, full reference will be `R.abc`
    PsiElement parent = psiElement.getParent();
    if (parent == null) {
      return null;
    }
    if (isResourceExpression(parent)) {
      return parent;
    }

    // psiElement might've been `R`, try again.
    parent = parent.getParent();
    if (parent != null && isResourceExpression(parent)) {
      return parent;
    }

    return null;
  }

  /** Checks if `expression` matches an expected R.abc.xyz pattern. */
  private static boolean isResourceExpression(PsiElement expression) {
    if (!(expression instanceof PsiQualifiedReferenceElement)) {
      return false;
    }

    PsiQualifiedReferenceElement qualifiedExpression = (PsiQualifiedReferenceElement) expression;
    // qualifier should be `R.abc` which is also a `PsiReferenceExpression`
    PsiElement qualifier = qualifiedExpression.getQualifier();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }

    PsiReferenceExpression qualifierExpression = (PsiReferenceExpression) qualifier;
    // rClassExpression should be `R`
    PsiElement rClassExpression = qualifierExpression.getQualifier();
    // rTypeExpression should be `abc`
    String rType = qualifierExpression.getReferenceName();

    return rClassExpression != null
        && rType != null
        && SdkConstants.R_CLASS.equals(rClassExpression.getText())
        && ResourceType.fromClassName(rType) != null;
  }
}
