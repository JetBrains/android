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
import com.intellij.psi.PsiNamedElement;

public class RoomVisitor extends PsiElementVisitor {

  public void visitAddExpression(@NotNull RoomAddExpression o) {
    visitExpression(o);
  }

  public void visitAlterTableStatement(@NotNull RoomAlterTableStatement o) {
    visitStatement(o);
  }

  public void visitAnalyzeStatement(@NotNull RoomAnalyzeStatement o) {
    visitStatement(o);
  }

  public void visitAndExpression(@NotNull RoomAndExpression o) {
    visitExpression(o);
  }

  public void visitAttachStatement(@NotNull RoomAttachStatement o) {
    visitStatement(o);
  }

  public void visitBeginStatement(@NotNull RoomBeginStatement o) {
    visitStatement(o);
  }

  public void visitBetweenExpression(@NotNull RoomBetweenExpression o) {
    visitExpression(o);
  }

  public void visitBindParameter(@NotNull RoomBindParameter o) {
    visitPsiElement(o);
  }

  public void visitBitExpression(@NotNull RoomBitExpression o) {
    visitExpression(o);
  }

  public void visitCaseExpression(@NotNull RoomCaseExpression o) {
    visitExpression(o);
  }

  public void visitCastExpression(@NotNull RoomCastExpression o) {
    visitExpression(o);
  }

  public void visitCollateExpression(@NotNull RoomCollateExpression o) {
    visitExpression(o);
  }

  public void visitCollationName(@NotNull RoomCollationName o) {
    visitNameElement(o);
  }

  public void visitColumnAliasName(@NotNull RoomColumnAliasName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitColumnConstraint(@NotNull RoomColumnConstraint o) {
    visitPsiElement(o);
  }

  public void visitColumnDefinition(@NotNull RoomColumnDefinition o) {
    visitPsiElement(o);
  }

  public void visitColumnDefinitionName(@NotNull RoomColumnDefinitionName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitColumnName(@NotNull RoomColumnName o) {
    visitNameElement(o);
  }

  public void visitColumnRefExpression(@NotNull RoomColumnRefExpression o) {
    visitExpression(o);
  }

  public void visitCommitStatement(@NotNull RoomCommitStatement o) {
    visitStatement(o);
  }

  public void visitComparisonExpression(@NotNull RoomComparisonExpression o) {
    visitExpression(o);
  }

  public void visitCompoundOperator(@NotNull RoomCompoundOperator o) {
    visitPsiElement(o);
  }

  public void visitConcatExpression(@NotNull RoomConcatExpression o) {
    visitExpression(o);
  }

  public void visitConflictClause(@NotNull RoomConflictClause o) {
    visitPsiElement(o);
  }

  public void visitCreateIndexStatement(@NotNull RoomCreateIndexStatement o) {
    visitStatement(o);
  }

  public void visitCreateTableStatement(@NotNull RoomCreateTableStatement o) {
    visitStatement(o);
  }

  public void visitCreateTriggerStatement(@NotNull RoomCreateTriggerStatement o) {
    visitStatement(o);
  }

  public void visitCreateViewStatement(@NotNull RoomCreateViewStatement o) {
    visitStatement(o);
  }

  public void visitCreateVirtualTableStatement(@NotNull RoomCreateVirtualTableStatement o) {
    visitStatement(o);
  }

  public void visitDatabaseName(@NotNull RoomDatabaseName o) {
    visitNameElement(o);
  }

  public void visitDefinedTableName(@NotNull RoomDefinedTableName o) {
    visitNameElement(o);
  }

  public void visitDeleteStatement(@NotNull RoomDeleteStatement o) {
    visitStatement(o);
    // visitHasWithClause(o);
  }

  public void visitDetachStatement(@NotNull RoomDetachStatement o) {
    visitStatement(o);
  }

  public void visitDropIndexStatement(@NotNull RoomDropIndexStatement o) {
    visitStatement(o);
  }

  public void visitDropTableStatement(@NotNull RoomDropTableStatement o) {
    visitStatement(o);
  }

  public void visitDropTriggerStatement(@NotNull RoomDropTriggerStatement o) {
    visitStatement(o);
  }

  public void visitDropViewStatement(@NotNull RoomDropViewStatement o) {
    visitStatement(o);
  }

  public void visitEquivalenceExpression(@NotNull RoomEquivalenceExpression o) {
    visitExpression(o);
  }

  public void visitErrorMessage(@NotNull RoomErrorMessage o) {
    visitPsiElement(o);
  }

  public void visitExistsExpression(@NotNull RoomExistsExpression o) {
    visitExpression(o);
  }

  public void visitExpression(@NotNull RoomExpression o) {
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

  public void visitFunctionCallExpression(@NotNull RoomFunctionCallExpression o) {
    visitExpression(o);
  }

  public void visitFunctionName(@NotNull RoomFunctionName o) {
    visitNameElement(o);
  }

  public void visitGroupByClause(@NotNull RoomGroupByClause o) {
    visitPsiElement(o);
  }

  public void visitInExpression(@NotNull RoomInExpression o) {
    visitExpression(o);
  }

  public void visitIndexedColumn(@NotNull RoomIndexedColumn o) {
    visitPsiElement(o);
  }

  public void visitInsertColumns(@NotNull RoomInsertColumns o) {
    visitPsiElement(o);
  }

  public void visitInsertStatement(@NotNull RoomInsertStatement o) {
    visitStatement(o);
    // visitHasWithClause(o);
  }

  public void visitIsnullExpression(@NotNull RoomIsnullExpression o) {
    visitExpression(o);
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

  public void visitLikeExpression(@NotNull RoomLikeExpression o) {
    visitExpression(o);
  }

  public void visitLimitClause(@NotNull RoomLimitClause o) {
    visitPsiElement(o);
  }

  public void visitLiteralExpression(@NotNull RoomLiteralExpression o) {
    visitExpression(o);
  }

  public void visitModuleArgument(@NotNull RoomModuleArgument o) {
    visitPsiElement(o);
  }

  public void visitModuleName(@NotNull RoomModuleName o) {
    visitNameElement(o);
  }

  public void visitMulExpression(@NotNull RoomMulExpression o) {
    visitExpression(o);
  }

  public void visitOrExpression(@NotNull RoomOrExpression o) {
    visitExpression(o);
  }

  public void visitOrderClause(@NotNull RoomOrderClause o) {
    visitPsiElement(o);
  }

  public void visitOrderingTerm(@NotNull RoomOrderingTerm o) {
    visitPsiElement(o);
  }

  public void visitParenExpression(@NotNull RoomParenExpression o) {
    visitExpression(o);
  }

  public void visitPragmaName(@NotNull RoomPragmaName o) {
    visitNameElement(o);
  }

  public void visitPragmaStatement(@NotNull RoomPragmaStatement o) {
    visitStatement(o);
  }

  public void visitPragmaValue(@NotNull RoomPragmaValue o) {
    visitPsiElement(o);
  }

  public void visitRaiseFunctionExpression(@NotNull RoomRaiseFunctionExpression o) {
    visitExpression(o);
  }

  public void visitReindexStatement(@NotNull RoomReindexStatement o) {
    visitStatement(o);
  }

  public void visitReleaseStatement(@NotNull RoomReleaseStatement o) {
    visitStatement(o);
  }

  public void visitResultColumn(@NotNull RoomResultColumn o) {
    visitPsiElement(o);
  }

  public void visitResultColumns(@NotNull RoomResultColumns o) {
    visitPsiElement(o);
  }

  public void visitRollbackStatement(@NotNull RoomRollbackStatement o) {
    visitStatement(o);
  }

  public void visitSavepointName(@NotNull RoomSavepointName o) {
    visitNameElement(o);
  }

  public void visitSavepointStatement(@NotNull RoomSavepointStatement o) {
    visitStatement(o);
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

  public void visitSelectStatement(@NotNull RoomSelectStatement o) {
    visitStatement(o);
    // visitHasWithClause(o);
  }

  public void visitSelectedTableName(@NotNull RoomSelectedTableName o) {
    visitNameElement(o);
  }

  public void visitSignedNumber(@NotNull RoomSignedNumber o) {
    visitPsiElement(o);
  }

  public void visitSingleTableStatementTable(@NotNull RoomSingleTableStatementTable o) {
    visitSqlTableElement(o);
  }

  public void visitStatement(@NotNull RoomStatement o) {
    visitPsiElement(o);
  }

  public void visitSubquery(@NotNull RoomSubquery o) {
    visitSqlTableElement(o);
  }

  public void visitTableAliasName(@NotNull RoomTableAliasName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitTableConstraint(@NotNull RoomTableConstraint o) {
    visitPsiElement(o);
  }

  public void visitTableDefinitionName(@NotNull RoomTableDefinitionName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
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

  public void visitUnaryExpression(@NotNull RoomUnaryExpression o) {
    visitExpression(o);
  }

  public void visitUpdateStatement(@NotNull RoomUpdateStatement o) {
    visitStatement(o);
    // visitHasWithClause(o);
  }

  public void visitVacuumStatement(@NotNull RoomVacuumStatement o) {
    visitStatement(o);
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
    visitPsiElement(o);
  }

  public void visitWithClauseTableDef(@NotNull RoomWithClauseTableDef o) {
    visitPsiElement(o);
  }

  public void visitPsiNamedElement(@NotNull PsiNamedElement o) {
    visitElement(o);
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
