/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class DefinitionReference implements DataBindingXmlReferenceContributor.ResolvesToModelClass, PsiReference {
  protected final TextRange myTextRange;
  protected final PsiElement myElement;
  protected final PsiElement myTarget;

  DefinitionReference(@NotNull PsiElement element, PsiElement resolveTo) {
    this(element, resolveTo, getElementRange(element));
  }

  @NotNull
  private static TextRange getElementRange(@NotNull PsiElement element) {
    int startOffsetInParent = element.getParent().getStartOffsetInParent();
    return startOffsetInParent > 0 ? element.getTextRange().shiftRight(-startOffsetInParent) : element.getTextRange();
  }

  DefinitionReference(@NotNull PsiElement element, PsiElement resolveTo, @NotNull TextRange range) {
    myElement = element;
    myTarget = resolveTo;
    myTextRange = range;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myTextRange;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myTarget;
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myElement.getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myElement.getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}
