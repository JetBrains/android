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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.psi.AugmentedAssignmentStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/** Used by find usages tools. */
public class BuildReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(PsiElement element) {
    return element instanceof TargetExpression || element instanceof ReferenceExpression;
  }

  @Override
  public boolean isDeclarationWriteAccess(PsiElement element) {
    return element instanceof TargetExpression;
  }

  @Override
  public Access getReferenceAccess(PsiElement referencedElement, PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public Access getExpressionAccess(PsiElement expression) {
    if (isDeclarationWriteAccess(expression)) {
      return Access.Write;
    }
    if (expression instanceof ReferenceExpression) {
      if (PsiUtils.getParentOfType(expression, AugmentedAssignmentStatement.class, true) != null) {
        return Access.ReadWrite;
      }
    }
    return Access.Read;
  }
}
