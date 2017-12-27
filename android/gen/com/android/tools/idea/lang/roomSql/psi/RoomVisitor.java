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

package com.android.tools.idea.lang.roomSql.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class RoomVisitor extends PsiElementVisitor {

  public void visitAddExpr(@NotNull RoomAddExpr o) {
    visitExpr(o);
  }

  public void visitAlterTableStmt(@NotNull RoomAlterTableStmt o) {
    visitPsiElement(o);
  }

  public void visitAnalyzeStmt(@NotNull RoomAnalyzeStmt o) {
    visitPsiElement(o);
  }

  public void visitAndExpr(@NotNull RoomAndExpr o) {
    visitExpr(o);
  }

  public void visitAttachStmt(@NotNull RoomAttachStmt o) {
    visitPsiElement(o);
  }

  public void visitBeginStmt(@NotNull RoomBeginStmt o) {
    visitPsiElement(o);
  }

  public void visitBetweenExpr(@NotNull RoomBetweenExpr o) {
    visitExpr(o);
  }

  public void visitBindParameter(@NotNull RoomBindParameter o) {
    visitPsiElement(o);
  }

  public void visitBitExpr(@NotNull RoomBitExpr o) {
    visitExpr(o);
  }

  public void visitCaseExpr(@NotNull RoomCaseExpr o) {
    visitExpr(o);
  }

  public void visitCastExpr(@NotNull RoomCastExpr o) {
    visitExpr(o);
  }

  public void visitCollateExpr(@NotNull RoomCollateExpr o) {
    visitExpr(o);
  }

  public void visitCollationName(@NotNull RoomCollationName o) {
    visitPsiElement(o);
  }

  public void visitColumnAlias(@NotNull RoomColumnAlias o) {
    visitPsiElement(o);
  }

  public void visitColumnConstraint(@NotNull RoomColumnConstraint o) {
    visitPsiElement(o);
  }

  public void visitColumnDef(@NotNull RoomColumnDef o) {
    visitPsiElement(o);
  }

  public void visitColumnName(@NotNull RoomColumnName o) {
    visitNameElement(o);
  }

  public void visitColumnRefExpr(@NotNull RoomColumnRefExpr o) {
    visitExpr(o);
  }

  public void visitCommitStmt(@NotNull RoomCommitStmt o) {
    visitPsiElement(o);
  }

  public void visitCommonTableExpression(@NotNull RoomCommonTableExpression o) {
    visitPsiElement(o);
  }

  public void visitComparisonExpr(@NotNull RoomComparisonExpr o) {
    visitExpr(o);
  }

  public void visitCompoundOperator(@NotNull RoomCompoundOperator o) {
    visitPsiElement(o);
  }

  public void visitConcatExpr(@NotNull RoomConcatExpr o) {
    visitExpr(o);
  }

  public void visitConflictClause(@NotNull RoomConflictClause o) {
    visitPsiElement(o);
  }

  public void visitCreateIndexStmt(@NotNull RoomCreateIndexStmt o) {
    visitPsiElement(o);
  }

  public void visitCreateTableStmt(@NotNull RoomCreateTableStmt o) {
    visitPsiElement(o);
  }

  public void visitCreateTriggerStmt(@NotNull RoomCreateTriggerStmt o) {
    visitPsiElement(o);
  }

  public void visitCreateViewStmt(@NotNull RoomCreateViewStmt o) {
    visitPsiElement(o);
  }

  public void visitCreateVirtualTableStmt(@NotNull RoomCreateVirtualTableStmt o) {
    visitPsiElement(o);
  }

  public void visitCteTableName(@NotNull RoomCteTableName o) {
    visitPsiElement(o);
  }

  public void visitDatabaseName(@NotNull RoomDatabaseName o) {
    visitPsiElement(o);
  }

  public void visitDeleteStmt(@NotNull RoomDeleteStmt o) {
    visitPsiElement(o);
  }

  public void visitDeleteStmtLimited(@NotNull RoomDeleteStmtLimited o) {
    visitPsiElement(o);
  }

  public void visitDetachStmt(@NotNull RoomDetachStmt o) {
    visitPsiElement(o);
  }

  public void visitDropIndexStmt(@NotNull RoomDropIndexStmt o) {
    visitPsiElement(o);
  }

  public void visitDropTableStmt(@NotNull RoomDropTableStmt o) {
    visitPsiElement(o);
  }

  public void visitDropTriggerStmt(@NotNull RoomDropTriggerStmt o) {
    visitPsiElement(o);
  }

  public void visitDropViewStmt(@NotNull RoomDropViewStmt o) {
    visitPsiElement(o);
  }

  public void visitEquivalenceExpr(@NotNull RoomEquivalenceExpr o) {
    visitExpr(o);
  }

  public void visitErrorMessage(@NotNull RoomErrorMessage o) {
    visitPsiElement(o);
  }

  public void visitExistsExpr(@NotNull RoomExistsExpr o) {
    visitExpr(o);
  }

  public void visitExpr(@NotNull RoomExpr o) {
    visitPsiElement(o);
  }

  public void visitForeignKeyClause(@NotNull RoomForeignKeyClause o) {
    visitPsiElement(o);
  }

  public void visitForeignTable(@NotNull RoomForeignTable o) {
    visitPsiElement(o);
  }

  public void visitFunctionCallExpr(@NotNull RoomFunctionCallExpr o) {
    visitExpr(o);
  }

  public void visitFunctionName(@NotNull RoomFunctionName o) {
    visitPsiElement(o);
  }

  public void visitInExpr(@NotNull RoomInExpr o) {
    visitExpr(o);
  }

  public void visitIndexName(@NotNull RoomIndexName o) {
    visitPsiElement(o);
  }

  public void visitIndexedColumn(@NotNull RoomIndexedColumn o) {
    visitPsiElement(o);
  }

  public void visitInsertStmt(@NotNull RoomInsertStmt o) {
    visitPsiElement(o);
  }

  public void visitIsnullExpr(@NotNull RoomIsnullExpr o) {
    visitExpr(o);
  }

  public void visitJoinClause(@NotNull RoomJoinClause o) {
    visitPsiElement(o);
  }

  public void visitJoinConstraint(@NotNull RoomJoinConstraint o) {
    visitPsiElement(o);
  }

  public void visitJoinOperator(@NotNull RoomJoinOperator o) {
    visitPsiElement(o);
  }

  public void visitLikeExpr(@NotNull RoomLikeExpr o) {
    visitExpr(o);
  }

  public void visitLiteralExpr(@NotNull RoomLiteralExpr o) {
    visitExpr(o);
  }

  public void visitLiteralValue(@NotNull RoomLiteralValue o) {
    visitPsiElement(o);
  }

  public void visitModuleArgument(@NotNull RoomModuleArgument o) {
    visitPsiElement(o);
  }

  public void visitModuleName(@NotNull RoomModuleName o) {
    visitPsiElement(o);
  }

  public void visitMulExpr(@NotNull RoomMulExpr o) {
    visitExpr(o);
  }

  public void visitOrExpr(@NotNull RoomOrExpr o) {
    visitExpr(o);
  }

  public void visitOrderingTerm(@NotNull RoomOrderingTerm o) {
    visitPsiElement(o);
  }

  public void visitParenExpr(@NotNull RoomParenExpr o) {
    visitExpr(o);
  }

  public void visitPragmaName(@NotNull RoomPragmaName o) {
    visitPsiElement(o);
  }

  public void visitPragmaStmt(@NotNull RoomPragmaStmt o) {
    visitPsiElement(o);
  }

  public void visitPragmaValue(@NotNull RoomPragmaValue o) {
    visitPsiElement(o);
  }

  public void visitQualifiedTableName(@NotNull RoomQualifiedTableName o) {
    visitPsiElement(o);
  }

  public void visitRaiseFunctionExpr(@NotNull RoomRaiseFunctionExpr o) {
    visitExpr(o);
  }

  public void visitReindexStmt(@NotNull RoomReindexStmt o) {
    visitPsiElement(o);
  }

  public void visitReleaseStmt(@NotNull RoomReleaseStmt o) {
    visitPsiElement(o);
  }

  public void visitResultColumn(@NotNull RoomResultColumn o) {
    visitPsiElement(o);
  }

  public void visitRollbackStmt(@NotNull RoomRollbackStmt o) {
    visitPsiElement(o);
  }

  public void visitSavepointName(@NotNull RoomSavepointName o) {
    visitPsiElement(o);
  }

  public void visitSavepointStmt(@NotNull RoomSavepointStmt o) {
    visitPsiElement(o);
  }

  public void visitSelectStmt(@NotNull RoomSelectStmt o) {
    visitPsiElement(o);
  }

  public void visitSignedNumber(@NotNull RoomSignedNumber o) {
    visitPsiElement(o);
  }

  public void visitSqlStmt(@NotNull RoomSqlStmt o) {
    visitPsiElement(o);
  }

  public void visitTableAlias(@NotNull RoomTableAlias o) {
    visitPsiElement(o);
  }

  public void visitTableConstraint(@NotNull RoomTableConstraint o) {
    visitPsiElement(o);
  }

  public void visitTableName(@NotNull RoomTableName o) {
    visitNameElement(o);
  }

  public void visitTableOrIndexName(@NotNull RoomTableOrIndexName o) {
    visitPsiElement(o);
  }

  public void visitTableOrSubquery(@NotNull RoomTableOrSubquery o) {
    visitPsiElement(o);
  }

  public void visitTriggerName(@NotNull RoomTriggerName o) {
    visitPsiElement(o);
  }

  public void visitTypeName(@NotNull RoomTypeName o) {
    visitPsiElement(o);
  }

  public void visitUnaryExpr(@NotNull RoomUnaryExpr o) {
    visitExpr(o);
  }

  public void visitUpdateStmt(@NotNull RoomUpdateStmt o) {
    visitPsiElement(o);
  }

  public void visitUpdateStmtLimited(@NotNull RoomUpdateStmtLimited o) {
    visitPsiElement(o);
  }

  public void visitVacuumStmt(@NotNull RoomVacuumStmt o) {
    visitPsiElement(o);
  }

  public void visitViewName(@NotNull RoomViewName o) {
    visitPsiElement(o);
  }

  public void visitWithClause(@NotNull RoomWithClause o) {
    visitPsiElement(o);
  }

  public void visitNameElement(@NotNull RoomNameElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
