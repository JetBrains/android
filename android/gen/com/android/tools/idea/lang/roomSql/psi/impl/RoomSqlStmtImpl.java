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

public class RoomSqlStmtImpl extends ASTWrapperPsiElement implements RoomSqlStmt {

  public RoomSqlStmtImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RoomVisitor visitor) {
    visitor.visitSqlStmt(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RoomVisitor) accept((RoomVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RoomAlterTableStmt getAlterTableStmt() {
    return findChildByClass(RoomAlterTableStmt.class);
  }

  @Override
  @Nullable
  public RoomAnalyzeStmt getAnalyzeStmt() {
    return findChildByClass(RoomAnalyzeStmt.class);
  }

  @Override
  @Nullable
  public RoomAttachStmt getAttachStmt() {
    return findChildByClass(RoomAttachStmt.class);
  }

  @Override
  @Nullable
  public RoomBeginStmt getBeginStmt() {
    return findChildByClass(RoomBeginStmt.class);
  }

  @Override
  @Nullable
  public RoomCommitStmt getCommitStmt() {
    return findChildByClass(RoomCommitStmt.class);
  }

  @Override
  @Nullable
  public RoomCreateIndexStmt getCreateIndexStmt() {
    return findChildByClass(RoomCreateIndexStmt.class);
  }

  @Override
  @Nullable
  public RoomCreateTableStmt getCreateTableStmt() {
    return findChildByClass(RoomCreateTableStmt.class);
  }

  @Override
  @Nullable
  public RoomCreateTriggerStmt getCreateTriggerStmt() {
    return findChildByClass(RoomCreateTriggerStmt.class);
  }

  @Override
  @Nullable
  public RoomCreateViewStmt getCreateViewStmt() {
    return findChildByClass(RoomCreateViewStmt.class);
  }

  @Override
  @Nullable
  public RoomCreateVirtualTableStmt getCreateVirtualTableStmt() {
    return findChildByClass(RoomCreateVirtualTableStmt.class);
  }

  @Override
  @Nullable
  public RoomDeleteStmt getDeleteStmt() {
    return findChildByClass(RoomDeleteStmt.class);
  }

  @Override
  @Nullable
  public RoomDeleteStmtLimited getDeleteStmtLimited() {
    return findChildByClass(RoomDeleteStmtLimited.class);
  }

  @Override
  @Nullable
  public RoomDetachStmt getDetachStmt() {
    return findChildByClass(RoomDetachStmt.class);
  }

  @Override
  @Nullable
  public RoomDropIndexStmt getDropIndexStmt() {
    return findChildByClass(RoomDropIndexStmt.class);
  }

  @Override
  @Nullable
  public RoomDropTableStmt getDropTableStmt() {
    return findChildByClass(RoomDropTableStmt.class);
  }

  @Override
  @Nullable
  public RoomDropTriggerStmt getDropTriggerStmt() {
    return findChildByClass(RoomDropTriggerStmt.class);
  }

  @Override
  @Nullable
  public RoomDropViewStmt getDropViewStmt() {
    return findChildByClass(RoomDropViewStmt.class);
  }

  @Override
  @Nullable
  public RoomInsertStmt getInsertStmt() {
    return findChildByClass(RoomInsertStmt.class);
  }

  @Override
  @Nullable
  public RoomPragmaStmt getPragmaStmt() {
    return findChildByClass(RoomPragmaStmt.class);
  }

  @Override
  @Nullable
  public RoomReindexStmt getReindexStmt() {
    return findChildByClass(RoomReindexStmt.class);
  }

  @Override
  @Nullable
  public RoomReleaseStmt getReleaseStmt() {
    return findChildByClass(RoomReleaseStmt.class);
  }

  @Override
  @Nullable
  public RoomRollbackStmt getRollbackStmt() {
    return findChildByClass(RoomRollbackStmt.class);
  }

  @Override
  @Nullable
  public RoomSavepointStmt getSavepointStmt() {
    return findChildByClass(RoomSavepointStmt.class);
  }

  @Override
  @Nullable
  public RoomSelectStmt getSelectStmt() {
    return findChildByClass(RoomSelectStmt.class);
  }

  @Override
  @Nullable
  public RoomUpdateStmt getUpdateStmt() {
    return findChildByClass(RoomUpdateStmt.class);
  }

  @Override
  @Nullable
  public RoomUpdateStmtLimited getUpdateStmtLimited() {
    return findChildByClass(RoomUpdateStmtLimited.class);
  }

  @Override
  @Nullable
  public RoomVacuumStmt getVacuumStmt() {
    return findChildByClass(RoomVacuumStmt.class);
  }

}
