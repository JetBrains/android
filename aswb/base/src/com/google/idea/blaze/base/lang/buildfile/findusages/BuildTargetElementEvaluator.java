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

import com.google.idea.blaze.base.lang.buildfile.psi.Argument.Keyword;
import com.google.idea.blaze.base.lang.buildfile.psi.ArgumentList;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * StringLiterals can reference multiple targets (e.g. "//package:target" references both the
 * package and the target). IntelliJ defaults to highlighting / navigating to the innermost
 * reference, but in this case, we want the opposite behavior (the target reference should trump the
 * package reference).
 */
public class BuildTargetElementEvaluator extends TargetElementEvaluatorEx2 {

  @Override
  public boolean includeSelfInGotoImplementation(PsiElement element) {
    return false;
  }

  /** Returns null in the cases where we're happy with the default behavior. */
  @Nullable
  @Override
  public PsiElement getElementByReference(PsiReference ref, int flags) {
    if (!(ref instanceof PsiMultiReference) || !(ref.getElement() instanceof StringLiteral)) {
      return null;
    }
    // choose the outer-most reference
    PsiReference[] refs = ((PsiMultiReference) ref).getReferences().clone();
    Arrays.sort(refs, COMPARATOR);
    return refs[0].resolve();
  }

  private static final Comparator<PsiReference> COMPARATOR =
      (ref1, ref2) -> {
        boolean resolves1 = ref1.resolve() != null;
        boolean resolves2 = ref2.resolve() != null;
        if (resolves1 && !resolves2) {
          return -1;
        }
        if (!resolves1 && resolves2) {
          return 1;
        }

        final TextRange range1 = ref1.getRangeInElement();
        final TextRange range2 = ref2.getRangeInElement();

        if (TextRange.areSegmentsEqual(range1, range2)) {
          return 0;
        }
        if (range1.getStartOffset() >= range2.getStartOffset()
            && range1.getEndOffset() <= range2.getEndOffset()) {
          return 1;
        }
        if (range2.getStartOffset() >= range1.getStartOffset()
            && range2.getEndOffset() <= range1.getEndOffset()) {
          return -1;
        }

        return 0;
      };

  /** Redirect 'name' funcall argument values to the funcall expression (b/29088829). */
  @Nullable
  @Override
  public PsiElement getNamedElement(PsiElement element) {
    return getParentFuncallIfNameString(element);
  }

  @Nullable
  private static FuncallExpression getParentFuncallIfNameString(PsiElement element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof StringLiteral)) {
      return null;
    }
    parent = parent.getParent();
    if (!(parent instanceof Keyword)) {
      return null;
    }
    if (!Objects.equals(((Keyword) parent).getName(), "name")) {
      return null;
    }
    parent = parent.getParent();
    if (!(parent instanceof ArgumentList)) {
      return null;
    }
    parent = parent.getParent();
    return parent instanceof FuncallExpression ? (FuncallExpression) parent : null;
  }
}
