/*
 * Copyright (C) 2025 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from
// wear-dwf/src/com/android/tools/idea/wear/dwf/dom/raw/expressions/wff_expressions.bnf
// Do not edit it manually.
package com.android.tools.idea.wear.dwf.dom.raw.expressions.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.*;
import com.android.tools.idea.wear.dwf.dom.raw.expressions.*;

public class WFFExpressionLiteralExprImpl extends WFFExpressionExprImpl implements WFFExpressionLiteralExpr {

  public WFFExpressionLiteralExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull WFFExpressionVisitor visitor) {
    visitor.visitLiteralExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof WFFExpressionVisitor) accept((WFFExpressionVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public WFFExpressionConfiguration getConfiguration() {
    return findChildByClass(WFFExpressionConfiguration.class);
  }

  @Override
  @Nullable
  public WFFExpressionDataSource getDataSource() {
    return findChildByClass(WFFExpressionDataSource.class);
  }

  @Override
  @Nullable
  public PsiElement getId() {
    return findChildByType(ID);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getString() {
    return findChildByType(STRING);
  }

}
