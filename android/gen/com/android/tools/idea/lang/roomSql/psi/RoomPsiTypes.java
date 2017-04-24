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

  IElementType ALTER_TABLE_STMT = new RoomAstNodeType("ALTER_TABLE_STMT");
  IElementType ANALYZE_STMT = new RoomAstNodeType("ANALYZE_STMT");
  IElementType ATTACH_STMT = new RoomAstNodeType("ATTACH_STMT");
  IElementType BEGIN_STMT = new RoomAstNodeType("BEGIN_STMT");
  IElementType BIND_PARAMETER = new RoomAstNodeType("BIND_PARAMETER");
  IElementType COLLATION_NAME = new RoomAstNodeType("COLLATION_NAME");
  IElementType COLUMN_ALIAS = new RoomAstNodeType("COLUMN_ALIAS");
  IElementType COLUMN_CONSTRAINT = new RoomAstNodeType("COLUMN_CONSTRAINT");
  IElementType COLUMN_DEF = new RoomAstNodeType("COLUMN_DEF");
  IElementType COLUMN_NAME = new RoomAstNodeType("COLUMN_NAME");
  IElementType COMMIT_STMT = new RoomAstNodeType("COMMIT_STMT");
  IElementType COMMON_TABLE_EXPRESSION = new RoomAstNodeType("COMMON_TABLE_EXPRESSION");
  IElementType COMPOUND_OPERATOR = new RoomAstNodeType("COMPOUND_OPERATOR");
  IElementType CONFLICT_CLAUSE = new RoomAstNodeType("CONFLICT_CLAUSE");
  IElementType CREATE_INDEX_STMT = new RoomAstNodeType("CREATE_INDEX_STMT");
  IElementType CREATE_TABLE_STMT = new RoomAstNodeType("CREATE_TABLE_STMT");
  IElementType CREATE_TRIGGER_STMT = new RoomAstNodeType("CREATE_TRIGGER_STMT");
  IElementType CREATE_VIEW_STMT = new RoomAstNodeType("CREATE_VIEW_STMT");
  IElementType CREATE_VIRTUAL_TABLE_STMT = new RoomAstNodeType("CREATE_VIRTUAL_TABLE_STMT");
  IElementType CTE_TABLE_NAME = new RoomAstNodeType("CTE_TABLE_NAME");
  IElementType DATABASE_NAME = new RoomAstNodeType("DATABASE_NAME");
  IElementType DELETE_STMT = new RoomAstNodeType("DELETE_STMT");
  IElementType DELETE_STMT_LIMITED = new RoomAstNodeType("DELETE_STMT_LIMITED");
  IElementType DETACH_STMT = new RoomAstNodeType("DETACH_STMT");
  IElementType DROP_INDEX_STMT = new RoomAstNodeType("DROP_INDEX_STMT");
  IElementType DROP_TABLE_STMT = new RoomAstNodeType("DROP_TABLE_STMT");
  IElementType DROP_TRIGGER_STMT = new RoomAstNodeType("DROP_TRIGGER_STMT");
  IElementType DROP_VIEW_STMT = new RoomAstNodeType("DROP_VIEW_STMT");
  IElementType ERROR_MESSAGE = new RoomAstNodeType("ERROR_MESSAGE");
  IElementType EXPR = new RoomAstNodeType("EXPR");
  IElementType FOREIGN_KEY_CLAUSE = new RoomAstNodeType("FOREIGN_KEY_CLAUSE");
  IElementType FOREIGN_TABLE = new RoomAstNodeType("FOREIGN_TABLE");
  IElementType FUNCTION_NAME = new RoomAstNodeType("FUNCTION_NAME");
  IElementType INDEXED_COLUMN = new RoomAstNodeType("INDEXED_COLUMN");
  IElementType INDEX_NAME = new RoomAstNodeType("INDEX_NAME");
  IElementType INSERT_STMT = new RoomAstNodeType("INSERT_STMT");
  IElementType JOIN_CLAUSE = new RoomAstNodeType("JOIN_CLAUSE");
  IElementType JOIN_CONSTRAINT = new RoomAstNodeType("JOIN_CONSTRAINT");
  IElementType JOIN_OPERATOR = new RoomAstNodeType("JOIN_OPERATOR");
  IElementType LITERAL_VALUE = new RoomAstNodeType("LITERAL_VALUE");
  IElementType MODULE_ARGUMENT = new RoomAstNodeType("MODULE_ARGUMENT");
  IElementType MODULE_NAME = new RoomAstNodeType("MODULE_NAME");
  IElementType ORDERING_TERM = new RoomAstNodeType("ORDERING_TERM");
  IElementType PRAGMA_NAME = new RoomAstNodeType("PRAGMA_NAME");
  IElementType PRAGMA_STMT = new RoomAstNodeType("PRAGMA_STMT");
  IElementType PRAGMA_VALUE = new RoomAstNodeType("PRAGMA_VALUE");
  IElementType QUALIFIED_TABLE_NAME = new RoomAstNodeType("QUALIFIED_TABLE_NAME");
  IElementType RAISE_FUNCTION = new RoomAstNodeType("RAISE_FUNCTION");
  IElementType REINDEX_STMT = new RoomAstNodeType("REINDEX_STMT");
  IElementType RELEASE_STMT = new RoomAstNodeType("RELEASE_STMT");
  IElementType RESULT_COLUMN = new RoomAstNodeType("RESULT_COLUMN");
  IElementType ROLLBACK_STMT = new RoomAstNodeType("ROLLBACK_STMT");
  IElementType SAVEPOINT_NAME = new RoomAstNodeType("SAVEPOINT_NAME");
  IElementType SAVEPOINT_STMT = new RoomAstNodeType("SAVEPOINT_STMT");
  IElementType SELECT_STMT = new RoomAstNodeType("SELECT_STMT");
  IElementType SIGNED_NUMBER = new RoomAstNodeType("SIGNED_NUMBER");
  IElementType SQL_STMT = new RoomAstNodeType("SQL_STMT");
  IElementType TABLE_ALIAS = new RoomAstNodeType("TABLE_ALIAS");
  IElementType TABLE_CONSTRAINT = new RoomAstNodeType("TABLE_CONSTRAINT");
  IElementType TABLE_NAME = new RoomAstNodeType("TABLE_NAME");
  IElementType TABLE_OR_INDEX_NAME = new RoomAstNodeType("TABLE_OR_INDEX_NAME");
  IElementType TABLE_OR_SUBQUERY = new RoomAstNodeType("TABLE_OR_SUBQUERY");
  IElementType TRIGGER_NAME = new RoomAstNodeType("TRIGGER_NAME");
  IElementType TYPE_NAME = new RoomAstNodeType("TYPE_NAME");
  IElementType UNARY_OPERATOR = new RoomAstNodeType("UNARY_OPERATOR");
  IElementType UPDATE_STMT = new RoomAstNodeType("UPDATE_STMT");
  IElementType UPDATE_STMT_LIMITED = new RoomAstNodeType("UPDATE_STMT_LIMITED");
  IElementType VACUUM_STMT = new RoomAstNodeType("VACUUM_STMT");
  IElementType VIEW_NAME = new RoomAstNodeType("VIEW_NAME");
  IElementType WITH_CLAUSE = new RoomAstNodeType("WITH_CLAUSE");

  IElementType ABORT = new RoomTokenType("ABORT");
  IElementType ACTION = new RoomTokenType("ACTION");
  IElementType ADD = new RoomTokenType("ADD");
  IElementType AFTER = new RoomTokenType("AFTER");
  IElementType ALL = new RoomTokenType("ALL");
  IElementType ALTER = new RoomTokenType("ALTER");
  IElementType ANALYZE = new RoomTokenType("ANALYZE");
  IElementType AS = new RoomTokenType("AS");
  IElementType ASC = new RoomTokenType("ASC");
  IElementType ATTACH = new RoomTokenType("ATTACH");
  IElementType AUTOINCREMENT = new RoomTokenType("AUTOINCREMENT");
  IElementType BEFORE = new RoomTokenType("BEFORE");
  IElementType BEGIN = new RoomTokenType("BEGIN");
  IElementType BLOB_LITERAL = new RoomTokenType("BLOB_LITERAL");
  IElementType BY = new RoomTokenType("BY");
  IElementType CASCADE = new RoomTokenType("CASCADE");
  IElementType CASE = new RoomTokenType("CASE");
  IElementType CAST = new RoomTokenType("CAST");
  IElementType CHECK = new RoomTokenType("CHECK");
  IElementType COLLATE = new RoomTokenType("COLLATE");
  IElementType COLUMN = new RoomTokenType("COLUMN");
  IElementType COMMENT = new RoomTokenType("COMMENT");
  IElementType COMMIT = new RoomTokenType("COMMIT");
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
  IElementType DROP = new RoomTokenType("DROP");
  IElementType EACH = new RoomTokenType("EACH");
  IElementType ELSE = new RoomTokenType("ELSE");
  IElementType END = new RoomTokenType("END");
  IElementType EXCEPT = new RoomTokenType("EXCEPT");
  IElementType EXCLUSIVE = new RoomTokenType("EXCLUSIVE");
  IElementType EXISTS = new RoomTokenType("EXISTS");
  IElementType EXPLAIN = new RoomTokenType("EXPLAIN");
  IElementType FAIL = new RoomTokenType("FAIL");
  IElementType FOR = new RoomTokenType("FOR");
  IElementType FOREIGN = new RoomTokenType("FOREIGN");
  IElementType FROM = new RoomTokenType("FROM");
  IElementType GROUP = new RoomTokenType("GROUP");
  IElementType HAVING = new RoomTokenType("HAVING");
  IElementType IF = new RoomTokenType("IF");
  IElementType IGNORE = new RoomTokenType("IGNORE");
  IElementType IMMEDIATE = new RoomTokenType("IMMEDIATE");
  IElementType INDEX = new RoomTokenType("INDEX");
  IElementType INDEXED = new RoomTokenType("INDEXED");
  IElementType INITIALLY = new RoomTokenType("INITIALLY");
  IElementType INNER = new RoomTokenType("INNER");
  IElementType INSERT = new RoomTokenType("INSERT");
  IElementType INSTEAD = new RoomTokenType("INSTEAD");
  IElementType INTERSECT = new RoomTokenType("INTERSECT");
  IElementType INTO = new RoomTokenType("INTO");
  IElementType JOIN = new RoomTokenType("JOIN");
  IElementType KEY = new RoomTokenType("KEY");
  IElementType LEFT = new RoomTokenType("LEFT");
  IElementType LIMIT = new RoomTokenType("LIMIT");
  IElementType MATCH = new RoomTokenType("MATCH");
  IElementType NAME_LITERAL = new RoomTokenType("NAME_LITERAL");
  IElementType NATURAL = new RoomTokenType("NATURAL");
  IElementType NO = new RoomTokenType("NO");
  IElementType NOT = new RoomTokenType("NOT");
  IElementType NULL = new RoomTokenType("NULL");
  IElementType NUMERIC_LITERAL = new RoomTokenType("NUMERIC_LITERAL");
  IElementType OF = new RoomTokenType("OF");
  IElementType OFFSET = new RoomTokenType("OFFSET");
  IElementType ON = new RoomTokenType("ON");
  IElementType OR = new RoomTokenType("OR");
  IElementType ORDER = new RoomTokenType("ORDER");
  IElementType OUTER = new RoomTokenType("OUTER");
  IElementType PARAMETER_NAME = new RoomTokenType("PARAMETER_NAME");
  IElementType PLAN = new RoomTokenType("PLAN");
  IElementType PRAGMA = new RoomTokenType("PRAGMA");
  IElementType PRIMARY = new RoomTokenType("PRIMARY");
  IElementType QUERY = new RoomTokenType("QUERY");
  IElementType RAISE = new RoomTokenType("RAISE");
  IElementType RECURSIVE = new RoomTokenType("RECURSIVE");
  IElementType REFERENCES = new RoomTokenType("REFERENCES");
  IElementType REINDEX = new RoomTokenType("REINDEX");
  IElementType RELEASE = new RoomTokenType("RELEASE");
  IElementType RENAME = new RoomTokenType("RENAME");
  IElementType REPLACE = new RoomTokenType("REPLACE");
  IElementType RESTRICT = new RoomTokenType("RESTRICT");
  IElementType ROLLBACK = new RoomTokenType("ROLLBACK");
  IElementType ROW = new RoomTokenType("ROW");
  IElementType ROWID = new RoomTokenType("ROWID");
  IElementType SAVEPOINT = new RoomTokenType("SAVEPOINT");
  IElementType SELECT = new RoomTokenType("SELECT");
  IElementType SET = new RoomTokenType("SET");
  IElementType STRING_LITERAL = new RoomTokenType("STRING_LITERAL");
  IElementType TABLE = new RoomTokenType("TABLE");
  IElementType TEMP = new RoomTokenType("TEMP");
  IElementType TEMPORARY = new RoomTokenType("TEMPORARY");
  IElementType THEN = new RoomTokenType("THEN");
  IElementType TO = new RoomTokenType("TO");
  IElementType TRANSACTION = new RoomTokenType("TRANSACTION");
  IElementType TRIGGER = new RoomTokenType("TRIGGER");
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
       if (type == ALTER_TABLE_STMT) {
        return new RoomAlterTableStmtImpl(node);
      }
      else if (type == ANALYZE_STMT) {
        return new RoomAnalyzeStmtImpl(node);
      }
      else if (type == ATTACH_STMT) {
        return new RoomAttachStmtImpl(node);
      }
      else if (type == BEGIN_STMT) {
        return new RoomBeginStmtImpl(node);
      }
      else if (type == BIND_PARAMETER) {
        return new RoomBindParameterImpl(node);
      }
      else if (type == COLLATION_NAME) {
        return new RoomCollationNameImpl(node);
      }
      else if (type == COLUMN_ALIAS) {
        return new RoomColumnAliasImpl(node);
      }
      else if (type == COLUMN_CONSTRAINT) {
        return new RoomColumnConstraintImpl(node);
      }
      else if (type == COLUMN_DEF) {
        return new RoomColumnDefImpl(node);
      }
      else if (type == COLUMN_NAME) {
        return new RoomColumnNameImpl(node);
      }
      else if (type == COMMIT_STMT) {
        return new RoomCommitStmtImpl(node);
      }
      else if (type == COMMON_TABLE_EXPRESSION) {
        return new RoomCommonTableExpressionImpl(node);
      }
      else if (type == COMPOUND_OPERATOR) {
        return new RoomCompoundOperatorImpl(node);
      }
      else if (type == CONFLICT_CLAUSE) {
        return new RoomConflictClauseImpl(node);
      }
      else if (type == CREATE_INDEX_STMT) {
        return new RoomCreateIndexStmtImpl(node);
      }
      else if (type == CREATE_TABLE_STMT) {
        return new RoomCreateTableStmtImpl(node);
      }
      else if (type == CREATE_TRIGGER_STMT) {
        return new RoomCreateTriggerStmtImpl(node);
      }
      else if (type == CREATE_VIEW_STMT) {
        return new RoomCreateViewStmtImpl(node);
      }
      else if (type == CREATE_VIRTUAL_TABLE_STMT) {
        return new RoomCreateVirtualTableStmtImpl(node);
      }
      else if (type == CTE_TABLE_NAME) {
        return new RoomCteTableNameImpl(node);
      }
      else if (type == DATABASE_NAME) {
        return new RoomDatabaseNameImpl(node);
      }
      else if (type == DELETE_STMT) {
        return new RoomDeleteStmtImpl(node);
      }
      else if (type == DELETE_STMT_LIMITED) {
        return new RoomDeleteStmtLimitedImpl(node);
      }
      else if (type == DETACH_STMT) {
        return new RoomDetachStmtImpl(node);
      }
      else if (type == DROP_INDEX_STMT) {
        return new RoomDropIndexStmtImpl(node);
      }
      else if (type == DROP_TABLE_STMT) {
        return new RoomDropTableStmtImpl(node);
      }
      else if (type == DROP_TRIGGER_STMT) {
        return new RoomDropTriggerStmtImpl(node);
      }
      else if (type == DROP_VIEW_STMT) {
        return new RoomDropViewStmtImpl(node);
      }
      else if (type == ERROR_MESSAGE) {
        return new RoomErrorMessageImpl(node);
      }
      else if (type == EXPR) {
        return new RoomExprImpl(node);
      }
      else if (type == FOREIGN_KEY_CLAUSE) {
        return new RoomForeignKeyClauseImpl(node);
      }
      else if (type == FOREIGN_TABLE) {
        return new RoomForeignTableImpl(node);
      }
      else if (type == FUNCTION_NAME) {
        return new RoomFunctionNameImpl(node);
      }
      else if (type == INDEXED_COLUMN) {
        return new RoomIndexedColumnImpl(node);
      }
      else if (type == INDEX_NAME) {
        return new RoomIndexNameImpl(node);
      }
      else if (type == INSERT_STMT) {
        return new RoomInsertStmtImpl(node);
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
      else if (type == LITERAL_VALUE) {
        return new RoomLiteralValueImpl(node);
      }
      else if (type == MODULE_ARGUMENT) {
        return new RoomModuleArgumentImpl(node);
      }
      else if (type == MODULE_NAME) {
        return new RoomModuleNameImpl(node);
      }
      else if (type == ORDERING_TERM) {
        return new RoomOrderingTermImpl(node);
      }
      else if (type == PRAGMA_NAME) {
        return new RoomPragmaNameImpl(node);
      }
      else if (type == PRAGMA_STMT) {
        return new RoomPragmaStmtImpl(node);
      }
      else if (type == PRAGMA_VALUE) {
        return new RoomPragmaValueImpl(node);
      }
      else if (type == QUALIFIED_TABLE_NAME) {
        return new RoomQualifiedTableNameImpl(node);
      }
      else if (type == RAISE_FUNCTION) {
        return new RoomRaiseFunctionImpl(node);
      }
      else if (type == REINDEX_STMT) {
        return new RoomReindexStmtImpl(node);
      }
      else if (type == RELEASE_STMT) {
        return new RoomReleaseStmtImpl(node);
      }
      else if (type == RESULT_COLUMN) {
        return new RoomResultColumnImpl(node);
      }
      else if (type == ROLLBACK_STMT) {
        return new RoomRollbackStmtImpl(node);
      }
      else if (type == SAVEPOINT_NAME) {
        return new RoomSavepointNameImpl(node);
      }
      else if (type == SAVEPOINT_STMT) {
        return new RoomSavepointStmtImpl(node);
      }
      else if (type == SELECT_STMT) {
        return new RoomSelectStmtImpl(node);
      }
      else if (type == SIGNED_NUMBER) {
        return new RoomSignedNumberImpl(node);
      }
      else if (type == SQL_STMT) {
        return new RoomSqlStmtImpl(node);
      }
      else if (type == TABLE_ALIAS) {
        return new RoomTableAliasImpl(node);
      }
      else if (type == TABLE_CONSTRAINT) {
        return new RoomTableConstraintImpl(node);
      }
      else if (type == TABLE_NAME) {
        return new RoomTableNameImpl(node);
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
      else if (type == UNARY_OPERATOR) {
        return new RoomUnaryOperatorImpl(node);
      }
      else if (type == UPDATE_STMT) {
        return new RoomUpdateStmtImpl(node);
      }
      else if (type == UPDATE_STMT_LIMITED) {
        return new RoomUpdateStmtLimitedImpl(node);
      }
      else if (type == VACUUM_STMT) {
        return new RoomVacuumStmtImpl(node);
      }
      else if (type == VIEW_NAME) {
        return new RoomViewNameImpl(node);
      }
      else if (type == WITH_CLAUSE) {
        return new RoomWithClauseImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
