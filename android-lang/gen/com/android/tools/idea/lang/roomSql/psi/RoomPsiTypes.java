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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.roomSql.psi.impl.*;

public interface RoomPsiTypes {

  IElementType ADD_EXPRESSION = new RoomAstNodeType("ADD_EXPRESSION");
  IElementType ALTER_TABLE_STATEMENT = new RoomAstNodeType("ALTER_TABLE_STATEMENT");
  IElementType ANALYZE_STATEMENT = new RoomAstNodeType("ANALYZE_STATEMENT");
  IElementType AND_EXPRESSION = new RoomAstNodeType("AND_EXPRESSION");
  IElementType ATTACH_STATEMENT = new RoomAstNodeType("ATTACH_STATEMENT");
  IElementType BEGIN_STATEMENT = new RoomAstNodeType("BEGIN_STATEMENT");
  IElementType BETWEEN_EXPRESSION = new RoomAstNodeType("BETWEEN_EXPRESSION");
  IElementType BIND_PARAMETER = new RoomAstNodeType("BIND_PARAMETER");
  IElementType BIT_EXPRESSION = new RoomAstNodeType("BIT_EXPRESSION");
  IElementType CASE_EXPRESSION = new RoomAstNodeType("CASE_EXPRESSION");
  IElementType CAST_EXPRESSION = new RoomAstNodeType("CAST_EXPRESSION");
  IElementType COLLATE_EXPRESSION = new RoomAstNodeType("COLLATE_EXPRESSION");
  IElementType COLLATION_NAME = new RoomAstNodeType("COLLATION_NAME");
  IElementType COLUMN_ALIAS_NAME = new RoomAstNodeType("COLUMN_ALIAS_NAME");
  IElementType COLUMN_CONSTRAINT = new RoomAstNodeType("COLUMN_CONSTRAINT");
  IElementType COLUMN_DEFINITION = new RoomAstNodeType("COLUMN_DEFINITION");
  IElementType COLUMN_DEFINITION_NAME = new RoomAstNodeType("COLUMN_DEFINITION_NAME");
  IElementType COLUMN_NAME = new RoomAstNodeType("COLUMN_NAME");
  IElementType COLUMN_REF_EXPRESSION = new RoomAstNodeType("COLUMN_REF_EXPRESSION");
  IElementType COMMIT_STATEMENT = new RoomAstNodeType("COMMIT_STATEMENT");
  IElementType COMPARISON_EXPRESSION = new RoomAstNodeType("COMPARISON_EXPRESSION");
  IElementType COMPOUND_OPERATOR = new RoomAstNodeType("COMPOUND_OPERATOR");
  IElementType CONCAT_EXPRESSION = new RoomAstNodeType("CONCAT_EXPRESSION");
  IElementType CONFLICT_CLAUSE = new RoomAstNodeType("CONFLICT_CLAUSE");
  IElementType CREATE_INDEX_STATEMENT = new RoomAstNodeType("CREATE_INDEX_STATEMENT");
  IElementType CREATE_TABLE_STATEMENT = new RoomAstNodeType("CREATE_TABLE_STATEMENT");
  IElementType CREATE_TRIGGER_STATEMENT = new RoomAstNodeType("CREATE_TRIGGER_STATEMENT");
  IElementType CREATE_VIEW_STATEMENT = new RoomAstNodeType("CREATE_VIEW_STATEMENT");
  IElementType CREATE_VIRTUAL_TABLE_STATEMENT = new RoomAstNodeType("CREATE_VIRTUAL_TABLE_STATEMENT");
  IElementType DATABASE_NAME = new RoomAstNodeType("DATABASE_NAME");
  IElementType DEFINED_TABLE_NAME = new RoomAstNodeType("DEFINED_TABLE_NAME");
  IElementType DELETE_STATEMENT = new RoomAstNodeType("DELETE_STATEMENT");
  IElementType DETACH_STATEMENT = new RoomAstNodeType("DETACH_STATEMENT");
  IElementType DROP_INDEX_STATEMENT = new RoomAstNodeType("DROP_INDEX_STATEMENT");
  IElementType DROP_TABLE_STATEMENT = new RoomAstNodeType("DROP_TABLE_STATEMENT");
  IElementType DROP_TRIGGER_STATEMENT = new RoomAstNodeType("DROP_TRIGGER_STATEMENT");
  IElementType DROP_VIEW_STATEMENT = new RoomAstNodeType("DROP_VIEW_STATEMENT");
  IElementType EQUIVALENCE_EXPRESSION = new RoomAstNodeType("EQUIVALENCE_EXPRESSION");
  IElementType ERROR_MESSAGE = new RoomAstNodeType("ERROR_MESSAGE");
  IElementType EXISTS_EXPRESSION = new RoomAstNodeType("EXISTS_EXPRESSION");
  IElementType EXPRESSION = new RoomAstNodeType("EXPRESSION");
  IElementType FOREIGN_KEY_CLAUSE = new RoomAstNodeType("FOREIGN_KEY_CLAUSE");
  IElementType FOREIGN_TABLE = new RoomAstNodeType("FOREIGN_TABLE");
  IElementType FROM_CLAUSE = new RoomAstNodeType("FROM_CLAUSE");
  IElementType FROM_TABLE = new RoomAstNodeType("FROM_TABLE");
  IElementType FUNCTION_CALL_EXPRESSION = new RoomAstNodeType("FUNCTION_CALL_EXPRESSION");
  IElementType FUNCTION_NAME = new RoomAstNodeType("FUNCTION_NAME");
  IElementType GROUP_BY_CLAUSE = new RoomAstNodeType("GROUP_BY_CLAUSE");
  IElementType INDEXED_COLUMN = new RoomAstNodeType("INDEXED_COLUMN");
  IElementType INSERT_COLUMNS = new RoomAstNodeType("INSERT_COLUMNS");
  IElementType INSERT_STATEMENT = new RoomAstNodeType("INSERT_STATEMENT");
  IElementType IN_EXPRESSION = new RoomAstNodeType("IN_EXPRESSION");
  IElementType ISNULL_EXPRESSION = new RoomAstNodeType("ISNULL_EXPRESSION");
  IElementType JOIN_CLAUSE = new RoomAstNodeType("JOIN_CLAUSE");
  IElementType JOIN_CONSTRAINT = new RoomAstNodeType("JOIN_CONSTRAINT");
  IElementType JOIN_OPERATOR = new RoomAstNodeType("JOIN_OPERATOR");
  IElementType LIKE_EXPRESSION = new RoomAstNodeType("LIKE_EXPRESSION");
  IElementType LIMIT_CLAUSE = new RoomAstNodeType("LIMIT_CLAUSE");
  IElementType LITERAL_EXPRESSION = new RoomAstNodeType("LITERAL_EXPRESSION");
  IElementType MODULE_ARGUMENT = new RoomAstNodeType("MODULE_ARGUMENT");
  IElementType MODULE_NAME = new RoomAstNodeType("MODULE_NAME");
  IElementType MUL_EXPRESSION = new RoomAstNodeType("MUL_EXPRESSION");
  IElementType ORDERING_TERM = new RoomAstNodeType("ORDERING_TERM");
  IElementType ORDER_CLAUSE = new RoomAstNodeType("ORDER_CLAUSE");
  IElementType OR_EXPRESSION = new RoomAstNodeType("OR_EXPRESSION");
  IElementType PAREN_EXPRESSION = new RoomAstNodeType("PAREN_EXPRESSION");
  IElementType PRAGMA_NAME = new RoomAstNodeType("PRAGMA_NAME");
  IElementType PRAGMA_STATEMENT = new RoomAstNodeType("PRAGMA_STATEMENT");
  IElementType PRAGMA_VALUE = new RoomAstNodeType("PRAGMA_VALUE");
  IElementType RAISE_FUNCTION_EXPRESSION = new RoomAstNodeType("RAISE_FUNCTION_EXPRESSION");
  IElementType REINDEX_STATEMENT = new RoomAstNodeType("REINDEX_STATEMENT");
  IElementType RELEASE_STATEMENT = new RoomAstNodeType("RELEASE_STATEMENT");
  IElementType RESULT_COLUMN = new RoomAstNodeType("RESULT_COLUMN");
  IElementType RESULT_COLUMNS = new RoomAstNodeType("RESULT_COLUMNS");
  IElementType ROLLBACK_STATEMENT = new RoomAstNodeType("ROLLBACK_STATEMENT");
  IElementType SAVEPOINT_NAME = new RoomAstNodeType("SAVEPOINT_NAME");
  IElementType SAVEPOINT_STATEMENT = new RoomAstNodeType("SAVEPOINT_STATEMENT");
  IElementType SELECTED_TABLE_NAME = new RoomAstNodeType("SELECTED_TABLE_NAME");
  IElementType SELECT_CORE = new RoomAstNodeType("SELECT_CORE");
  IElementType SELECT_CORE_SELECT = new RoomAstNodeType("SELECT_CORE_SELECT");
  IElementType SELECT_CORE_VALUES = new RoomAstNodeType("SELECT_CORE_VALUES");
  IElementType SELECT_STATEMENT = new RoomAstNodeType("SELECT_STATEMENT");
  IElementType SIGNED_NUMBER = new RoomAstNodeType("SIGNED_NUMBER");
  IElementType SINGLE_TABLE_STATEMENT_TABLE = new RoomAstNodeType("SINGLE_TABLE_STATEMENT_TABLE");
  IElementType STATEMENT = new RoomAstNodeType("STATEMENT");
  IElementType SUBQUERY = new RoomAstNodeType("SUBQUERY");
  IElementType TABLE_ALIAS_NAME = new RoomAstNodeType("TABLE_ALIAS_NAME");
  IElementType TABLE_CONSTRAINT = new RoomAstNodeType("TABLE_CONSTRAINT");
  IElementType TABLE_DEFINITION_NAME = new RoomAstNodeType("TABLE_DEFINITION_NAME");
  IElementType TABLE_OR_INDEX_NAME = new RoomAstNodeType("TABLE_OR_INDEX_NAME");
  IElementType TABLE_OR_SUBQUERY = new RoomAstNodeType("TABLE_OR_SUBQUERY");
  IElementType TRIGGER_NAME = new RoomAstNodeType("TRIGGER_NAME");
  IElementType TYPE_NAME = new RoomAstNodeType("TYPE_NAME");
  IElementType UNARY_EXPRESSION = new RoomAstNodeType("UNARY_EXPRESSION");
  IElementType UPDATE_STATEMENT = new RoomAstNodeType("UPDATE_STATEMENT");
  IElementType VACUUM_STATEMENT = new RoomAstNodeType("VACUUM_STATEMENT");
  IElementType VIEW_NAME = new RoomAstNodeType("VIEW_NAME");
  IElementType WHERE_CLAUSE = new RoomAstNodeType("WHERE_CLAUSE");
  IElementType WITH_CLAUSE = new RoomAstNodeType("WITH_CLAUSE");
  IElementType WITH_CLAUSE_TABLE = new RoomAstNodeType("WITH_CLAUSE_TABLE");
  IElementType WITH_CLAUSE_TABLE_DEF = new RoomAstNodeType("WITH_CLAUSE_TABLE_DEF");

  IElementType ABORT = new RoomTokenType("ABORT");
  IElementType ACTION = new RoomTokenType("ACTION");
  IElementType ADD = new RoomTokenType("ADD");
  IElementType AFTER = new RoomTokenType("AFTER");
  IElementType ALL = new RoomTokenType("ALL");
  IElementType ALTER = new RoomTokenType("ALTER");
  IElementType AMP = new RoomTokenType("&");
  IElementType ANALYZE = new RoomTokenType("ANALYZE");
  IElementType AND = new RoomTokenType("AND");
  IElementType AS = new RoomTokenType("AS");
  IElementType ASC = new RoomTokenType("ASC");
  IElementType ATTACH = new RoomTokenType("ATTACH");
  IElementType AUTOINCREMENT = new RoomTokenType("AUTOINCREMENT");
  IElementType BACKTICK_LITERAL = new RoomTokenType("BACKTICK_LITERAL");
  IElementType BAR = new RoomTokenType("|");
  IElementType BEFORE = new RoomTokenType("BEFORE");
  IElementType BEGIN = new RoomTokenType("BEGIN");
  IElementType BETWEEN = new RoomTokenType("BETWEEN");
  IElementType BRACKET_LITERAL = new RoomTokenType("BRACKET_LITERAL");
  IElementType BY = new RoomTokenType("BY");
  IElementType CASCADE = new RoomTokenType("CASCADE");
  IElementType CASE = new RoomTokenType("CASE");
  IElementType CAST = new RoomTokenType("CAST");
  IElementType CHECK = new RoomTokenType("CHECK");
  IElementType COLLATE = new RoomTokenType("COLLATE");
  IElementType COLUMN = new RoomTokenType("COLUMN");
  IElementType COMMA = new RoomTokenType(",");
  IElementType COMMENT = new RoomTokenType("COMMENT");
  IElementType COMMIT = new RoomTokenType("COMMIT");
  IElementType CONCAT = new RoomTokenType("||");
  IElementType CONFLICT = new RoomTokenType("CONFLICT");
  IElementType CONSTRAINT = new RoomTokenType("CONSTRAINT");
  IElementType CREATE = new RoomTokenType("CREATE");
  IElementType CROSS = new RoomTokenType("CROSS");
  IElementType CURRENT_DATE = new RoomTokenType("CURRENT_DATE");
  IElementType CURRENT_TIME = new RoomTokenType("CURRENT_TIME");
  IElementType CURRENT_TIMESTAMP = new RoomTokenType("CURRENT_TIMESTAMP");
  IElementType DATABASE = new RoomTokenType("DATABASE");
  IElementType DEFAULT = new RoomTokenType("DEFAULT");
  IElementType DEFERRABLE = new RoomTokenType("DEFERRABLE");
  IElementType DEFERRED = new RoomTokenType("DEFERRED");
  IElementType DELETE = new RoomTokenType("DELETE");
  IElementType DESC = new RoomTokenType("DESC");
  IElementType DETACH = new RoomTokenType("DETACH");
  IElementType DISTINCT = new RoomTokenType("DISTINCT");
  IElementType DIV = new RoomTokenType("/");
  IElementType DOT = new RoomTokenType(".");
  IElementType DOUBLE_QUOTE_STRING_LITERAL = new RoomTokenType("DOUBLE_QUOTE_STRING_LITERAL");
  IElementType DROP = new RoomTokenType("DROP");
  IElementType EACH = new RoomTokenType("EACH");
  IElementType ELSE = new RoomTokenType("ELSE");
  IElementType END = new RoomTokenType("END");
  IElementType EQ = new RoomTokenType("=");
  IElementType EQEQ = new RoomTokenType("==");
  IElementType ESCAPE = new RoomTokenType("ESCAPE");
  IElementType EXCEPT = new RoomTokenType("EXCEPT");
  IElementType EXCLUSIVE = new RoomTokenType("EXCLUSIVE");
  IElementType EXISTS = new RoomTokenType("EXISTS");
  IElementType EXPLAIN = new RoomTokenType("EXPLAIN");
  IElementType FAIL = new RoomTokenType("FAIL");
  IElementType FOR = new RoomTokenType("FOR");
  IElementType FOREIGN = new RoomTokenType("FOREIGN");
  IElementType FROM = new RoomTokenType("FROM");
  IElementType FULL = new RoomTokenType("FULL");
  IElementType GLOB = new RoomTokenType("GLOB");
  IElementType GROUP = new RoomTokenType("GROUP");
  IElementType GT = new RoomTokenType(">");
  IElementType GTE = new RoomTokenType(">=");
  IElementType HAVING = new RoomTokenType("HAVING");
  IElementType IDENTIFIER = new RoomTokenType("IDENTIFIER");
  IElementType IF = new RoomTokenType("IF");
  IElementType IGNORE = new RoomTokenType("IGNORE");
  IElementType IMMEDIATE = new RoomTokenType("IMMEDIATE");
  IElementType IN = new RoomTokenType("IN");
  IElementType INDEX = new RoomTokenType("INDEX");
  IElementType INDEXED = new RoomTokenType("INDEXED");
  IElementType INITIALLY = new RoomTokenType("INITIALLY");
  IElementType INNER = new RoomTokenType("INNER");
  IElementType INSERT = new RoomTokenType("INSERT");
  IElementType INSTEAD = new RoomTokenType("INSTEAD");
  IElementType INTERSECT = new RoomTokenType("INTERSECT");
  IElementType INTO = new RoomTokenType("INTO");
  IElementType IS = new RoomTokenType("IS");
  IElementType ISNULL = new RoomTokenType("ISNULL");
  IElementType JOIN = new RoomTokenType("JOIN");
  IElementType KEY = new RoomTokenType("KEY");
  IElementType LEFT = new RoomTokenType("LEFT");
  IElementType LIKE = new RoomTokenType("LIKE");
  IElementType LIMIT = new RoomTokenType("LIMIT");
  IElementType LINE_COMMENT = new RoomTokenType("LINE_COMMENT");
  IElementType LPAREN = new RoomTokenType("(");
  IElementType LT = new RoomTokenType("<");
  IElementType LTE = new RoomTokenType("<=");
  IElementType MATCH = new RoomTokenType("MATCH");
  IElementType MINUS = new RoomTokenType("-");
  IElementType MOD = new RoomTokenType("%");
  IElementType NAMED_PARAMETER = new RoomTokenType("NAMED_PARAMETER");
  IElementType NATURAL = new RoomTokenType("NATURAL");
  IElementType NO = new RoomTokenType("NO");
  IElementType NOT = new RoomTokenType("NOT");
  IElementType NOTNULL = new RoomTokenType("NOTNULL");
  IElementType NOT_EQ = new RoomTokenType("!=");
  IElementType NULL = new RoomTokenType("NULL");
  IElementType NUMBERED_PARAMETER = new RoomTokenType("NUMBERED_PARAMETER");
  IElementType NUMERIC_LITERAL = new RoomTokenType("NUMERIC_LITERAL");
  IElementType OF = new RoomTokenType("OF");
  IElementType OFFSET = new RoomTokenType("OFFSET");
  IElementType ON = new RoomTokenType("ON");
  IElementType OR = new RoomTokenType("OR");
  IElementType ORDER = new RoomTokenType("ORDER");
  IElementType OUTER = new RoomTokenType("OUTER");
  IElementType PLAN = new RoomTokenType("PLAN");
  IElementType PLUS = new RoomTokenType("+");
  IElementType PRAGMA = new RoomTokenType("PRAGMA");
  IElementType PRIMARY = new RoomTokenType("PRIMARY");
  IElementType QUERY = new RoomTokenType("QUERY");
  IElementType RAISE = new RoomTokenType("RAISE");
  IElementType RECURSIVE = new RoomTokenType("RECURSIVE");
  IElementType REFERENCES = new RoomTokenType("REFERENCES");
  IElementType REGEXP = new RoomTokenType("REGEXP");
  IElementType REINDEX = new RoomTokenType("REINDEX");
  IElementType RELEASE = new RoomTokenType("RELEASE");
  IElementType RENAME = new RoomTokenType("RENAME");
  IElementType REPLACE = new RoomTokenType("REPLACE");
  IElementType RESTRICT = new RoomTokenType("RESTRICT");
  IElementType RIGHT = new RoomTokenType("RIGHT");
  IElementType ROLLBACK = new RoomTokenType("ROLLBACK");
  IElementType ROW = new RoomTokenType("ROW");
  IElementType ROWID = new RoomTokenType("ROWID");
  IElementType RPAREN = new RoomTokenType(")");
  IElementType SAVEPOINT = new RoomTokenType("SAVEPOINT");
  IElementType SELECT = new RoomTokenType("SELECT");
  IElementType SEMICOLON = new RoomTokenType(";");
  IElementType SET = new RoomTokenType("SET");
  IElementType SHL = new RoomTokenType("<<");
  IElementType SHR = new RoomTokenType(">>");
  IElementType SINGLE_QUOTE_STRING_LITERAL = new RoomTokenType("SINGLE_QUOTE_STRING_LITERAL");
  IElementType STAR = new RoomTokenType("*");
  IElementType TABLE = new RoomTokenType("TABLE");
  IElementType TEMP = new RoomTokenType("TEMP");
  IElementType TEMPORARY = new RoomTokenType("TEMPORARY");
  IElementType THEN = new RoomTokenType("THEN");
  IElementType TILDE = new RoomTokenType("~");
  IElementType TO = new RoomTokenType("TO");
  IElementType TRANSACTION = new RoomTokenType("TRANSACTION");
  IElementType TRIGGER = new RoomTokenType("TRIGGER");
  IElementType UNEQ = new RoomTokenType("<>");
  IElementType UNION = new RoomTokenType("UNION");
  IElementType UNIQUE = new RoomTokenType("UNIQUE");
  IElementType UPDATE = new RoomTokenType("UPDATE");
  IElementType USING = new RoomTokenType("USING");
  IElementType VACUUM = new RoomTokenType("VACUUM");
  IElementType VALUES = new RoomTokenType("VALUES");
  IElementType VIEW = new RoomTokenType("VIEW");
  IElementType VIRTUAL = new RoomTokenType("VIRTUAL");
  IElementType WHEN = new RoomTokenType("WHEN");
  IElementType WHERE = new RoomTokenType("WHERE");
  IElementType WITH = new RoomTokenType("WITH");
  IElementType WITHOUT = new RoomTokenType("WITHOUT");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ADD_EXPRESSION) {
        return new RoomAddExpressionImpl(node);
      }
      else if (type == ALTER_TABLE_STATEMENT) {
        return new RoomAlterTableStatementImpl(node);
      }
      else if (type == ANALYZE_STATEMENT) {
        return new RoomAnalyzeStatementImpl(node);
      }
      else if (type == AND_EXPRESSION) {
        return new RoomAndExpressionImpl(node);
      }
      else if (type == ATTACH_STATEMENT) {
        return new RoomAttachStatementImpl(node);
      }
      else if (type == BEGIN_STATEMENT) {
        return new RoomBeginStatementImpl(node);
      }
      else if (type == BETWEEN_EXPRESSION) {
        return new RoomBetweenExpressionImpl(node);
      }
      else if (type == BIND_PARAMETER) {
        return new RoomBindParameterImpl(node);
      }
      else if (type == BIT_EXPRESSION) {
        return new RoomBitExpressionImpl(node);
      }
      else if (type == CASE_EXPRESSION) {
        return new RoomCaseExpressionImpl(node);
      }
      else if (type == CAST_EXPRESSION) {
        return new RoomCastExpressionImpl(node);
      }
      else if (type == COLLATE_EXPRESSION) {
        return new RoomCollateExpressionImpl(node);
      }
      else if (type == COLLATION_NAME) {
        return new RoomCollationNameImpl(node);
      }
      else if (type == COLUMN_ALIAS_NAME) {
        return new RoomColumnAliasNameImpl(node);
      }
      else if (type == COLUMN_CONSTRAINT) {
        return new RoomColumnConstraintImpl(node);
      }
      else if (type == COLUMN_DEFINITION) {
        return new RoomColumnDefinitionImpl(node);
      }
      else if (type == COLUMN_DEFINITION_NAME) {
        return new RoomColumnDefinitionNameImpl(node);
      }
      else if (type == COLUMN_NAME) {
        return new RoomColumnNameImpl(node);
      }
      else if (type == COLUMN_REF_EXPRESSION) {
        return new RoomColumnRefExpressionImpl(node);
      }
      else if (type == COMMIT_STATEMENT) {
        return new RoomCommitStatementImpl(node);
      }
      else if (type == COMPARISON_EXPRESSION) {
        return new RoomComparisonExpressionImpl(node);
      }
      else if (type == COMPOUND_OPERATOR) {
        return new RoomCompoundOperatorImpl(node);
      }
      else if (type == CONCAT_EXPRESSION) {
        return new RoomConcatExpressionImpl(node);
      }
      else if (type == CONFLICT_CLAUSE) {
        return new RoomConflictClauseImpl(node);
      }
      else if (type == CREATE_INDEX_STATEMENT) {
        return new RoomCreateIndexStatementImpl(node);
      }
      else if (type == CREATE_TABLE_STATEMENT) {
        return new RoomCreateTableStatementImpl(node);
      }
      else if (type == CREATE_TRIGGER_STATEMENT) {
        return new RoomCreateTriggerStatementImpl(node);
      }
      else if (type == CREATE_VIEW_STATEMENT) {
        return new RoomCreateViewStatementImpl(node);
      }
      else if (type == CREATE_VIRTUAL_TABLE_STATEMENT) {
        return new RoomCreateVirtualTableStatementImpl(node);
      }
      else if (type == DATABASE_NAME) {
        return new RoomDatabaseNameImpl(node);
      }
      else if (type == DEFINED_TABLE_NAME) {
        return new RoomDefinedTableNameImpl(node);
      }
      else if (type == DELETE_STATEMENT) {
        return new RoomDeleteStatementImpl(node);
      }
      else if (type == DETACH_STATEMENT) {
        return new RoomDetachStatementImpl(node);
      }
      else if (type == DROP_INDEX_STATEMENT) {
        return new RoomDropIndexStatementImpl(node);
      }
      else if (type == DROP_TABLE_STATEMENT) {
        return new RoomDropTableStatementImpl(node);
      }
      else if (type == DROP_TRIGGER_STATEMENT) {
        return new RoomDropTriggerStatementImpl(node);
      }
      else if (type == DROP_VIEW_STATEMENT) {
        return new RoomDropViewStatementImpl(node);
      }
      else if (type == EQUIVALENCE_EXPRESSION) {
        return new RoomEquivalenceExpressionImpl(node);
      }
      else if (type == ERROR_MESSAGE) {
        return new RoomErrorMessageImpl(node);
      }
      else if (type == EXISTS_EXPRESSION) {
        return new RoomExistsExpressionImpl(node);
      }
      else if (type == FOREIGN_KEY_CLAUSE) {
        return new RoomForeignKeyClauseImpl(node);
      }
      else if (type == FOREIGN_TABLE) {
        return new RoomForeignTableImpl(node);
      }
      else if (type == FROM_CLAUSE) {
        return new RoomFromClauseImpl(node);
      }
      else if (type == FROM_TABLE) {
        return new RoomFromTableImpl(node);
      }
      else if (type == FUNCTION_CALL_EXPRESSION) {
        return new RoomFunctionCallExpressionImpl(node);
      }
      else if (type == FUNCTION_NAME) {
        return new RoomFunctionNameImpl(node);
      }
      else if (type == GROUP_BY_CLAUSE) {
        return new RoomGroupByClauseImpl(node);
      }
      else if (type == INDEXED_COLUMN) {
        return new RoomIndexedColumnImpl(node);
      }
      else if (type == INSERT_COLUMNS) {
        return new RoomInsertColumnsImpl(node);
      }
      else if (type == INSERT_STATEMENT) {
        return new RoomInsertStatementImpl(node);
      }
      else if (type == IN_EXPRESSION) {
        return new RoomInExpressionImpl(node);
      }
      else if (type == ISNULL_EXPRESSION) {
        return new RoomIsnullExpressionImpl(node);
      }
      else if (type == JOIN_CLAUSE) {
        return new RoomJoinClauseImpl(node);
      }
      else if (type == JOIN_CONSTRAINT) {
        return new RoomJoinConstraintImpl(node);
      }
      else if (type == JOIN_OPERATOR) {
        return new RoomJoinOperatorImpl(node);
      }
      else if (type == LIKE_EXPRESSION) {
        return new RoomLikeExpressionImpl(node);
      }
      else if (type == LIMIT_CLAUSE) {
        return new RoomLimitClauseImpl(node);
      }
      else if (type == LITERAL_EXPRESSION) {
        return new RoomLiteralExpressionImpl(node);
      }
      else if (type == MODULE_ARGUMENT) {
        return new RoomModuleArgumentImpl(node);
      }
      else if (type == MODULE_NAME) {
        return new RoomModuleNameImpl(node);
      }
      else if (type == MUL_EXPRESSION) {
        return new RoomMulExpressionImpl(node);
      }
      else if (type == ORDERING_TERM) {
        return new RoomOrderingTermImpl(node);
      }
      else if (type == ORDER_CLAUSE) {
        return new RoomOrderClauseImpl(node);
      }
      else if (type == OR_EXPRESSION) {
        return new RoomOrExpressionImpl(node);
      }
      else if (type == PAREN_EXPRESSION) {
        return new RoomParenExpressionImpl(node);
      }
      else if (type == PRAGMA_NAME) {
        return new RoomPragmaNameImpl(node);
      }
      else if (type == PRAGMA_STATEMENT) {
        return new RoomPragmaStatementImpl(node);
      }
      else if (type == PRAGMA_VALUE) {
        return new RoomPragmaValueImpl(node);
      }
      else if (type == RAISE_FUNCTION_EXPRESSION) {
        return new RoomRaiseFunctionExpressionImpl(node);
      }
      else if (type == REINDEX_STATEMENT) {
        return new RoomReindexStatementImpl(node);
      }
      else if (type == RELEASE_STATEMENT) {
        return new RoomReleaseStatementImpl(node);
      }
      else if (type == RESULT_COLUMN) {
        return new RoomResultColumnImpl(node);
      }
      else if (type == RESULT_COLUMNS) {
        return new RoomResultColumnsImpl(node);
      }
      else if (type == ROLLBACK_STATEMENT) {
        return new RoomRollbackStatementImpl(node);
      }
      else if (type == SAVEPOINT_NAME) {
        return new RoomSavepointNameImpl(node);
      }
      else if (type == SAVEPOINT_STATEMENT) {
        return new RoomSavepointStatementImpl(node);
      }
      else if (type == SELECTED_TABLE_NAME) {
        return new RoomSelectedTableNameImpl(node);
      }
      else if (type == SELECT_CORE) {
        return new RoomSelectCoreImpl(node);
      }
      else if (type == SELECT_CORE_SELECT) {
        return new RoomSelectCoreSelectImpl(node);
      }
      else if (type == SELECT_CORE_VALUES) {
        return new RoomSelectCoreValuesImpl(node);
      }
      else if (type == SELECT_STATEMENT) {
        return new RoomSelectStatementImpl(node);
      }
      else if (type == SIGNED_NUMBER) {
        return new RoomSignedNumberImpl(node);
      }
      else if (type == SINGLE_TABLE_STATEMENT_TABLE) {
        return new RoomSingleTableStatementTableImpl(node);
      }
      else if (type == STATEMENT) {
        return new RoomStatementImpl(node);
      }
      else if (type == SUBQUERY) {
        return new RoomSubqueryImpl(node);
      }
      else if (type == TABLE_ALIAS_NAME) {
        return new RoomTableAliasNameImpl(node);
      }
      else if (type == TABLE_CONSTRAINT) {
        return new RoomTableConstraintImpl(node);
      }
      else if (type == TABLE_DEFINITION_NAME) {
        return new RoomTableDefinitionNameImpl(node);
      }
      else if (type == TABLE_OR_INDEX_NAME) {
        return new RoomTableOrIndexNameImpl(node);
      }
      else if (type == TABLE_OR_SUBQUERY) {
        return new RoomTableOrSubqueryImpl(node);
      }
      else if (type == TRIGGER_NAME) {
        return new RoomTriggerNameImpl(node);
      }
      else if (type == TYPE_NAME) {
        return new RoomTypeNameImpl(node);
      }
      else if (type == UNARY_EXPRESSION) {
        return new RoomUnaryExpressionImpl(node);
      }
      else if (type == UPDATE_STATEMENT) {
        return new RoomUpdateStatementImpl(node);
      }
      else if (type == VACUUM_STATEMENT) {
        return new RoomVacuumStatementImpl(node);
      }
      else if (type == VIEW_NAME) {
        return new RoomViewNameImpl(node);
      }
      else if (type == WHERE_CLAUSE) {
        return new RoomWhereClauseImpl(node);
      }
      else if (type == WITH_CLAUSE) {
        return new RoomWithClauseImpl(node);
      }
      else if (type == WITH_CLAUSE_TABLE) {
        return new RoomWithClauseTableImpl(node);
      }
      else if (type == WITH_CLAUSE_TABLE_DEF) {
        return new RoomWithClauseTableDefImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
