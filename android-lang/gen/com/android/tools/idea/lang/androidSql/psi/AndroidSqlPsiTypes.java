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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.androidSql.psi.impl.*;

public interface AndroidSqlPsiTypes {

  IElementType ADD_EXPRESSION = new AndroidSqlAstNodeType("ADD_EXPRESSION");
  IElementType ALTER_TABLE_STATEMENT = new AndroidSqlAstNodeType("ALTER_TABLE_STATEMENT");
  IElementType ANALYZE_STATEMENT = new AndroidSqlAstNodeType("ANALYZE_STATEMENT");
  IElementType AND_EXPRESSION = new AndroidSqlAstNodeType("AND_EXPRESSION");
  IElementType ATTACH_STATEMENT = new AndroidSqlAstNodeType("ATTACH_STATEMENT");
  IElementType BEGIN_STATEMENT = new AndroidSqlAstNodeType("BEGIN_STATEMENT");
  IElementType BETWEEN_EXPRESSION = new AndroidSqlAstNodeType("BETWEEN_EXPRESSION");
  IElementType BIND_PARAMETER = new AndroidSqlAstNodeType("BIND_PARAMETER");
  IElementType BIT_EXPRESSION = new AndroidSqlAstNodeType("BIT_EXPRESSION");
  IElementType CASE_EXPRESSION = new AndroidSqlAstNodeType("CASE_EXPRESSION");
  IElementType CAST_EXPRESSION = new AndroidSqlAstNodeType("CAST_EXPRESSION");
  IElementType COLLATE_EXPRESSION = new AndroidSqlAstNodeType("COLLATE_EXPRESSION");
  IElementType COLLATION_NAME = new AndroidSqlAstNodeType("COLLATION_NAME");
  IElementType COLUMN_ALIAS_NAME = new AndroidSqlAstNodeType("COLUMN_ALIAS_NAME");
  IElementType COLUMN_CONSTRAINT = new AndroidSqlAstNodeType("COLUMN_CONSTRAINT");
  IElementType COLUMN_DEFINITION = new AndroidSqlAstNodeType("COLUMN_DEFINITION");
  IElementType COLUMN_DEFINITION_NAME = new AndroidSqlAstNodeType("COLUMN_DEFINITION_NAME");
  IElementType COLUMN_NAME = new AndroidSqlAstNodeType("COLUMN_NAME");
  IElementType COLUMN_REF_EXPRESSION = new AndroidSqlAstNodeType("COLUMN_REF_EXPRESSION");
  IElementType COMMIT_STATEMENT = new AndroidSqlAstNodeType("COMMIT_STATEMENT");
  IElementType COMPARISON_EXPRESSION = new AndroidSqlAstNodeType("COMPARISON_EXPRESSION");
  IElementType COMPOUND_OPERATOR = new AndroidSqlAstNodeType("COMPOUND_OPERATOR");
  IElementType CONCAT_EXPRESSION = new AndroidSqlAstNodeType("CONCAT_EXPRESSION");
  IElementType CONFLICT_CLAUSE = new AndroidSqlAstNodeType("CONFLICT_CLAUSE");
  IElementType CREATE_INDEX_STATEMENT = new AndroidSqlAstNodeType("CREATE_INDEX_STATEMENT");
  IElementType CREATE_TABLE_STATEMENT = new AndroidSqlAstNodeType("CREATE_TABLE_STATEMENT");
  IElementType CREATE_TRIGGER_STATEMENT = new AndroidSqlAstNodeType("CREATE_TRIGGER_STATEMENT");
  IElementType CREATE_VIEW_STATEMENT = new AndroidSqlAstNodeType("CREATE_VIEW_STATEMENT");
  IElementType CREATE_VIRTUAL_TABLE_STATEMENT = new AndroidSqlAstNodeType("CREATE_VIRTUAL_TABLE_STATEMENT");
  IElementType DATABASE_NAME = new AndroidSqlAstNodeType("DATABASE_NAME");
  IElementType DEFINED_TABLE_NAME = new AndroidSqlAstNodeType("DEFINED_TABLE_NAME");
  IElementType DELETE_STATEMENT = new AndroidSqlAstNodeType("DELETE_STATEMENT");
  IElementType DETACH_STATEMENT = new AndroidSqlAstNodeType("DETACH_STATEMENT");
  IElementType DROP_INDEX_STATEMENT = new AndroidSqlAstNodeType("DROP_INDEX_STATEMENT");
  IElementType DROP_TABLE_STATEMENT = new AndroidSqlAstNodeType("DROP_TABLE_STATEMENT");
  IElementType DROP_TRIGGER_STATEMENT = new AndroidSqlAstNodeType("DROP_TRIGGER_STATEMENT");
  IElementType DROP_VIEW_STATEMENT = new AndroidSqlAstNodeType("DROP_VIEW_STATEMENT");
  IElementType EQUIVALENCE_EXPRESSION = new AndroidSqlAstNodeType("EQUIVALENCE_EXPRESSION");
  IElementType ERROR_MESSAGE = new AndroidSqlAstNodeType("ERROR_MESSAGE");
  IElementType EXISTS_EXPRESSION = new AndroidSqlAstNodeType("EXISTS_EXPRESSION");
  IElementType EXPLAIN_PREFIX = new AndroidSqlAstNodeType("EXPLAIN_PREFIX");
  IElementType EXPRESSION = new AndroidSqlAstNodeType("EXPRESSION");
  IElementType FOREIGN_KEY_CLAUSE = new AndroidSqlAstNodeType("FOREIGN_KEY_CLAUSE");
  IElementType FOREIGN_TABLE = new AndroidSqlAstNodeType("FOREIGN_TABLE");
  IElementType FROM_CLAUSE = new AndroidSqlAstNodeType("FROM_CLAUSE");
  IElementType FROM_TABLE = new AndroidSqlAstNodeType("FROM_TABLE");
  IElementType FUNCTION_CALL_EXPRESSION = new AndroidSqlAstNodeType("FUNCTION_CALL_EXPRESSION");
  IElementType GROUP_BY_CLAUSE = new AndroidSqlAstNodeType("GROUP_BY_CLAUSE");
  IElementType INDEXED_COLUMN = new AndroidSqlAstNodeType("INDEXED_COLUMN");
  IElementType INSERT_COLUMNS = new AndroidSqlAstNodeType("INSERT_COLUMNS");
  IElementType INSERT_STATEMENT = new AndroidSqlAstNodeType("INSERT_STATEMENT");
  IElementType IN_EXPRESSION = new AndroidSqlAstNodeType("IN_EXPRESSION");
  IElementType ISNULL_EXPRESSION = new AndroidSqlAstNodeType("ISNULL_EXPRESSION");
  IElementType JOIN_CONSTRAINT = new AndroidSqlAstNodeType("JOIN_CONSTRAINT");
  IElementType JOIN_OPERATOR = new AndroidSqlAstNodeType("JOIN_OPERATOR");
  IElementType LIKE_EXPRESSION = new AndroidSqlAstNodeType("LIKE_EXPRESSION");
  IElementType LIMIT_CLAUSE = new AndroidSqlAstNodeType("LIMIT_CLAUSE");
  IElementType LITERAL_EXPRESSION = new AndroidSqlAstNodeType("LITERAL_EXPRESSION");
  IElementType MODULE_ARGUMENT = new AndroidSqlAstNodeType("MODULE_ARGUMENT");
  IElementType MODULE_NAME = new AndroidSqlAstNodeType("MODULE_NAME");
  IElementType MUL_EXPRESSION = new AndroidSqlAstNodeType("MUL_EXPRESSION");
  IElementType ORDERING_TERM = new AndroidSqlAstNodeType("ORDERING_TERM");
  IElementType ORDER_CLAUSE = new AndroidSqlAstNodeType("ORDER_CLAUSE");
  IElementType OR_EXPRESSION = new AndroidSqlAstNodeType("OR_EXPRESSION");
  IElementType PAREN_EXPRESSION = new AndroidSqlAstNodeType("PAREN_EXPRESSION");
  IElementType PRAGMA_NAME = new AndroidSqlAstNodeType("PRAGMA_NAME");
  IElementType PRAGMA_STATEMENT = new AndroidSqlAstNodeType("PRAGMA_STATEMENT");
  IElementType PRAGMA_VALUE = new AndroidSqlAstNodeType("PRAGMA_VALUE");
  IElementType RAISE_FUNCTION_EXPRESSION = new AndroidSqlAstNodeType("RAISE_FUNCTION_EXPRESSION");
  IElementType REINDEX_STATEMENT = new AndroidSqlAstNodeType("REINDEX_STATEMENT");
  IElementType RELEASE_STATEMENT = new AndroidSqlAstNodeType("RELEASE_STATEMENT");
  IElementType RESULT_COLUMN = new AndroidSqlAstNodeType("RESULT_COLUMN");
  IElementType RESULT_COLUMNS = new AndroidSqlAstNodeType("RESULT_COLUMNS");
  IElementType ROLLBACK_STATEMENT = new AndroidSqlAstNodeType("ROLLBACK_STATEMENT");
  IElementType SAVEPOINT_NAME = new AndroidSqlAstNodeType("SAVEPOINT_NAME");
  IElementType SAVEPOINT_STATEMENT = new AndroidSqlAstNodeType("SAVEPOINT_STATEMENT");
  IElementType SELECTED_TABLE_NAME = new AndroidSqlAstNodeType("SELECTED_TABLE_NAME");
  IElementType SELECT_CORE = new AndroidSqlAstNodeType("SELECT_CORE");
  IElementType SELECT_CORE_SELECT = new AndroidSqlAstNodeType("SELECT_CORE_SELECT");
  IElementType SELECT_CORE_VALUES = new AndroidSqlAstNodeType("SELECT_CORE_VALUES");
  IElementType SELECT_STATEMENT = new AndroidSqlAstNodeType("SELECT_STATEMENT");
  IElementType SELECT_SUBQUERY = new AndroidSqlAstNodeType("SELECT_SUBQUERY");
  IElementType SIGNED_NUMBER = new AndroidSqlAstNodeType("SIGNED_NUMBER");
  IElementType SINGLE_TABLE_STATEMENT_TABLE = new AndroidSqlAstNodeType("SINGLE_TABLE_STATEMENT_TABLE");
  IElementType TABLE_ALIAS_NAME = new AndroidSqlAstNodeType("TABLE_ALIAS_NAME");
  IElementType TABLE_CONSTRAINT = new AndroidSqlAstNodeType("TABLE_CONSTRAINT");
  IElementType TABLE_DEFINITION_NAME = new AndroidSqlAstNodeType("TABLE_DEFINITION_NAME");
  IElementType TABLE_OR_INDEX_NAME = new AndroidSqlAstNodeType("TABLE_OR_INDEX_NAME");
  IElementType TABLE_OR_SUBQUERY = new AndroidSqlAstNodeType("TABLE_OR_SUBQUERY");
  IElementType TRIGGER_NAME = new AndroidSqlAstNodeType("TRIGGER_NAME");
  IElementType TYPE_NAME = new AndroidSqlAstNodeType("TYPE_NAME");
  IElementType UNARY_EXPRESSION = new AndroidSqlAstNodeType("UNARY_EXPRESSION");
  IElementType UPDATE_STATEMENT = new AndroidSqlAstNodeType("UPDATE_STATEMENT");
  IElementType VACUUM_STATEMENT = new AndroidSqlAstNodeType("VACUUM_STATEMENT");
  IElementType VIEW_NAME = new AndroidSqlAstNodeType("VIEW_NAME");
  IElementType WHERE_CLAUSE = new AndroidSqlAstNodeType("WHERE_CLAUSE");
  IElementType WITH_CLAUSE = new AndroidSqlAstNodeType("WITH_CLAUSE");
  IElementType WITH_CLAUSE_SELECT_STATEMENT = new AndroidSqlAstNodeType("WITH_CLAUSE_SELECT_STATEMENT");
  IElementType WITH_CLAUSE_STATEMENT = new AndroidSqlAstNodeType("WITH_CLAUSE_STATEMENT");
  IElementType WITH_CLAUSE_TABLE = new AndroidSqlAstNodeType("WITH_CLAUSE_TABLE");
  IElementType WITH_CLAUSE_TABLE_DEF = new AndroidSqlAstNodeType("WITH_CLAUSE_TABLE_DEF");

  IElementType ABORT = new AndroidSqlTokenType("ABORT");
  IElementType ACTION = new AndroidSqlTokenType("ACTION");
  IElementType ADD = new AndroidSqlTokenType("ADD");
  IElementType AFTER = new AndroidSqlTokenType("AFTER");
  IElementType ALL = new AndroidSqlTokenType("ALL");
  IElementType ALTER = new AndroidSqlTokenType("ALTER");
  IElementType AMP = new AndroidSqlTokenType("&");
  IElementType ANALYZE = new AndroidSqlTokenType("ANALYZE");
  IElementType AND = new AndroidSqlTokenType("AND");
  IElementType AS = new AndroidSqlTokenType("AS");
  IElementType ASC = new AndroidSqlTokenType("ASC");
  IElementType ATTACH = new AndroidSqlTokenType("ATTACH");
  IElementType AUTOINCREMENT = new AndroidSqlTokenType("AUTOINCREMENT");
  IElementType BACKTICK_LITERAL = new AndroidSqlTokenType("BACKTICK_LITERAL");
  IElementType BAR = new AndroidSqlTokenType("|");
  IElementType BEFORE = new AndroidSqlTokenType("BEFORE");
  IElementType BEGIN = new AndroidSqlTokenType("BEGIN");
  IElementType BETWEEN = new AndroidSqlTokenType("BETWEEN");
  IElementType BRACKET_LITERAL = new AndroidSqlTokenType("BRACKET_LITERAL");
  IElementType BY = new AndroidSqlTokenType("BY");
  IElementType CASCADE = new AndroidSqlTokenType("CASCADE");
  IElementType CASE = new AndroidSqlTokenType("CASE");
  IElementType CAST = new AndroidSqlTokenType("CAST");
  IElementType CHECK = new AndroidSqlTokenType("CHECK");
  IElementType COLLATE = new AndroidSqlTokenType("COLLATE");
  IElementType COLUMN = new AndroidSqlTokenType("COLUMN");
  IElementType COMMA = new AndroidSqlTokenType(",");
  IElementType COMMENT = new AndroidSqlTokenType("COMMENT");
  IElementType COMMIT = new AndroidSqlTokenType("COMMIT");
  IElementType CONCAT = new AndroidSqlTokenType("||");
  IElementType CONFLICT = new AndroidSqlTokenType("CONFLICT");
  IElementType CONSTRAINT = new AndroidSqlTokenType("CONSTRAINT");
  IElementType CREATE = new AndroidSqlTokenType("CREATE");
  IElementType CROSS = new AndroidSqlTokenType("CROSS");
  IElementType CURRENT_DATE = new AndroidSqlTokenType("CURRENT_DATE");
  IElementType CURRENT_TIME = new AndroidSqlTokenType("CURRENT_TIME");
  IElementType CURRENT_TIMESTAMP = new AndroidSqlTokenType("CURRENT_TIMESTAMP");
  IElementType DATABASE = new AndroidSqlTokenType("DATABASE");
  IElementType DEFAULT = new AndroidSqlTokenType("DEFAULT");
  IElementType DEFERRABLE = new AndroidSqlTokenType("DEFERRABLE");
  IElementType DEFERRED = new AndroidSqlTokenType("DEFERRED");
  IElementType DELETE = new AndroidSqlTokenType("DELETE");
  IElementType DESC = new AndroidSqlTokenType("DESC");
  IElementType DETACH = new AndroidSqlTokenType("DETACH");
  IElementType DISTINCT = new AndroidSqlTokenType("DISTINCT");
  IElementType DIV = new AndroidSqlTokenType("/");
  IElementType DOT = new AndroidSqlTokenType(".");
  IElementType DOUBLE_QUOTE_STRING_LITERAL = new AndroidSqlTokenType("DOUBLE_QUOTE_STRING_LITERAL");
  IElementType DROP = new AndroidSqlTokenType("DROP");
  IElementType EACH = new AndroidSqlTokenType("EACH");
  IElementType ELSE = new AndroidSqlTokenType("ELSE");
  IElementType END = new AndroidSqlTokenType("END");
  IElementType EQ = new AndroidSqlTokenType("=");
  IElementType EQEQ = new AndroidSqlTokenType("==");
  IElementType ESCAPE = new AndroidSqlTokenType("ESCAPE");
  IElementType EXCEPT = new AndroidSqlTokenType("EXCEPT");
  IElementType EXCLUSIVE = new AndroidSqlTokenType("EXCLUSIVE");
  IElementType EXISTS = new AndroidSqlTokenType("EXISTS");
  IElementType EXPLAIN = new AndroidSqlTokenType("EXPLAIN");
  IElementType FAIL = new AndroidSqlTokenType("FAIL");
  IElementType FOR = new AndroidSqlTokenType("FOR");
  IElementType FOREIGN = new AndroidSqlTokenType("FOREIGN");
  IElementType FROM = new AndroidSqlTokenType("FROM");
  IElementType FULL = new AndroidSqlTokenType("FULL");
  IElementType GLOB = new AndroidSqlTokenType("GLOB");
  IElementType GROUP = new AndroidSqlTokenType("GROUP");
  IElementType GT = new AndroidSqlTokenType(">");
  IElementType GTE = new AndroidSqlTokenType(">=");
  IElementType HAVING = new AndroidSqlTokenType("HAVING");
  IElementType IDENTIFIER = new AndroidSqlTokenType("IDENTIFIER");
  IElementType IF = new AndroidSqlTokenType("IF");
  IElementType IGNORE = new AndroidSqlTokenType("IGNORE");
  IElementType IMMEDIATE = new AndroidSqlTokenType("IMMEDIATE");
  IElementType IN = new AndroidSqlTokenType("IN");
  IElementType INDEX = new AndroidSqlTokenType("INDEX");
  IElementType INDEXED = new AndroidSqlTokenType("INDEXED");
  IElementType INITIALLY = new AndroidSqlTokenType("INITIALLY");
  IElementType INNER = new AndroidSqlTokenType("INNER");
  IElementType INSERT = new AndroidSqlTokenType("INSERT");
  IElementType INSTEAD = new AndroidSqlTokenType("INSTEAD");
  IElementType INTERSECT = new AndroidSqlTokenType("INTERSECT");
  IElementType INTO = new AndroidSqlTokenType("INTO");
  IElementType IS = new AndroidSqlTokenType("IS");
  IElementType ISNULL = new AndroidSqlTokenType("ISNULL");
  IElementType JOIN = new AndroidSqlTokenType("JOIN");
  IElementType KEY = new AndroidSqlTokenType("KEY");
  IElementType LEFT = new AndroidSqlTokenType("LEFT");
  IElementType LIKE = new AndroidSqlTokenType("LIKE");
  IElementType LIMIT = new AndroidSqlTokenType("LIMIT");
  IElementType LINE_COMMENT = new AndroidSqlTokenType("LINE_COMMENT");
  IElementType LPAREN = new AndroidSqlTokenType("(");
  IElementType LT = new AndroidSqlTokenType("<");
  IElementType LTE = new AndroidSqlTokenType("<=");
  IElementType MATCH = new AndroidSqlTokenType("MATCH");
  IElementType MINUS = new AndroidSqlTokenType("-");
  IElementType MOD = new AndroidSqlTokenType("%");
  IElementType NAMED_PARAMETER = new AndroidSqlTokenType("NAMED_PARAMETER");
  IElementType NATURAL = new AndroidSqlTokenType("NATURAL");
  IElementType NO = new AndroidSqlTokenType("NO");
  IElementType NOT = new AndroidSqlTokenType("NOT");
  IElementType NOTNULL = new AndroidSqlTokenType("NOTNULL");
  IElementType NOT_EQ = new AndroidSqlTokenType("!=");
  IElementType NULL = new AndroidSqlTokenType("NULL");
  IElementType NUMBERED_PARAMETER = new AndroidSqlTokenType("NUMBERED_PARAMETER");
  IElementType NUMERIC_LITERAL = new AndroidSqlTokenType("NUMERIC_LITERAL");
  IElementType OF = new AndroidSqlTokenType("OF");
  IElementType OFFSET = new AndroidSqlTokenType("OFFSET");
  IElementType ON = new AndroidSqlTokenType("ON");
  IElementType OR = new AndroidSqlTokenType("OR");
  IElementType ORDER = new AndroidSqlTokenType("ORDER");
  IElementType OUTER = new AndroidSqlTokenType("OUTER");
  IElementType PLAN = new AndroidSqlTokenType("PLAN");
  IElementType PLUS = new AndroidSqlTokenType("+");
  IElementType PRAGMA = new AndroidSqlTokenType("PRAGMA");
  IElementType PRIMARY = new AndroidSqlTokenType("PRIMARY");
  IElementType QUERY = new AndroidSqlTokenType("QUERY");
  IElementType RAISE = new AndroidSqlTokenType("RAISE");
  IElementType RECURSIVE = new AndroidSqlTokenType("RECURSIVE");
  IElementType REFERENCES = new AndroidSqlTokenType("REFERENCES");
  IElementType REGEXP = new AndroidSqlTokenType("REGEXP");
  IElementType REINDEX = new AndroidSqlTokenType("REINDEX");
  IElementType RELEASE = new AndroidSqlTokenType("RELEASE");
  IElementType RENAME = new AndroidSqlTokenType("RENAME");
  IElementType REPLACE = new AndroidSqlTokenType("REPLACE");
  IElementType RESTRICT = new AndroidSqlTokenType("RESTRICT");
  IElementType RIGHT = new AndroidSqlTokenType("RIGHT");
  IElementType ROLLBACK = new AndroidSqlTokenType("ROLLBACK");
  IElementType ROW = new AndroidSqlTokenType("ROW");
  IElementType RPAREN = new AndroidSqlTokenType(")");
  IElementType SAVEPOINT = new AndroidSqlTokenType("SAVEPOINT");
  IElementType SELECT = new AndroidSqlTokenType("SELECT");
  IElementType SEMICOLON = new AndroidSqlTokenType(";");
  IElementType SET = new AndroidSqlTokenType("SET");
  IElementType SHL = new AndroidSqlTokenType("<<");
  IElementType SHR = new AndroidSqlTokenType(">>");
  IElementType SINGLE_QUOTE_STRING_LITERAL = new AndroidSqlTokenType("SINGLE_QUOTE_STRING_LITERAL");
  IElementType STAR = new AndroidSqlTokenType("*");
  IElementType TABLE = new AndroidSqlTokenType("TABLE");
  IElementType TEMP = new AndroidSqlTokenType("TEMP");
  IElementType TEMPORARY = new AndroidSqlTokenType("TEMPORARY");
  IElementType THEN = new AndroidSqlTokenType("THEN");
  IElementType TILDE = new AndroidSqlTokenType("~");
  IElementType TO = new AndroidSqlTokenType("TO");
  IElementType TRANSACTION = new AndroidSqlTokenType("TRANSACTION");
  IElementType TRIGGER = new AndroidSqlTokenType("TRIGGER");
  IElementType UNEQ = new AndroidSqlTokenType("<>");
  IElementType UNION = new AndroidSqlTokenType("UNION");
  IElementType UNIQUE = new AndroidSqlTokenType("UNIQUE");
  IElementType UPDATE = new AndroidSqlTokenType("UPDATE");
  IElementType USING = new AndroidSqlTokenType("USING");
  IElementType VACUUM = new AndroidSqlTokenType("VACUUM");
  IElementType VALUES = new AndroidSqlTokenType("VALUES");
  IElementType VIEW = new AndroidSqlTokenType("VIEW");
  IElementType VIRTUAL = new AndroidSqlTokenType("VIRTUAL");
  IElementType WHEN = new AndroidSqlTokenType("WHEN");
  IElementType WHERE = new AndroidSqlTokenType("WHERE");
  IElementType WITH = new AndroidSqlTokenType("WITH");
  IElementType WITHOUT = new AndroidSqlTokenType("WITHOUT");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADD_EXPRESSION) {
        return new AndroidSqlAddExpressionImpl(node);
      }
      else if (type == ALTER_TABLE_STATEMENT) {
        return new AndroidSqlAlterTableStatementImpl(node);
      }
      else if (type == ANALYZE_STATEMENT) {
        return new AndroidSqlAnalyzeStatementImpl(node);
      }
      else if (type == AND_EXPRESSION) {
        return new AndroidSqlAndExpressionImpl(node);
      }
      else if (type == ATTACH_STATEMENT) {
        return new AndroidSqlAttachStatementImpl(node);
      }
      else if (type == BEGIN_STATEMENT) {
        return new AndroidSqlBeginStatementImpl(node);
      }
      else if (type == BETWEEN_EXPRESSION) {
        return new AndroidSqlBetweenExpressionImpl(node);
      }
      else if (type == BIND_PARAMETER) {
        return new AndroidSqlBindParameterImpl(node);
      }
      else if (type == BIT_EXPRESSION) {
        return new AndroidSqlBitExpressionImpl(node);
      }
      else if (type == CASE_EXPRESSION) {
        return new AndroidSqlCaseExpressionImpl(node);
      }
      else if (type == CAST_EXPRESSION) {
        return new AndroidSqlCastExpressionImpl(node);
      }
      else if (type == COLLATE_EXPRESSION) {
        return new AndroidSqlCollateExpressionImpl(node);
      }
      else if (type == COLLATION_NAME) {
        return new AndroidSqlCollationNameImpl(node);
      }
      else if (type == COLUMN_ALIAS_NAME) {
        return new AndroidSqlColumnAliasNameImpl(node);
      }
      else if (type == COLUMN_CONSTRAINT) {
        return new AndroidSqlColumnConstraintImpl(node);
      }
      else if (type == COLUMN_DEFINITION) {
        return new AndroidSqlColumnDefinitionImpl(node);
      }
      else if (type == COLUMN_DEFINITION_NAME) {
        return new AndroidSqlColumnDefinitionNameImpl(node);
      }
      else if (type == COLUMN_NAME) {
        return new AndroidSqlColumnNameImpl(node);
      }
      else if (type == COLUMN_REF_EXPRESSION) {
        return new AndroidSqlColumnRefExpressionImpl(node);
      }
      else if (type == COMMIT_STATEMENT) {
        return new AndroidSqlCommitStatementImpl(node);
      }
      else if (type == COMPARISON_EXPRESSION) {
        return new AndroidSqlComparisonExpressionImpl(node);
      }
      else if (type == COMPOUND_OPERATOR) {
        return new AndroidSqlCompoundOperatorImpl(node);
      }
      else if (type == CONCAT_EXPRESSION) {
        return new AndroidSqlConcatExpressionImpl(node);
      }
      else if (type == CONFLICT_CLAUSE) {
        return new AndroidSqlConflictClauseImpl(node);
      }
      else if (type == CREATE_INDEX_STATEMENT) {
        return new AndroidSqlCreateIndexStatementImpl(node);
      }
      else if (type == CREATE_TABLE_STATEMENT) {
        return new AndroidSqlCreateTableStatementImpl(node);
      }
      else if (type == CREATE_TRIGGER_STATEMENT) {
        return new AndroidSqlCreateTriggerStatementImpl(node);
      }
      else if (type == CREATE_VIEW_STATEMENT) {
        return new AndroidSqlCreateViewStatementImpl(node);
      }
      else if (type == CREATE_VIRTUAL_TABLE_STATEMENT) {
        return new AndroidSqlCreateVirtualTableStatementImpl(node);
      }
      else if (type == DATABASE_NAME) {
        return new AndroidSqlDatabaseNameImpl(node);
      }
      else if (type == DEFINED_TABLE_NAME) {
        return new AndroidSqlDefinedTableNameImpl(node);
      }
      else if (type == DELETE_STATEMENT) {
        return new AndroidSqlDeleteStatementImpl(node);
      }
      else if (type == DETACH_STATEMENT) {
        return new AndroidSqlDetachStatementImpl(node);
      }
      else if (type == DROP_INDEX_STATEMENT) {
        return new AndroidSqlDropIndexStatementImpl(node);
      }
      else if (type == DROP_TABLE_STATEMENT) {
        return new AndroidSqlDropTableStatementImpl(node);
      }
      else if (type == DROP_TRIGGER_STATEMENT) {
        return new AndroidSqlDropTriggerStatementImpl(node);
      }
      else if (type == DROP_VIEW_STATEMENT) {
        return new AndroidSqlDropViewStatementImpl(node);
      }
      else if (type == EQUIVALENCE_EXPRESSION) {
        return new AndroidSqlEquivalenceExpressionImpl(node);
      }
      else if (type == ERROR_MESSAGE) {
        return new AndroidSqlErrorMessageImpl(node);
      }
      else if (type == EXISTS_EXPRESSION) {
        return new AndroidSqlExistsExpressionImpl(node);
      }
      else if (type == EXPLAIN_PREFIX) {
        return new AndroidSqlExplainPrefixImpl(node);
      }
      else if (type == FOREIGN_KEY_CLAUSE) {
        return new AndroidSqlForeignKeyClauseImpl(node);
      }
      else if (type == FOREIGN_TABLE) {
        return new AndroidSqlForeignTableImpl(node);
      }
      else if (type == FROM_CLAUSE) {
        return new AndroidSqlFromClauseImpl(node);
      }
      else if (type == FROM_TABLE) {
        return new AndroidSqlFromTableImpl(node);
      }
      else if (type == FUNCTION_CALL_EXPRESSION) {
        return new AndroidSqlFunctionCallExpressionImpl(node);
      }
      else if (type == GROUP_BY_CLAUSE) {
        return new AndroidSqlGroupByClauseImpl(node);
      }
      else if (type == INDEXED_COLUMN) {
        return new AndroidSqlIndexedColumnImpl(node);
      }
      else if (type == INSERT_COLUMNS) {
        return new AndroidSqlInsertColumnsImpl(node);
      }
      else if (type == INSERT_STATEMENT) {
        return new AndroidSqlInsertStatementImpl(node);
      }
      else if (type == IN_EXPRESSION) {
        return new AndroidSqlInExpressionImpl(node);
      }
      else if (type == ISNULL_EXPRESSION) {
        return new AndroidSqlIsnullExpressionImpl(node);
      }
      else if (type == JOIN_CONSTRAINT) {
        return new AndroidSqlJoinConstraintImpl(node);
      }
      else if (type == JOIN_OPERATOR) {
        return new AndroidSqlJoinOperatorImpl(node);
      }
      else if (type == LIKE_EXPRESSION) {
        return new AndroidSqlLikeExpressionImpl(node);
      }
      else if (type == LIMIT_CLAUSE) {
        return new AndroidSqlLimitClauseImpl(node);
      }
      else if (type == LITERAL_EXPRESSION) {
        return new AndroidSqlLiteralExpressionImpl(node);
      }
      else if (type == MODULE_ARGUMENT) {
        return new AndroidSqlModuleArgumentImpl(node);
      }
      else if (type == MODULE_NAME) {
        return new AndroidSqlModuleNameImpl(node);
      }
      else if (type == MUL_EXPRESSION) {
        return new AndroidSqlMulExpressionImpl(node);
      }
      else if (type == ORDERING_TERM) {
        return new AndroidSqlOrderingTermImpl(node);
      }
      else if (type == ORDER_CLAUSE) {
        return new AndroidSqlOrderClauseImpl(node);
      }
      else if (type == OR_EXPRESSION) {
        return new AndroidSqlOrExpressionImpl(node);
      }
      else if (type == PAREN_EXPRESSION) {
        return new AndroidSqlParenExpressionImpl(node);
      }
      else if (type == PRAGMA_NAME) {
        return new AndroidSqlPragmaNameImpl(node);
      }
      else if (type == PRAGMA_STATEMENT) {
        return new AndroidSqlPragmaStatementImpl(node);
      }
      else if (type == PRAGMA_VALUE) {
        return new AndroidSqlPragmaValueImpl(node);
      }
      else if (type == RAISE_FUNCTION_EXPRESSION) {
        return new AndroidSqlRaiseFunctionExpressionImpl(node);
      }
      else if (type == REINDEX_STATEMENT) {
        return new AndroidSqlReindexStatementImpl(node);
      }
      else if (type == RELEASE_STATEMENT) {
        return new AndroidSqlReleaseStatementImpl(node);
      }
      else if (type == RESULT_COLUMN) {
        return new AndroidSqlResultColumnImpl(node);
      }
      else if (type == RESULT_COLUMNS) {
        return new AndroidSqlResultColumnsImpl(node);
      }
      else if (type == ROLLBACK_STATEMENT) {
        return new AndroidSqlRollbackStatementImpl(node);
      }
      else if (type == SAVEPOINT_NAME) {
        return new AndroidSqlSavepointNameImpl(node);
      }
      else if (type == SAVEPOINT_STATEMENT) {
        return new AndroidSqlSavepointStatementImpl(node);
      }
      else if (type == SELECTED_TABLE_NAME) {
        return new AndroidSqlSelectedTableNameImpl(node);
      }
      else if (type == SELECT_CORE) {
        return new AndroidSqlSelectCoreImpl(node);
      }
      else if (type == SELECT_CORE_SELECT) {
        return new AndroidSqlSelectCoreSelectImpl(node);
      }
      else if (type == SELECT_CORE_VALUES) {
        return new AndroidSqlSelectCoreValuesImpl(node);
      }
      else if (type == SELECT_STATEMENT) {
        return new AndroidSqlSelectStatementImpl(node);
      }
      else if (type == SELECT_SUBQUERY) {
        return new AndroidSqlSelectSubqueryImpl(node);
      }
      else if (type == SIGNED_NUMBER) {
        return new AndroidSqlSignedNumberImpl(node);
      }
      else if (type == SINGLE_TABLE_STATEMENT_TABLE) {
        return new AndroidSqlSingleTableStatementTableImpl(node);
      }
      else if (type == TABLE_ALIAS_NAME) {
        return new AndroidSqlTableAliasNameImpl(node);
      }
      else if (type == TABLE_CONSTRAINT) {
        return new AndroidSqlTableConstraintImpl(node);
      }
      else if (type == TABLE_DEFINITION_NAME) {
        return new AndroidSqlTableDefinitionNameImpl(node);
      }
      else if (type == TABLE_OR_INDEX_NAME) {
        return new AndroidSqlTableOrIndexNameImpl(node);
      }
      else if (type == TABLE_OR_SUBQUERY) {
        return new AndroidSqlTableOrSubqueryImpl(node);
      }
      else if (type == TRIGGER_NAME) {
        return new AndroidSqlTriggerNameImpl(node);
      }
      else if (type == TYPE_NAME) {
        return new AndroidSqlTypeNameImpl(node);
      }
      else if (type == UNARY_EXPRESSION) {
        return new AndroidSqlUnaryExpressionImpl(node);
      }
      else if (type == UPDATE_STATEMENT) {
        return new AndroidSqlUpdateStatementImpl(node);
      }
      else if (type == VACUUM_STATEMENT) {
        return new AndroidSqlVacuumStatementImpl(node);
      }
      else if (type == VIEW_NAME) {
        return new AndroidSqlViewNameImpl(node);
      }
      else if (type == WHERE_CLAUSE) {
        return new AndroidSqlWhereClauseImpl(node);
      }
      else if (type == WITH_CLAUSE) {
        return new AndroidSqlWithClauseImpl(node);
      }
      else if (type == WITH_CLAUSE_SELECT_STATEMENT) {
        return new AndroidSqlWithClauseSelectStatementImpl(node);
      }
      else if (type == WITH_CLAUSE_STATEMENT) {
        return new AndroidSqlWithClauseStatementImpl(node);
      }
      else if (type == WITH_CLAUSE_TABLE) {
        return new AndroidSqlWithClauseTableImpl(node);
      }
      else if (type == WITH_CLAUSE_TABLE_DEF) {
        return new AndroidSqlWithClauseTableDefImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
