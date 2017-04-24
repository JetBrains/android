/*
 * Copyright (C) 2017 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from roomSql.bnf. Do not edit it manually.

package com.android.tools.idea.lang.roomSql.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.roomSql.psi.*;

public class RoomExprImpl extends ASTWrapperPsiElement implements RoomExpr {

  public RoomExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RoomVisitor visitor) {
    visitor.visitExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RoomVisitor) accept((RoomVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RoomBindParameter getBindParameter() {
    return findChildByClass(RoomBindParameter.class);
  }

  @Override
  @Nullable
  public RoomColumnName getColumnName() {
    return findChildByClass(RoomColumnName.class);
  }

  @Override
  @Nullable
  public RoomDatabaseName getDatabaseName() {
    return findChildByClass(RoomDatabaseName.class);
  }

  @Override
  @Nullable
  public RoomExpr getExpr() {
    return findChildByClass(RoomExpr.class);
  }

  @Override
  @Nullable
  public RoomFunctionName getFunctionName() {
    return findChildByClass(RoomFunctionName.class);
  }

  @Override
  @Nullable
  public RoomLiteralValue getLiteralValue() {
    return findChildByClass(RoomLiteralValue.class);
  }

  @Override
  @Nullable
  public RoomRaiseFunction getRaiseFunction() {
    return findChildByClass(RoomRaiseFunction.class);
  }

  @Override
  @Nullable
  public RoomSelectStmt getSelectStmt() {
    return findChildByClass(RoomSelectStmt.class);
  }

  @Override
  @Nullable
  public RoomTableName getTableName() {
    return findChildByClass(RoomTableName.class);
  }

  @Override
  @Nullable
  public RoomTypeName getTypeName() {
    return findChildByClass(RoomTypeName.class);
  }

  @Override
  @Nullable
  public RoomUnaryOperator getUnaryOperator() {
    return findChildByClass(RoomUnaryOperator.class);
  }

}
