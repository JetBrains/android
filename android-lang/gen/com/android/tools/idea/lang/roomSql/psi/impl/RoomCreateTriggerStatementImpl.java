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

public class RoomCreateTriggerStatementImpl extends ASTWrapperPsiElement implements RoomCreateTriggerStatement {

  public RoomCreateTriggerStatementImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RoomVisitor visitor) {
    visitor.visitCreateTriggerStatement(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RoomVisitor) accept((RoomVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RoomColumnName> getColumnNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RoomColumnName.class);
  }

  @Override
  @Nullable
  public RoomDatabaseName getDatabaseName() {
    return findChildByClass(RoomDatabaseName.class);
  }

  @Override
  @NotNull
  public RoomDefinedTableName getDefinedTableName() {
    return findNotNullChildByClass(RoomDefinedTableName.class);
  }

  @Override
  @Nullable
  public RoomDeleteStatement getDeleteStatement() {
    return findChildByClass(RoomDeleteStatement.class);
  }

  @Override
  @Nullable
  public RoomExpression getExpression() {
    return findChildByClass(RoomExpression.class);
  }

  @Override
  @Nullable
  public RoomInsertStatement getInsertStatement() {
    return findChildByClass(RoomInsertStatement.class);
  }

  @Override
  @Nullable
  public RoomSelectStatement getSelectStatement() {
    return findChildByClass(RoomSelectStatement.class);
  }

  @Override
  @NotNull
  public RoomTriggerName getTriggerName() {
    return findNotNullChildByClass(RoomTriggerName.class);
  }

  @Override
  @Nullable
  public RoomUpdateStatement getUpdateStatement() {
    return findChildByClass(RoomUpdateStatement.class);
  }

  @Override
  @Nullable
  public RoomWithClause getWithClause() {
    return findChildByClass(RoomWithClause.class);
  }

}
