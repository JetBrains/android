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

// ATTENTION: This file has been automatically generated from androidSql.bnf. Do not edit it manually.

package com.android.tools.idea.lang.androidSql.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public class AndroidSqlVisitor extends PsiElementVisitor {

  public void visitAddExpression(@NotNull AndroidSqlAddExpression o) {
    visitExpression(o);
  }

  public void visitAlterTableStatement(@NotNull AndroidSqlAlterTableStatement o) {
    visitPsiElement(o);
  }

  public void visitAnalyzeStatement(@NotNull AndroidSqlAnalyzeStatement o) {
    visitPsiElement(o);
  }

  public void visitAndExpression(@NotNull AndroidSqlAndExpression o) {
    visitExpression(o);
  }

  public void visitAttachStatement(@NotNull AndroidSqlAttachStatement o) {
    visitPsiElement(o);
  }

  public void visitBeginStatement(@NotNull AndroidSqlBeginStatement o) {
    visitPsiElement(o);
  }

  public void visitBetweenExpression(@NotNull AndroidSqlBetweenExpression o) {
    visitExpression(o);
  }

  public void visitBindParameter(@NotNull AndroidSqlBindParameter o) {
    visitPsiElement(o);
  }

  public void visitBitExpression(@NotNull AndroidSqlBitExpression o) {
    visitExpression(o);
  }

  public void visitCaseExpression(@NotNull AndroidSqlCaseExpression o) {
    visitExpression(o);
  }

  public void visitCastExpression(@NotNull AndroidSqlCastExpression o) {
    visitExpression(o);
  }

  public void visitCollateExpression(@NotNull AndroidSqlCollateExpression o) {
    visitExpression(o);
  }

  public void visitCollationName(@NotNull AndroidSqlCollationName o) {
    visitNameElement(o);
  }

  public void visitColumnAliasName(@NotNull AndroidSqlColumnAliasName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitColumnConstraint(@NotNull AndroidSqlColumnConstraint o) {
    visitPsiElement(o);
  }

  public void visitColumnDefinition(@NotNull AndroidSqlColumnDefinition o) {
    visitPsiElement(o);
  }

  public void visitColumnDefinitionName(@NotNull AndroidSqlColumnDefinitionName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitColumnName(@NotNull AndroidSqlColumnName o) {
    visitNameElement(o);
  }

  public void visitColumnRefExpression(@NotNull AndroidSqlColumnRefExpression o) {
    visitExpression(o);
  }

  public void visitCommitStatement(@NotNull AndroidSqlCommitStatement o) {
    visitPsiElement(o);
  }

  public void visitComparisonExpression(@NotNull AndroidSqlComparisonExpression o) {
    visitExpression(o);
  }

  public void visitCompoundOperator(@NotNull AndroidSqlCompoundOperator o) {
    visitPsiElement(o);
  }

  public void visitConcatExpression(@NotNull AndroidSqlConcatExpression o) {
    visitExpression(o);
  }

  public void visitConflictClause(@NotNull AndroidSqlConflictClause o) {
    visitPsiElement(o);
  }

  public void visitCreateIndexStatement(@NotNull AndroidSqlCreateIndexStatement o) {
    visitPsiElement(o);
  }

  public void visitCreateTableStatement(@NotNull AndroidSqlCreateTableStatement o) {
    visitPsiElement(o);
  }

  public void visitCreateTriggerStatement(@NotNull AndroidSqlCreateTriggerStatement o) {
    visitPsiElement(o);
  }

  public void visitCreateViewStatement(@NotNull AndroidSqlCreateViewStatement o) {
    visitPsiElement(o);
  }

  public void visitCreateVirtualTableStatement(@NotNull AndroidSqlCreateVirtualTableStatement o) {
    visitPsiElement(o);
  }

  public void visitDatabaseName(@NotNull AndroidSqlDatabaseName o) {
    visitNameElement(o);
  }

  public void visitDefinedTableName(@NotNull AndroidSqlDefinedTableName o) {
    visitNameElement(o);
  }

  public void visitDeleteStatement(@NotNull AndroidSqlDeleteStatement o) {
    visitPsiElement(o);
  }

  public void visitDetachStatement(@NotNull AndroidSqlDetachStatement o) {
    visitPsiElement(o);
  }

  public void visitDropIndexStatement(@NotNull AndroidSqlDropIndexStatement o) {
    visitPsiElement(o);
  }

  public void visitDropTableStatement(@NotNull AndroidSqlDropTableStatement o) {
    visitPsiElement(o);
  }

  public void visitDropTriggerStatement(@NotNull AndroidSqlDropTriggerStatement o) {
    visitPsiElement(o);
  }

  public void visitDropViewStatement(@NotNull AndroidSqlDropViewStatement o) {
    visitPsiElement(o);
  }

  public void visitEquivalenceExpression(@NotNull AndroidSqlEquivalenceExpression o) {
    visitExpression(o);
  }

  public void visitErrorMessage(@NotNull AndroidSqlErrorMessage o) {
    visitPsiElement(o);
  }

  public void visitExistsExpression(@NotNull AndroidSqlExistsExpression o) {
    visitExpression(o);
  }

  public void visitExplainPrefix(@NotNull AndroidSqlExplainPrefix o) {
    visitPsiElement(o);
  }

  public void visitExpression(@NotNull AndroidSqlExpression o) {
    visitPsiElement(o);
  }

  public void visitForeignKeyClause(@NotNull AndroidSqlForeignKeyClause o) {
    visitPsiElement(o);
  }

  public void visitForeignTable(@NotNull AndroidSqlForeignTable o) {
    visitPsiElement(o);
  }

  public void visitFromClause(@NotNull AndroidSqlFromClause o) {
    visitPsiElement(o);
  }

  public void visitFromTable(@NotNull AndroidSqlFromTable o) {
    visitTableElement(o);
  }

  public void visitFunctionCallExpression(@NotNull AndroidSqlFunctionCallExpression o) {
    visitExpression(o);
  }

  public void visitGroupByClause(@NotNull AndroidSqlGroupByClause o) {
    visitPsiElement(o);
  }

  public void visitInExpression(@NotNull AndroidSqlInExpression o) {
    visitExpression(o);
  }

  public void visitIndexedColumn(@NotNull AndroidSqlIndexedColumn o) {
    visitPsiElement(o);
  }

  public void visitInsertColumns(@NotNull AndroidSqlInsertColumns o) {
    visitPsiElement(o);
  }

  public void visitInsertStatement(@NotNull AndroidSqlInsertStatement o) {
    visitPsiElement(o);
  }

  public void visitIsnullExpression(@NotNull AndroidSqlIsnullExpression o) {
    visitExpression(o);
  }

  public void visitJoinConstraint(@NotNull AndroidSqlJoinConstraint o) {
    visitPsiElement(o);
  }

  public void visitJoinOperator(@NotNull AndroidSqlJoinOperator o) {
    visitPsiElement(o);
  }

  public void visitLikeExpression(@NotNull AndroidSqlLikeExpression o) {
    visitExpression(o);
  }

  public void visitLimitClause(@NotNull AndroidSqlLimitClause o) {
    visitPsiElement(o);
  }

  public void visitLiteralExpression(@NotNull AndroidSqlLiteralExpression o) {
    visitExpression(o);
  }

  public void visitModuleArgument(@NotNull AndroidSqlModuleArgument o) {
    visitPsiElement(o);
  }

  public void visitModuleName(@NotNull AndroidSqlModuleName o) {
    visitNameElement(o);
  }

  public void visitMulExpression(@NotNull AndroidSqlMulExpression o) {
    visitExpression(o);
  }

  public void visitOrExpression(@NotNull AndroidSqlOrExpression o) {
    visitExpression(o);
  }

  public void visitOrderClause(@NotNull AndroidSqlOrderClause o) {
    visitPsiElement(o);
  }

  public void visitOrderingTerm(@NotNull AndroidSqlOrderingTerm o) {
    visitPsiElement(o);
  }

  public void visitParenExpression(@NotNull AndroidSqlParenExpression o) {
    visitExpression(o);
  }

  public void visitPragmaName(@NotNull AndroidSqlPragmaName o) {
    visitNameElement(o);
  }

  public void visitPragmaStatement(@NotNull AndroidSqlPragmaStatement o) {
    visitPsiElement(o);
  }

  public void visitPragmaValue(@NotNull AndroidSqlPragmaValue o) {
    visitPsiElement(o);
  }

  public void visitRaiseFunctionExpression(@NotNull AndroidSqlRaiseFunctionExpression o) {
    visitExpression(o);
  }

  public void visitReindexStatement(@NotNull AndroidSqlReindexStatement o) {
    visitPsiElement(o);
  }

  public void visitReleaseStatement(@NotNull AndroidSqlReleaseStatement o) {
    visitPsiElement(o);
  }

  public void visitResultColumn(@NotNull AndroidSqlResultColumn o) {
    visitPsiElement(o);
  }

  public void visitResultColumns(@NotNull AndroidSqlResultColumns o) {
    visitTableElement(o);
  }

  public void visitRollbackStatement(@NotNull AndroidSqlRollbackStatement o) {
    visitPsiElement(o);
  }

  public void visitSavepointName(@NotNull AndroidSqlSavepointName o) {
    visitNameElement(o);
  }

  public void visitSavepointStatement(@NotNull AndroidSqlSavepointStatement o) {
    visitPsiElement(o);
  }

  public void visitSelectCore(@NotNull AndroidSqlSelectCore o) {
    visitPsiElement(o);
  }

  public void visitSelectCoreSelect(@NotNull AndroidSqlSelectCoreSelect o) {
    visitPsiElement(o);
  }

  public void visitSelectCoreValues(@NotNull AndroidSqlSelectCoreValues o) {
    visitPsiElement(o);
  }

  public void visitSelectStatement(@NotNull AndroidSqlSelectStatement o) {
    visitPsiElement(o);
  }

  public void visitSelectSubquery(@NotNull AndroidSqlSelectSubquery o) {
    visitTableElement(o);
  }

  public void visitSelectedTableName(@NotNull AndroidSqlSelectedTableName o) {
    visitNameElement(o);
  }

  public void visitSignedNumber(@NotNull AndroidSqlSignedNumber o) {
    visitPsiElement(o);
  }

  public void visitSingleTableStatementTable(@NotNull AndroidSqlSingleTableStatementTable o) {
    visitTableElement(o);
  }

  public void visitTableAliasName(@NotNull AndroidSqlTableAliasName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitTableConstraint(@NotNull AndroidSqlTableConstraint o) {
    visitPsiElement(o);
  }

  public void visitTableDefinitionName(@NotNull AndroidSqlTableDefinitionName o) {
    visitPsiNamedElement(o);
    // visitNameElement(o);
  }

  public void visitTableOrIndexName(@NotNull AndroidSqlTableOrIndexName o) {
    visitNameElement(o);
  }

  public void visitTableOrSubquery(@NotNull AndroidSqlTableOrSubquery o) {
    visitPsiElement(o);
  }

  public void visitTriggerName(@NotNull AndroidSqlTriggerName o) {
    visitNameElement(o);
  }

  public void visitTypeName(@NotNull AndroidSqlTypeName o) {
    visitNameElement(o);
  }

  public void visitUnaryExpression(@NotNull AndroidSqlUnaryExpression o) {
    visitExpression(o);
  }

  public void visitUpdateStatement(@NotNull AndroidSqlUpdateStatement o) {
    visitPsiElement(o);
  }

  public void visitVacuumStatement(@NotNull AndroidSqlVacuumStatement o) {
    visitPsiElement(o);
  }

  public void visitViewName(@NotNull AndroidSqlViewName o) {
    visitNameElement(o);
  }

  public void visitWhereClause(@NotNull AndroidSqlWhereClause o) {
    visitPsiElement(o);
  }

  public void visitWithClause(@NotNull AndroidSqlWithClause o) {
    visitPsiElement(o);
  }

  public void visitWithClauseSelectStatement(@NotNull AndroidSqlWithClauseSelectStatement o) {
    visitHasWithClause(o);
  }

  public void visitWithClauseStatement(@NotNull AndroidSqlWithClauseStatement o) {
    visitHasWithClause(o);
  }

  public void visitWithClauseTable(@NotNull AndroidSqlWithClauseTable o) {
    visitPsiElement(o);
  }

  public void visitWithClauseTableDef(@NotNull AndroidSqlWithClauseTableDef o) {
    visitPsiElement(o);
  }

  public void visitNameElement(@NotNull AndroidSqlNameElement o) {
    visitPsiElement(o);
  }

  public void visitTableElement(@NotNull AndroidSqlTableElement o) {
    visitPsiElement(o);
  }

  public void visitHasWithClause(@NotNull HasWithClause o) {
    visitElement(o);
  }

  public void visitPsiNamedElement(@NotNull PsiNamedElement o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
