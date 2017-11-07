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
import com.android.tools.idea.lang.roomSql.SqlTableElement;

public class RoomVisitor extends PsiElementVisitor {

  public void visitAddExpr(@NotNull RoomAddExpr o) {
    visitExpr(o);
  }

  public void visitAlterTableStmt(@NotNull RoomAlterTableStmt o) {
    visitStmt(o);
  }

  public void visitAnalyzeStmt(@NotNull RoomAnalyzeStmt o) {
    visitStmt(o);
  }

  public void visitAndExpr(@NotNull RoomAndExpr o) {
    visitExpr(o);
  }

  public void visitAttachStmt(@NotNull RoomAttachStmt o) {
    visitStmt(o);
  }

  public void visitBeginStmt(@NotNull RoomBeginStmt o) {
    visitStmt(o);
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
    visitNameElement(o);
  }

  public void visitColumnAliasName(@NotNull RoomColumnAliasName o) {
    visitNameElement(o);
  }

  public void visitColumnConstraint(@NotNull RoomColumnConstraint o) {
    visitPsiElement(o);
  }

  public void visitColumnDef(@NotNull RoomColumnDef o) {
    visitPsiElement(o);
  }

  public void visitColumnDefName(@NotNull RoomColumnDefName o) {
    visitNameElement(o);
  }

  public void visitColumnName(@NotNull RoomColumnName o) {
    visitNameElement(o);
  }

  public void visitColumnRefExpr(@NotNull RoomColumnRefExpr o) {
    visitExpr(o);
  }

  public void visitCommitStmt(@NotNull RoomCommitStmt o) {
    visitStmt(o);
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
    visitStmt(o);
  }

  public void visitCreateTableStmt(@NotNull RoomCreateTableStmt o) {
    visitStmt(o);
  }

  public void visitCreateTriggerStmt(@NotNull RoomCreateTriggerStmt o) {
    visitStmt(o);
  }

  public void visitCreateViewStmt(@NotNull RoomCreateViewStmt o) {
    visitStmt(o);
  }

  public void visitCreateVirtualTableStmt(@NotNull RoomCreateVirtualTableStmt o) {
    visitStmt(o);
  }

  public void visitDatabaseName(@NotNull RoomDatabaseName o) {
    visitNameElement(o);
  }

  public void visitDeleteStmt(@NotNull RoomDeleteStmt o) {
    visitStmt(o);
  }

  public void visitDetachStmt(@NotNull RoomDetachStmt o) {
    visitStmt(o);
  }

  public void visitDropIndexStmt(@NotNull RoomDropIndexStmt o) {
    visitStmt(o);
  }

  public void visitDropTableStmt(@NotNull RoomDropTableStmt o) {
    visitStmt(o);
  }

  public void visitDropTriggerStmt(@NotNull RoomDropTriggerStmt o) {
    visitStmt(o);
  }

  public void visitDropViewStmt(@NotNull RoomDropViewStmt o) {
    visitStmt(o);
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

  public void visitFromClause(@NotNull RoomFromClause o) {
    visitPsiElement(o);
  }

  public void visitFromTable(@NotNull RoomFromTable o) {
    visitSqlTableElement(o);
  }

  public void visitFunctionCallExpr(@NotNull RoomFunctionCallExpr o) {
    visitExpr(o);
  }

  public void visitFunctionName(@NotNull RoomFunctionName o) {
    visitNameElement(o);
  }

  public void visitGroupByClause(@NotNull RoomGroupByClause o) {
    visitPsiElement(o);
  }

  public void visitInExpr(@NotNull RoomInExpr o) {
    visitExpr(o);
  }

  public void visitIndexedColumn(@NotNull RoomIndexedColumn o) {
    visitPsiElement(o);
  }

  public void visitInsertColumns(@NotNull RoomInsertColumns o) {
    visitPsiElement(o);
  }

  public void visitInsertStmt(@NotNull RoomInsertStmt o) {
    visitStmt(o);
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

  public void visitLimitClause(@NotNull RoomLimitClause o) {
    visitPsiElement(o);
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
    visitNameElement(o);
  }

  public void visitMulExpr(@NotNull RoomMulExpr o) {
    visitExpr(o);
  }

  public void visitOrExpr(@NotNull RoomOrExpr o) {
    visitExpr(o);
  }

  public void visitOrderClause(@NotNull RoomOrderClause o) {
    visitPsiElement(o);
  }

  public void visitOrderingTerm(@NotNull RoomOrderingTerm o) {
    visitPsiElement(o);
  }

  public void visitParenExpr(@NotNull RoomParenExpr o) {
    visitExpr(o);
  }

  public void visitPragmaName(@NotNull RoomPragmaName o) {
    visitNameElement(o);
  }

  public void visitPragmaStmt(@NotNull RoomPragmaStmt o) {
    visitStmt(o);
  }

  public void visitPragmaValue(@NotNull RoomPragmaValue o) {
    visitPsiElement(o);
  }

  public void visitRaiseFunctionExpr(@NotNull RoomRaiseFunctionExpr o) {
    visitExpr(o);
  }

  public void visitReindexStmt(@NotNull RoomReindexStmt o) {
    visitStmt(o);
  }

  public void visitReleaseStmt(@NotNull RoomReleaseStmt o) {
    visitStmt(o);
  }

  public void visitResultColumn(@NotNull RoomResultColumn o) {
    visitPsiElement(o);
  }

  public void visitResultColumns(@NotNull RoomResultColumns o) {
    visitPsiElement(o);
  }

  public void visitRollbackStmt(@NotNull RoomRollbackStmt o) {
    visitStmt(o);
  }

  public void visitSavepointName(@NotNull RoomSavepointName o) {
    visitNameElement(o);
  }

  public void visitSavepointStmt(@NotNull RoomSavepointStmt o) {
    visitStmt(o);
  }

  public void visitSelectCore(@NotNull RoomSelectCore o) {
    visitPsiElement(o);
  }

  public void visitSelectCoreSelect(@NotNull RoomSelectCoreSelect o) {
    visitPsiElement(o);
  }

  public void visitSelectCoreValues(@NotNull RoomSelectCoreValues o) {
    visitPsiElement(o);
  }

  public void visitSelectStmt(@NotNull RoomSelectStmt o) {
    visitStmt(o);
  }

  public void visitSignedNumber(@NotNull RoomSignedNumber o) {
    visitPsiElement(o);
  }

  public void visitSingleTableStmtTable(@NotNull RoomSingleTableStmtTable o) {
    visitSqlTableElement(o);
  }

  public void visitStmt(@NotNull RoomStmt o) {
    visitPsiElement(o);
  }

  public void visitSubquery(@NotNull RoomSubquery o) {
    visitSqlTableElement(o);
  }

  public void visitTableAliasName(@NotNull RoomTableAliasName o) {
    visitNameElement(o);
  }

  public void visitTableConstraint(@NotNull RoomTableConstraint o) {
    visitPsiElement(o);
  }

  public void visitTableDefName(@NotNull RoomTableDefName o) {
    visitNameElement(o);
  }

  public void visitTableName(@NotNull RoomTableName o) {
    visitNameElement(o);
  }

  public void visitTableOrIndexName(@NotNull RoomTableOrIndexName o) {
    visitNameElement(o);
  }

  public void visitTableOrSubquery(@NotNull RoomTableOrSubquery o) {
    visitPsiElement(o);
  }

  public void visitTriggerName(@NotNull RoomTriggerName o) {
    visitNameElement(o);
  }

  public void visitTypeName(@NotNull RoomTypeName o) {
    visitNameElement(o);
  }

  public void visitUnaryExpr(@NotNull RoomUnaryExpr o) {
    visitExpr(o);
  }

  public void visitUpdateStmt(@NotNull RoomUpdateStmt o) {
    visitStmt(o);
  }

  public void visitVacuumStmt(@NotNull RoomVacuumStmt o) {
    visitStmt(o);
  }

  public void visitViewName(@NotNull RoomViewName o) {
    visitNameElement(o);
  }

  public void visitWhereClause(@NotNull RoomWhereClause o) {
    visitPsiElement(o);
  }

  public void visitWithClause(@NotNull RoomWithClause o) {
    visitPsiElement(o);
  }

  public void visitWithClauseTable(@NotNull RoomWithClauseTable o) {
    visitSqlTableElement(o);
  }

  public void visitWithClauseTableDef(@NotNull RoomWithClauseTableDef o) {
    visitPsiElement(o);
  }

  public void visitNameElement(@NotNull RoomNameElement o) {
    visitPsiElement(o);
  }

  public void visitSqlTableElement(@NotNull SqlTableElement o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
