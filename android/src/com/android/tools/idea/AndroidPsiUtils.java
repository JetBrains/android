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

package com.android.tools.idea;

import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.R_CLASS;

public class AndroidPsiUtils {
  /**
   * Returns true if the given PsiElement is a reference to an Android Resource.
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  public static boolean isResourceReference(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return isResourceReference((PsiReferenceExpression)element);
    }

    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      return isResourceReference((PsiReferenceExpression)element.getParent());
    }

    return false;
  }

  private static boolean isResourceReference(PsiReferenceExpression element) {
    PsiExpression exp = element.getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return false;
    }

    exp = ((PsiReferenceExpression)exp).getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return false;
    }

    PsiReferenceExpression ref = (PsiReferenceExpression)exp;
    return R_CLASS.equals(ref.getReferenceName()) && ref.getQualifierExpression() == null;
  }

  /** Returns the Android {@link ResourceType} given a PSI reference to an Android resource. */
  @Nullable
  public static ResourceType getResourceType(PsiElement resourceRefElement) {
    if (!isResourceReference(resourceRefElement)) {
      return null;
    }

    PsiReferenceExpression exp = resourceRefElement instanceof PsiReferenceExpression ?
                                 (PsiReferenceExpression)resourceRefElement :
                                 (PsiReferenceExpression)resourceRefElement.getParent();

    PsiExpression qualifierExpression = exp.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return ResourceType.getEnum(qualifierExpression.getLastChild().getText());
  }
}
