/*
 * Copyright (C) 2024 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from declarative.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.declarative.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.android.tools.idea.gradle.declarative.psi.*;
import com.android.tools.idea.gradle.declarative.parser.PsiImplUtil;
import com.intellij.psi.tree.IElementType;

public class DeclarativeLiteralImpl extends CompositePsiElement implements DeclarativeLiteral {

  public DeclarativeLiteralImpl(@NotNull IElementType type) {
    super(type);
  }

  public void accept(@NotNull DeclarativeVisitor visitor) {
    visitor.visitLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeclarativeVisitor) accept((DeclarativeVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getBoolean() {
    return findPsiChildByType(BOOLEAN);
  }

  @Override
  @Nullable
  public PsiElement getMultilineString() {
    return findPsiChildByType(MULTILINE_STRING);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findPsiChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getString() {
    return findPsiChildByType(STRING);
  }

  @Override
  @Nullable
  public Object getValue() {
    return PsiImplUtil.getValue(this);
  }

}
