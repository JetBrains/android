/*
 * Copyright (C) 2022 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from Aidl.bnf. Do not edit it manually.

package com.android.tools.idea.lang.aidl.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes.*;
import com.android.tools.idea.lang.aidl.psi.*;
import com.intellij.psi.PsiReference;

public class AidlQualifiedNameImpl extends AidlNamedElementImpl implements AidlQualifiedName {

  public AidlQualifiedNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AidlVisitor visitor) {
    visitor.visitQualifiedName(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) accept((AidlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AidlNameComponent> getNameComponentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AidlNameComponent.class);
  }

  @Override
  public @NotNull PsiElement getNameIdentifier() {
    return AidlPsiUtil.getNameIdentifier(this);
  }

  @Override
  public @NotNull String getQualifiedName() {
    return AidlPsiUtil.getQualifiedName(this);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return AidlPsiUtil.resolve(this);
  }

  @Override
  public @NotNull PsiReference getReference() {
    return AidlPsiUtil.getReference(this);
  }

}
