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

package com.android.tools.idea.lang.androidSql.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.*;
import static com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AndroidSqlParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType type, PsiBuilder builder) {
    parseLight(type, builder);
    return builder.getTreeBuilt();
  }

  public void parseLight(IElementType type, PsiBuilder builder) {
    boolean result;
    builder = adapt_builder_(type, builder, this, EXTENDS_SETS_);
    Marker marker = enter_section_(builder, 0, _COLLAPSE_, null);
    result = parse_root_(type, builder);
    exit_section_(builder, 0, marker, type, result, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType type, PsiBuilder builder) {
    return parse_root_(type, builder, 0);
  }

  static boolean parse_root_(IElementType type, PsiBuilder builder, int level) {
    return root(builder, level + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ADD_EXPRESSION, AND_EXPRESSION, BETWEEN_EXPRESSION, BIT_EXPRESSION,
      CASE_EXPRESSION, CAST_EXPRESSION, COLLATE_EXPRESSION, COLUMN_REF_EXPRESSION,
      COMPARISON_EXPRESSION, CONCAT_EXPRESSION, EQUIVALENCE_EXPRESSION, EXISTS_EXPRESSION,
      EXPRESSION, FUNCTION_CALL_EXPRESSION, IN_EXPRESSION, ISNULL_EXPRESSION,
      LIKE_EXPRESSION, LITERAL_EXPRESSION, MUL_EXPRESSION, OR_EXPRESSION,
      PAREN_EXPRESSION, RAISE_FUNCTION_EXPRESSION, UNARY_EXPRESSION),
  };

  /* ********************************************************** */
  // ADD COLUMN? column_definition
  static boolean add_column_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "add_column_statement")) return false;
    if (!nextTokenIs(builder, ADD)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ADD);
    result = result && add_column_statement_1(builder, level + 1);
    result = result && column_definition(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // COLUMN?
  private static boolean add_column_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "add_column_statement_1")) return false;
    consumeToken(builder, COLUMN);
    return true;
  }

  /* ********************************************************** */
  // ALTER TABLE single_table_statement_table ( rename_table_statement | rename_column_statement | add_column_statement )
  public static boolean alter_table_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "alter_table_statement")) return false;
    if (!nextTokenIs(builder, ALTER)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, ALTER, TABLE);
    result = result && single_table_statement_table(builder, level + 1);
    result = result && alter_table_statement_3(builder, level + 1);
    exit_section_(builder, marker, ALTER_TABLE_STATEMENT, result);
    return result;
  }

  // rename_table_statement | rename_column_statement | add_column_statement
  private static boolean alter_table_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "alter_table_statement_3")) return false;
    boolean result;
    result = rename_table_statement(builder, level + 1);
    if (!result) result = rename_column_statement(builder, level + 1);
    if (!result) result = add_column_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // ANALYZE ( database_name | table_or_index_name | database_name '.' table_or_index_name )?
  public static boolean analyze_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "analyze_statement")) return false;
    if (!nextTokenIs(builder, ANALYZE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ANALYZE);
    result = result && analyze_statement_1(builder, level + 1);
    exit_section_(builder, marker, ANALYZE_STATEMENT, result);
    return result;
  }

  // ( database_name | table_or_index_name | database_name '.' table_or_index_name )?
  private static boolean analyze_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "analyze_statement_1")) return false;
    analyze_statement_1_0(builder, level + 1);
    return true;
  }

  // database_name | table_or_index_name | database_name '.' table_or_index_name
  private static boolean analyze_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "analyze_statement_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    if (!result) result = table_or_index_name(builder, level + 1);
    if (!result) result = analyze_statement_1_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // database_name '.' table_or_index_name
  private static boolean analyze_statement_1_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "analyze_statement_1_0_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    result = result && table_or_index_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // ATTACH ( DATABASE )? expression AS database_name
  public static boolean attach_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "attach_statement")) return false;
    if (!nextTokenIs(builder, ATTACH)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ATTACH);
    result = result && attach_statement_1(builder, level + 1);
    result = result && expression(builder, level + 1, -1);
    result = result && consumeToken(builder, AS);
    result = result && database_name(builder, level + 1);
    exit_section_(builder, marker, ATTACH_STATEMENT, result);
    return result;
  }

  // ( DATABASE )?
  private static boolean attach_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "attach_statement_1")) return false;
    consumeToken(builder, DATABASE);
    return true;
  }

  /* ********************************************************** */
  // BEGIN ( DEFERRED | IMMEDIATE | EXCLUSIVE )? ( TRANSACTION )?
  public static boolean begin_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "begin_statement")) return false;
    if (!nextTokenIs(builder, BEGIN)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, BEGIN);
    result = result && begin_statement_1(builder, level + 1);
    result = result && begin_statement_2(builder, level + 1);
    exit_section_(builder, marker, BEGIN_STATEMENT, result);
    return result;
  }

  // ( DEFERRED | IMMEDIATE | EXCLUSIVE )?
  private static boolean begin_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "begin_statement_1")) return false;
    begin_statement_1_0(builder, level + 1);
    return true;
  }

  // DEFERRED | IMMEDIATE | EXCLUSIVE
  private static boolean begin_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "begin_statement_1_0")) return false;
    boolean result;
    result = consumeToken(builder, DEFERRED);
    if (!result) result = consumeToken(builder, IMMEDIATE);
    if (!result) result = consumeToken(builder, EXCLUSIVE);
    return result;
  }

  // ( TRANSACTION )?
  private static boolean begin_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "begin_statement_2")) return false;
    consumeToken(builder, TRANSACTION);
    return true;
  }

  /* ********************************************************** */
  // NUMBERED_PARAMETER | NAMED_PARAMETER
  public static boolean bind_parameter(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "bind_parameter")) return false;
    if (!nextTokenIs(builder, "<bind parameter>", NAMED_PARAMETER, NUMBERED_PARAMETER)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, BIND_PARAMETER, "<bind parameter>");
    result = consumeToken(builder, NUMBERED_PARAMETER);
    if (!result) result = consumeToken(builder, NAMED_PARAMETER);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean collation_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "collation_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLLATION_NAME, "<collation name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean column_alias_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_alias_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_ALIAS_NAME, "<column alias name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // ( CONSTRAINT  name )?
  //   ( PRIMARY KEY ( ASC | DESC )? conflict_clause ( AUTOINCREMENT )?
  //   | NOT NULL conflict_clause
  //   | UNIQUE conflict_clause
  //   | CHECK '(' expression ')'
  //   | DEFAULT ( signed_number | literal_value | '(' expression ')' )
  //   | COLLATE collation_name | foreign_key_clause )
  public static boolean column_constraint(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_CONSTRAINT, "<column constraint>");
    result = column_constraint_0(builder, level + 1);
    result = result && column_constraint_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( CONSTRAINT  name )?
  private static boolean column_constraint_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_0")) return false;
    column_constraint_0_0(builder, level + 1);
    return true;
  }

  // CONSTRAINT  name
  private static boolean column_constraint_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CONSTRAINT);
    result = result && name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // PRIMARY KEY ( ASC | DESC )? conflict_clause ( AUTOINCREMENT )?
  //   | NOT NULL conflict_clause
  //   | UNIQUE conflict_clause
  //   | CHECK '(' expression ')'
  //   | DEFAULT ( signed_number | literal_value | '(' expression ')' )
  //   | COLLATE collation_name | foreign_key_clause
  private static boolean column_constraint_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = column_constraint_1_0(builder, level + 1);
    if (!result) result = column_constraint_1_1(builder, level + 1);
    if (!result) result = column_constraint_1_2(builder, level + 1);
    if (!result) result = column_constraint_1_3(builder, level + 1);
    if (!result) result = column_constraint_1_4(builder, level + 1);
    if (!result) result = column_constraint_1_5(builder, level + 1);
    if (!result) result = foreign_key_clause(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // PRIMARY KEY ( ASC | DESC )? conflict_clause ( AUTOINCREMENT )?
  private static boolean column_constraint_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, PRIMARY, KEY);
    result = result && column_constraint_1_0_2(builder, level + 1);
    result = result && conflict_clause(builder, level + 1);
    result = result && column_constraint_1_0_4(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ASC | DESC )?
  private static boolean column_constraint_1_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_0_2")) return false;
    column_constraint_1_0_2_0(builder, level + 1);
    return true;
  }

  // ASC | DESC
  private static boolean column_constraint_1_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_0_2_0")) return false;
    boolean result;
    result = consumeToken(builder, ASC);
    if (!result) result = consumeToken(builder, DESC);
    return result;
  }

  // ( AUTOINCREMENT )?
  private static boolean column_constraint_1_0_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_0_4")) return false;
    consumeToken(builder, AUTOINCREMENT);
    return true;
  }

  // NOT NULL conflict_clause
  private static boolean column_constraint_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, NOT, NULL);
    result = result && conflict_clause(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // UNIQUE conflict_clause
  private static boolean column_constraint_1_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, UNIQUE);
    result = result && conflict_clause(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // CHECK '(' expression ')'
  private static boolean column_constraint_1_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_3")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, CHECK, LPAREN);
    result = result && expression(builder, level + 1, -1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // DEFAULT ( signed_number | literal_value | '(' expression ')' )
  private static boolean column_constraint_1_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_4")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DEFAULT);
    result = result && column_constraint_1_4_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // signed_number | literal_value | '(' expression ')'
  private static boolean column_constraint_1_4_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_4_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = signed_number(builder, level + 1);
    if (!result) result = literal_value(builder, level + 1);
    if (!result) result = column_constraint_1_4_1_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' expression ')'
  private static boolean column_constraint_1_4_1_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_4_1_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && expression(builder, level + 1, -1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // COLLATE collation_name
  private static boolean column_constraint_1_5(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_constraint_1_5")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COLLATE);
    result = result && collation_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // column_definition_name ( type_name )? ( column_constraint )*
  public static boolean column_definition(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_DEFINITION, "<column definition>");
    result = column_definition_name(builder, level + 1);
    result = result && column_definition_1(builder, level + 1);
    result = result && column_definition_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( type_name )?
  private static boolean column_definition_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition_1")) return false;
    column_definition_1_0(builder, level + 1);
    return true;
  }

  // ( type_name )
  private static boolean column_definition_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = type_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( column_constraint )*
  private static boolean column_definition_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!column_definition_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "column_definition_2", pos)) break;
    }
    return true;
  }

  // ( column_constraint )
  private static boolean column_definition_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = column_constraint(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean column_definition_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_definition_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_DEFINITION_NAME, "<column definition name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean column_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_NAME, "<column name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // ( COMMIT | END ) ( TRANSACTION )?
  public static boolean commit_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "commit_statement")) return false;
    if (!nextTokenIs(builder, "<commit statement>", COMMIT, END)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COMMIT_STATEMENT, "<commit statement>");
    result = commit_statement_0(builder, level + 1);
    result = result && commit_statement_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // COMMIT | END
  private static boolean commit_statement_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "commit_statement_0")) return false;
    boolean result;
    result = consumeToken(builder, COMMIT);
    if (!result) result = consumeToken(builder, END);
    return result;
  }

  // ( TRANSACTION )?
  private static boolean commit_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "commit_statement_1")) return false;
    consumeToken(builder, TRANSACTION);
    return true;
  }

  /* ********************************************************** */
  // UNION ALL? | INTERSECT | EXCEPT
  public static boolean compound_operator(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "compound_operator")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COMPOUND_OPERATOR, "<compound operator>");
    result = compound_operator_0(builder, level + 1);
    if (!result) result = consumeToken(builder, INTERSECT);
    if (!result) result = consumeToken(builder, EXCEPT);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // UNION ALL?
  private static boolean compound_operator_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "compound_operator_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, UNION);
    result = result && compound_operator_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ALL?
  private static boolean compound_operator_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "compound_operator_0_1")) return false;
    consumeToken(builder, ALL);
    return true;
  }

  /* ********************************************************** */
  // ( ON CONFLICT ( ROLLBACK | ABORT | FAIL | IGNORE | REPLACE ) )?
  public static boolean conflict_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "conflict_clause")) return false;
    Marker marker = enter_section_(builder, level, _NONE_, CONFLICT_CLAUSE, "<conflict clause>");
    conflict_clause_0(builder, level + 1);
    exit_section_(builder, level, marker, true, false, null);
    return true;
  }

  // ON CONFLICT ( ROLLBACK | ABORT | FAIL | IGNORE | REPLACE )
  private static boolean conflict_clause_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "conflict_clause_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, ON, CONFLICT);
    result = result && conflict_clause_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ROLLBACK | ABORT | FAIL | IGNORE | REPLACE
  private static boolean conflict_clause_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "conflict_clause_0_2")) return false;
    boolean result;
    result = consumeToken(builder, ROLLBACK);
    if (!result) result = consumeToken(builder, ABORT);
    if (!result) result = consumeToken(builder, FAIL);
    if (!result) result = consumeToken(builder, IGNORE);
    if (!result) result = consumeToken(builder, REPLACE);
    return result;
  }

  /* ********************************************************** */
  // CREATE ( UNIQUE )? INDEX ( IF NOT EXISTS )?
  //   ( database_name '.' )? index_name ON defined_table_name '(' indexed_column ( ',' indexed_column )* ')'
  //   where_clause?
  public static boolean create_index_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement")) return false;
    if (!nextTokenIs(builder, CREATE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CREATE);
    result = result && create_index_statement_1(builder, level + 1);
    result = result && consumeToken(builder, INDEX);
    result = result && create_index_statement_3(builder, level + 1);
    result = result && create_index_statement_4(builder, level + 1);
    result = result && index_name(builder, level + 1);
    result = result && consumeToken(builder, ON);
    result = result && defined_table_name(builder, level + 1);
    result = result && consumeToken(builder, LPAREN);
    result = result && indexed_column(builder, level + 1);
    result = result && create_index_statement_10(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    result = result && create_index_statement_12(builder, level + 1);
    exit_section_(builder, marker, CREATE_INDEX_STATEMENT, result);
    return result;
  }

  // ( UNIQUE )?
  private static boolean create_index_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_1")) return false;
    consumeToken(builder, UNIQUE);
    return true;
  }

  // ( IF NOT EXISTS )?
  private static boolean create_index_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_3")) return false;
    create_index_statement_3_0(builder, level + 1);
    return true;
  }

  // IF NOT EXISTS
  private static boolean create_index_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, NOT, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean create_index_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_4")) return false;
    create_index_statement_4_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean create_index_statement_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' indexed_column )*
  private static boolean create_index_statement_10(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_10")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!create_index_statement_10_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "create_index_statement_10", pos)) break;
    }
    return true;
  }

  // ',' indexed_column
  private static boolean create_index_statement_10_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_10_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && indexed_column(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // where_clause?
  private static boolean create_index_statement_12(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_index_statement_12")) return false;
    where_clause(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // CREATE ( TEMP | TEMPORARY )? TABLE ( IF NOT EXISTS )?
  //   ( database_name '.' )? table_definition_name
  //   ( '(' column_definition ( ',' column_definition )* ( ',' table_constraint )* ')' ( WITHOUT "ROWID" )? | AS (select_statement | with_clause_select_statement) )
  public static boolean create_table_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement")) return false;
    if (!nextTokenIs(builder, CREATE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CREATE);
    result = result && create_table_statement_1(builder, level + 1);
    result = result && consumeToken(builder, TABLE);
    result = result && create_table_statement_3(builder, level + 1);
    result = result && create_table_statement_4(builder, level + 1);
    result = result && table_definition_name(builder, level + 1);
    result = result && create_table_statement_6(builder, level + 1);
    exit_section_(builder, marker, CREATE_TABLE_STATEMENT, result);
    return result;
  }

  // ( TEMP | TEMPORARY )?
  private static boolean create_table_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_1")) return false;
    create_table_statement_1_0(builder, level + 1);
    return true;
  }

  // TEMP | TEMPORARY
  private static boolean create_table_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_1_0")) return false;
    boolean result;
    result = consumeToken(builder, TEMP);
    if (!result) result = consumeToken(builder, TEMPORARY);
    return result;
  }

  // ( IF NOT EXISTS )?
  private static boolean create_table_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_3")) return false;
    create_table_statement_3_0(builder, level + 1);
    return true;
  }

  // IF NOT EXISTS
  private static boolean create_table_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, NOT, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean create_table_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_4")) return false;
    create_table_statement_4_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean create_table_statement_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' column_definition ( ',' column_definition )* ( ',' table_constraint )* ')' ( WITHOUT "ROWID" )? | AS (select_statement | with_clause_select_statement)
  private static boolean create_table_statement_6(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = create_table_statement_6_0(builder, level + 1);
    if (!result) result = create_table_statement_6_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' column_definition ( ',' column_definition )* ( ',' table_constraint )* ')' ( WITHOUT "ROWID" )?
  private static boolean create_table_statement_6_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && column_definition(builder, level + 1);
    result = result && create_table_statement_6_0_2(builder, level + 1);
    result = result && create_table_statement_6_0_3(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    result = result && create_table_statement_6_0_5(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_definition )*
  private static boolean create_table_statement_6_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!create_table_statement_6_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "create_table_statement_6_0_2", pos)) break;
    }
    return true;
  }

  // ',' column_definition
  private static boolean create_table_statement_6_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_definition(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' table_constraint )*
  private static boolean create_table_statement_6_0_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!create_table_statement_6_0_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "create_table_statement_6_0_3", pos)) break;
    }
    return true;
  }

  // ',' table_constraint
  private static boolean create_table_statement_6_0_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && table_constraint(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( WITHOUT "ROWID" )?
  private static boolean create_table_statement_6_0_5(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_5")) return false;
    create_table_statement_6_0_5_0(builder, level + 1);
    return true;
  }

  // WITHOUT "ROWID"
  private static boolean create_table_statement_6_0_5_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_0_5_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, WITHOUT);
    result = result && consumeToken(builder, "ROWID");
    exit_section_(builder, marker, null, result);
    return result;
  }

  // AS (select_statement | with_clause_select_statement)
  private static boolean create_table_statement_6_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, AS);
    result = result && create_table_statement_6_1_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // select_statement | with_clause_select_statement
  private static boolean create_table_statement_6_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_table_statement_6_1_1")) return false;
    boolean result;
    result = select_statement(builder, level + 1);
    if (!result) result = with_clause_select_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // CREATE ( TEMP | TEMPORARY )? TRIGGER ( IF NOT EXISTS )?
  //   ( database_name '.' )? trigger_name ( BEFORE | AFTER | INSTEAD OF )?
  //   ( DELETE | INSERT | UPDATE ( OF column_name ( ',' column_name )* )? ) ON defined_table_name
  //   ( FOR EACH ROW )? ( WHEN expression )?
  //   BEGIN with_clause? ( update_statement | insert_statement | delete_statement | select_statement ) ';' END
  public static boolean create_trigger_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement")) return false;
    if (!nextTokenIs(builder, CREATE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CREATE);
    result = result && create_trigger_statement_1(builder, level + 1);
    result = result && consumeToken(builder, TRIGGER);
    result = result && create_trigger_statement_3(builder, level + 1);
    result = result && create_trigger_statement_4(builder, level + 1);
    result = result && trigger_name(builder, level + 1);
    result = result && create_trigger_statement_6(builder, level + 1);
    result = result && create_trigger_statement_7(builder, level + 1);
    result = result && consumeToken(builder, ON);
    result = result && defined_table_name(builder, level + 1);
    result = result && create_trigger_statement_10(builder, level + 1);
    result = result && create_trigger_statement_11(builder, level + 1);
    result = result && consumeToken(builder, BEGIN);
    result = result && create_trigger_statement_13(builder, level + 1);
    result = result && create_trigger_statement_14(builder, level + 1);
    result = result && consumeTokens(builder, 0, SEMICOLON, END);
    exit_section_(builder, marker, CREATE_TRIGGER_STATEMENT, result);
    return result;
  }

  // ( TEMP | TEMPORARY )?
  private static boolean create_trigger_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_1")) return false;
    create_trigger_statement_1_0(builder, level + 1);
    return true;
  }

  // TEMP | TEMPORARY
  private static boolean create_trigger_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_1_0")) return false;
    boolean result;
    result = consumeToken(builder, TEMP);
    if (!result) result = consumeToken(builder, TEMPORARY);
    return result;
  }

  // ( IF NOT EXISTS )?
  private static boolean create_trigger_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_3")) return false;
    create_trigger_statement_3_0(builder, level + 1);
    return true;
  }

  // IF NOT EXISTS
  private static boolean create_trigger_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, NOT, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean create_trigger_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_4")) return false;
    create_trigger_statement_4_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean create_trigger_statement_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( BEFORE | AFTER | INSTEAD OF )?
  private static boolean create_trigger_statement_6(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_6")) return false;
    create_trigger_statement_6_0(builder, level + 1);
    return true;
  }

  // BEFORE | AFTER | INSTEAD OF
  private static boolean create_trigger_statement_6_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_6_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, BEFORE);
    if (!result) result = consumeToken(builder, AFTER);
    if (!result) result = parseTokens(builder, 0, INSTEAD, OF);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // DELETE | INSERT | UPDATE ( OF column_name ( ',' column_name )* )?
  private static boolean create_trigger_statement_7(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DELETE);
    if (!result) result = consumeToken(builder, INSERT);
    if (!result) result = create_trigger_statement_7_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // UPDATE ( OF column_name ( ',' column_name )* )?
  private static boolean create_trigger_statement_7_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, UPDATE);
    result = result && create_trigger_statement_7_2_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( OF column_name ( ',' column_name )* )?
  private static boolean create_trigger_statement_7_2_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7_2_1")) return false;
    create_trigger_statement_7_2_1_0(builder, level + 1);
    return true;
  }

  // OF column_name ( ',' column_name )*
  private static boolean create_trigger_statement_7_2_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7_2_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, OF);
    result = result && column_name(builder, level + 1);
    result = result && create_trigger_statement_7_2_1_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_name )*
  private static boolean create_trigger_statement_7_2_1_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7_2_1_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!create_trigger_statement_7_2_1_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "create_trigger_statement_7_2_1_0_2", pos)) break;
    }
    return true;
  }

  // ',' column_name
  private static boolean create_trigger_statement_7_2_1_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_7_2_1_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( FOR EACH ROW )?
  private static boolean create_trigger_statement_10(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_10")) return false;
    create_trigger_statement_10_0(builder, level + 1);
    return true;
  }

  // FOR EACH ROW
  private static boolean create_trigger_statement_10_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_10_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, FOR, EACH, ROW);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( WHEN expression )?
  private static boolean create_trigger_statement_11(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_11")) return false;
    create_trigger_statement_11_0(builder, level + 1);
    return true;
  }

  // WHEN expression
  private static boolean create_trigger_statement_11_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_11_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, WHEN);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // with_clause?
  private static boolean create_trigger_statement_13(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_13")) return false;
    with_clause(builder, level + 1);
    return true;
  }

  // update_statement | insert_statement | delete_statement | select_statement
  private static boolean create_trigger_statement_14(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_trigger_statement_14")) return false;
    boolean result;
    result = update_statement(builder, level + 1);
    if (!result) result = insert_statement(builder, level + 1);
    if (!result) result = delete_statement(builder, level + 1);
    if (!result) result = select_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // CREATE ( TEMP | TEMPORARY )? VIEW ( IF NOT EXISTS )?
  //   ( database_name '.' )? view_name AS (select_statement | with_clause_select_statement)
  public static boolean create_view_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement")) return false;
    if (!nextTokenIs(builder, CREATE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CREATE);
    result = result && create_view_statement_1(builder, level + 1);
    result = result && consumeToken(builder, VIEW);
    result = result && create_view_statement_3(builder, level + 1);
    result = result && create_view_statement_4(builder, level + 1);
    result = result && view_name(builder, level + 1);
    result = result && consumeToken(builder, AS);
    result = result && create_view_statement_7(builder, level + 1);
    exit_section_(builder, marker, CREATE_VIEW_STATEMENT, result);
    return result;
  }

  // ( TEMP | TEMPORARY )?
  private static boolean create_view_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_1")) return false;
    create_view_statement_1_0(builder, level + 1);
    return true;
  }

  // TEMP | TEMPORARY
  private static boolean create_view_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_1_0")) return false;
    boolean result;
    result = consumeToken(builder, TEMP);
    if (!result) result = consumeToken(builder, TEMPORARY);
    return result;
  }

  // ( IF NOT EXISTS )?
  private static boolean create_view_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_3")) return false;
    create_view_statement_3_0(builder, level + 1);
    return true;
  }

  // IF NOT EXISTS
  private static boolean create_view_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, NOT, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean create_view_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_4")) return false;
    create_view_statement_4_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean create_view_statement_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // select_statement | with_clause_select_statement
  private static boolean create_view_statement_7(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_view_statement_7")) return false;
    boolean result;
    result = select_statement(builder, level + 1);
    if (!result) result = with_clause_select_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // CREATE VIRTUAL TABLE ( IF NOT EXISTS )?
  //   ( database_name '.' )? table_definition_name
  //   USING module_name ( '(' module_argument ( ',' module_argument )* ')' )?
  public static boolean create_virtual_table_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement")) return false;
    if (!nextTokenIs(builder, CREATE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, CREATE, VIRTUAL, TABLE);
    result = result && create_virtual_table_statement_3(builder, level + 1);
    result = result && create_virtual_table_statement_4(builder, level + 1);
    result = result && table_definition_name(builder, level + 1);
    result = result && consumeToken(builder, USING);
    result = result && module_name(builder, level + 1);
    result = result && create_virtual_table_statement_8(builder, level + 1);
    exit_section_(builder, marker, CREATE_VIRTUAL_TABLE_STATEMENT, result);
    return result;
  }

  // ( IF NOT EXISTS )?
  private static boolean create_virtual_table_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_3")) return false;
    create_virtual_table_statement_3_0(builder, level + 1);
    return true;
  }

  // IF NOT EXISTS
  private static boolean create_virtual_table_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, NOT, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean create_virtual_table_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_4")) return false;
    create_virtual_table_statement_4_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean create_virtual_table_statement_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( '(' module_argument ( ',' module_argument )* ')' )?
  private static boolean create_virtual_table_statement_8(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_8")) return false;
    create_virtual_table_statement_8_0(builder, level + 1);
    return true;
  }

  // '(' module_argument ( ',' module_argument )* ')'
  private static boolean create_virtual_table_statement_8_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_8_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && module_argument(builder, level + 1);
    result = result && create_virtual_table_statement_8_0_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' module_argument )*
  private static boolean create_virtual_table_statement_8_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_8_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!create_virtual_table_statement_8_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "create_virtual_table_statement_8_0_2", pos)) break;
    }
    return true;
  }

  // ',' module_argument
  private static boolean create_virtual_table_statement_8_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "create_virtual_table_statement_8_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && module_argument(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean database_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "database_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, DATABASE_NAME, "<database name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean defined_table_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "defined_table_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, DEFINED_TABLE_NAME, "<defined table name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // DELETE FROM single_table_statement_table ( INDEXED BY index_name | NOT INDEXED )?
  //   where_clause?
  //   ( order_clause? LIMIT expression ( ( OFFSET | ',' ) expression )? )? {
  //   }
  public static boolean delete_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement")) return false;
    if (!nextTokenIs(builder, DELETE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, DELETE, FROM);
    result = result && single_table_statement_table(builder, level + 1);
    result = result && delete_statement_3(builder, level + 1);
    result = result && delete_statement_4(builder, level + 1);
    result = result && delete_statement_5(builder, level + 1);
    result = result && delete_statement_6(builder, level + 1);
    exit_section_(builder, marker, DELETE_STATEMENT, result);
    return result;
  }

  // ( INDEXED BY index_name | NOT INDEXED )?
  private static boolean delete_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_3")) return false;
    delete_statement_3_0(builder, level + 1);
    return true;
  }

  // INDEXED BY index_name | NOT INDEXED
  private static boolean delete_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = delete_statement_3_0_0(builder, level + 1);
    if (!result) result = parseTokens(builder, 0, NOT, INDEXED);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // INDEXED BY index_name
  private static boolean delete_statement_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_3_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, INDEXED, BY);
    result = result && index_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // where_clause?
  private static boolean delete_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_4")) return false;
    where_clause(builder, level + 1);
    return true;
  }

  // ( order_clause? LIMIT expression ( ( OFFSET | ',' ) expression )? )?
  private static boolean delete_statement_5(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5")) return false;
    delete_statement_5_0(builder, level + 1);
    return true;
  }

  // order_clause? LIMIT expression ( ( OFFSET | ',' ) expression )?
  private static boolean delete_statement_5_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = delete_statement_5_0_0(builder, level + 1);
    result = result && consumeToken(builder, LIMIT);
    result = result && expression(builder, level + 1, -1);
    result = result && delete_statement_5_0_3(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // order_clause?
  private static boolean delete_statement_5_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5_0_0")) return false;
    order_clause(builder, level + 1);
    return true;
  }

  // ( ( OFFSET | ',' ) expression )?
  private static boolean delete_statement_5_0_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5_0_3")) return false;
    delete_statement_5_0_3_0(builder, level + 1);
    return true;
  }

  // ( OFFSET | ',' ) expression
  private static boolean delete_statement_5_0_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5_0_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = delete_statement_5_0_3_0_0(builder, level + 1);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // OFFSET | ','
  private static boolean delete_statement_5_0_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "delete_statement_5_0_3_0_0")) return false;
    boolean result;
    result = consumeToken(builder, OFFSET);
    if (!result) result = consumeToken(builder, COMMA);
    return result;
  }

  // {
  //   }
  private static boolean delete_statement_6(PsiBuilder builder, int level) {
    return true;
  }

  /* ********************************************************** */
  // DETACH ( DATABASE )? database_name
  public static boolean detach_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "detach_statement")) return false;
    if (!nextTokenIs(builder, DETACH)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DETACH);
    result = result && detach_statement_1(builder, level + 1);
    result = result && database_name(builder, level + 1);
    exit_section_(builder, marker, DETACH_STATEMENT, result);
    return result;
  }

  // ( DATABASE )?
  private static boolean detach_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "detach_statement_1")) return false;
    consumeToken(builder, DATABASE);
    return true;
  }

  /* ********************************************************** */
  // DROP INDEX ( IF EXISTS )? ( database_name '.' )? index_name
  public static boolean drop_index_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_index_statement")) return false;
    if (!nextTokenIs(builder, DROP)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, DROP, INDEX);
    result = result && drop_index_statement_2(builder, level + 1);
    result = result && drop_index_statement_3(builder, level + 1);
    result = result && index_name(builder, level + 1);
    exit_section_(builder, marker, DROP_INDEX_STATEMENT, result);
    return result;
  }

  // ( IF EXISTS )?
  private static boolean drop_index_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_index_statement_2")) return false;
    drop_index_statement_2_0(builder, level + 1);
    return true;
  }

  // IF EXISTS
  private static boolean drop_index_statement_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_index_statement_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean drop_index_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_index_statement_3")) return false;
    drop_index_statement_3_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean drop_index_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_index_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // DROP TABLE ( IF EXISTS )? ( database_name '.' )? defined_table_name
  public static boolean drop_table_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_table_statement")) return false;
    if (!nextTokenIs(builder, DROP)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, DROP, TABLE);
    result = result && drop_table_statement_2(builder, level + 1);
    result = result && drop_table_statement_3(builder, level + 1);
    result = result && defined_table_name(builder, level + 1);
    exit_section_(builder, marker, DROP_TABLE_STATEMENT, result);
    return result;
  }

  // ( IF EXISTS )?
  private static boolean drop_table_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_table_statement_2")) return false;
    drop_table_statement_2_0(builder, level + 1);
    return true;
  }

  // IF EXISTS
  private static boolean drop_table_statement_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_table_statement_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean drop_table_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_table_statement_3")) return false;
    drop_table_statement_3_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean drop_table_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_table_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // DROP TRIGGER ( IF EXISTS )? ( database_name '.' )? trigger_name
  public static boolean drop_trigger_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_trigger_statement")) return false;
    if (!nextTokenIs(builder, DROP)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, DROP, TRIGGER);
    result = result && drop_trigger_statement_2(builder, level + 1);
    result = result && drop_trigger_statement_3(builder, level + 1);
    result = result && trigger_name(builder, level + 1);
    exit_section_(builder, marker, DROP_TRIGGER_STATEMENT, result);
    return result;
  }

  // ( IF EXISTS )?
  private static boolean drop_trigger_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_trigger_statement_2")) return false;
    drop_trigger_statement_2_0(builder, level + 1);
    return true;
  }

  // IF EXISTS
  private static boolean drop_trigger_statement_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_trigger_statement_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean drop_trigger_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_trigger_statement_3")) return false;
    drop_trigger_statement_3_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean drop_trigger_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_trigger_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // DROP VIEW ( IF EXISTS )? ( database_name '.' )? view_name
  public static boolean drop_view_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_view_statement")) return false;
    if (!nextTokenIs(builder, DROP)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, DROP, VIEW);
    result = result && drop_view_statement_2(builder, level + 1);
    result = result && drop_view_statement_3(builder, level + 1);
    result = result && view_name(builder, level + 1);
    exit_section_(builder, marker, DROP_VIEW_STATEMENT, result);
    return result;
  }

  // ( IF EXISTS )?
  private static boolean drop_view_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_view_statement_2")) return false;
    drop_view_statement_2_0(builder, level + 1);
    return true;
  }

  // IF EXISTS
  private static boolean drop_view_statement_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_view_statement_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, IF, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean drop_view_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_view_statement_3")) return false;
    drop_view_statement_3_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean drop_view_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "drop_view_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // string_literal
  public static boolean error_message(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "error_message")) return false;
    if (!nextTokenIs(builder, "<error message>", DOUBLE_QUOTE_STRING_LITERAL, SINGLE_QUOTE_STRING_LITERAL)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, ERROR_MESSAGE, "<error message>");
    result = string_literal(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // EXPLAIN ( QUERY PLAN )?
  public static boolean explain_prefix(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "explain_prefix")) return false;
    if (!nextTokenIs(builder, EXPLAIN)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, EXPLAIN);
    result = result && explain_prefix_1(builder, level + 1);
    exit_section_(builder, marker, EXPLAIN_PREFIX, result);
    return result;
  }

  // ( QUERY PLAN )?
  private static boolean explain_prefix_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "explain_prefix_1")) return false;
    explain_prefix_1_0(builder, level + 1);
    return true;
  }

  // QUERY PLAN
  private static boolean explain_prefix_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "explain_prefix_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, QUERY, PLAN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // &(WITH|SELECT|VALUES) subquery_greedy
  static boolean expression_subquery(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "expression_subquery")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = expression_subquery_0(builder, level + 1);
    pinned = result; // pin = 1
    result = result && subquery_greedy(builder, level + 1);
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  // &(WITH|SELECT|VALUES)
  private static boolean expression_subquery_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "expression_subquery_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _AND_);
    result = expression_subquery_0_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // WITH|SELECT|VALUES
  private static boolean expression_subquery_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "expression_subquery_0_0")) return false;
    boolean result;
    result = consumeToken(builder, WITH);
    if (!result) result = consumeToken(builder, SELECT);
    if (!result) result = consumeToken(builder, VALUES);
    return result;
  }

  /* ********************************************************** */
  // REFERENCES foreign_table ( '(' column_name ( ',' column_name )* ')' )?
  //   ( ( ON ( DELETE | UPDATE ) ( SET NULL | SET DEFAULT | CASCADE | RESTRICT | NO ACTION ) | MATCH  name ) )*
  //   ( ( NOT )? DEFERRABLE ( INITIALLY DEFERRED | INITIALLY IMMEDIATE )? )?
  public static boolean foreign_key_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause")) return false;
    if (!nextTokenIs(builder, REFERENCES)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, REFERENCES);
    result = result && foreign_table(builder, level + 1);
    result = result && foreign_key_clause_2(builder, level + 1);
    result = result && foreign_key_clause_3(builder, level + 1);
    result = result && foreign_key_clause_4(builder, level + 1);
    exit_section_(builder, marker, FOREIGN_KEY_CLAUSE, result);
    return result;
  }

  // ( '(' column_name ( ',' column_name )* ')' )?
  private static boolean foreign_key_clause_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_2")) return false;
    foreign_key_clause_2_0(builder, level + 1);
    return true;
  }

  // '(' column_name ( ',' column_name )* ')'
  private static boolean foreign_key_clause_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && column_name(builder, level + 1);
    result = result && foreign_key_clause_2_0_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_name )*
  private static boolean foreign_key_clause_2_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_2_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!foreign_key_clause_2_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "foreign_key_clause_2_0_2", pos)) break;
    }
    return true;
  }

  // ',' column_name
  private static boolean foreign_key_clause_2_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_2_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ( ON ( DELETE | UPDATE ) ( SET NULL | SET DEFAULT | CASCADE | RESTRICT | NO ACTION ) | MATCH  name ) )*
  private static boolean foreign_key_clause_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!foreign_key_clause_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "foreign_key_clause_3", pos)) break;
    }
    return true;
  }

  // ON ( DELETE | UPDATE ) ( SET NULL | SET DEFAULT | CASCADE | RESTRICT | NO ACTION ) | MATCH  name
  private static boolean foreign_key_clause_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = foreign_key_clause_3_0_0(builder, level + 1);
    if (!result) result = foreign_key_clause_3_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ON ( DELETE | UPDATE ) ( SET NULL | SET DEFAULT | CASCADE | RESTRICT | NO ACTION )
  private static boolean foreign_key_clause_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ON);
    result = result && foreign_key_clause_3_0_0_1(builder, level + 1);
    result = result && foreign_key_clause_3_0_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // DELETE | UPDATE
  private static boolean foreign_key_clause_3_0_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3_0_0_1")) return false;
    boolean result;
    result = consumeToken(builder, DELETE);
    if (!result) result = consumeToken(builder, UPDATE);
    return result;
  }

  // SET NULL | SET DEFAULT | CASCADE | RESTRICT | NO ACTION
  private static boolean foreign_key_clause_3_0_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3_0_0_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = parseTokens(builder, 0, SET, NULL);
    if (!result) result = parseTokens(builder, 0, SET, DEFAULT);
    if (!result) result = consumeToken(builder, CASCADE);
    if (!result) result = consumeToken(builder, RESTRICT);
    if (!result) result = parseTokens(builder, 0, NO, ACTION);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // MATCH  name
  private static boolean foreign_key_clause_3_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_3_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, MATCH);
    result = result && name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ( NOT )? DEFERRABLE ( INITIALLY DEFERRED | INITIALLY IMMEDIATE )? )?
  private static boolean foreign_key_clause_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_4")) return false;
    foreign_key_clause_4_0(builder, level + 1);
    return true;
  }

  // ( NOT )? DEFERRABLE ( INITIALLY DEFERRED | INITIALLY IMMEDIATE )?
  private static boolean foreign_key_clause_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = foreign_key_clause_4_0_0(builder, level + 1);
    result = result && consumeToken(builder, DEFERRABLE);
    result = result && foreign_key_clause_4_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( NOT )?
  private static boolean foreign_key_clause_4_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_4_0_0")) return false;
    consumeToken(builder, NOT);
    return true;
  }

  // ( INITIALLY DEFERRED | INITIALLY IMMEDIATE )?
  private static boolean foreign_key_clause_4_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_4_0_2")) return false;
    foreign_key_clause_4_0_2_0(builder, level + 1);
    return true;
  }

  // INITIALLY DEFERRED | INITIALLY IMMEDIATE
  private static boolean foreign_key_clause_4_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_key_clause_4_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = parseTokens(builder, 0, INITIALLY, DEFERRED);
    if (!result) result = parseTokens(builder, 0, INITIALLY, IMMEDIATE);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean foreign_table(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "foreign_table")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FOREIGN_TABLE, "<foreign table>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // FROM table_or_subquery ( join_operator table_or_subquery join_constraint? )*
  public static boolean from_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_clause")) return false;
    if (!nextTokenIs(builder, FROM)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, FROM);
    result = result && table_or_subquery(builder, level + 1);
    result = result && from_clause_2(builder, level + 1);
    exit_section_(builder, marker, FROM_CLAUSE, result);
    return result;
  }

  // ( join_operator table_or_subquery join_constraint? )*
  private static boolean from_clause_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_clause_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!from_clause_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "from_clause_2", pos)) break;
    }
    return true;
  }

  // join_operator table_or_subquery join_constraint?
  private static boolean from_clause_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_clause_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = join_operator(builder, level + 1);
    result = result && table_or_subquery(builder, level + 1);
    result = result && from_clause_2_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // join_constraint?
  private static boolean from_clause_2_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_clause_2_0_2")) return false;
    join_constraint(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // ( database_name '.' )? defined_table_name ( ( AS )? table_alias_name )? ( INDEXED BY index_name | NOT INDEXED )?
  public static boolean from_table(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FROM_TABLE, "<from table>");
    result = from_table_0(builder, level + 1);
    result = result && defined_table_name(builder, level + 1);
    result = result && from_table_2(builder, level + 1);
    result = result && from_table_3(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( database_name '.' )?
  private static boolean from_table_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_0")) return false;
    from_table_0_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean from_table_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ( AS )? table_alias_name )?
  private static boolean from_table_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_2")) return false;
    from_table_2_0(builder, level + 1);
    return true;
  }

  // ( AS )? table_alias_name
  private static boolean from_table_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = from_table_2_0_0(builder, level + 1);
    result = result && table_alias_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( AS )?
  private static boolean from_table_2_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_2_0_0")) return false;
    consumeToken(builder, AS);
    return true;
  }

  // ( INDEXED BY index_name | NOT INDEXED )?
  private static boolean from_table_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_3")) return false;
    from_table_3_0(builder, level + 1);
    return true;
  }

  // INDEXED BY index_name | NOT INDEXED
  private static boolean from_table_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = from_table_3_0_0(builder, level + 1);
    if (!result) result = parseTokens(builder, 0, NOT, INDEXED);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // INDEXED BY index_name
  private static boolean from_table_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "from_table_3_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, INDEXED, BY);
    result = result && index_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // GROUP BY expression ( ',' expression )* ( HAVING expression )?
  public static boolean group_by_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "group_by_clause")) return false;
    if (!nextTokenIs(builder, GROUP)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, GROUP, BY);
    result = result && expression(builder, level + 1, -1);
    result = result && group_by_clause_3(builder, level + 1);
    result = result && group_by_clause_4(builder, level + 1);
    exit_section_(builder, marker, GROUP_BY_CLAUSE, result);
    return result;
  }

  // ( ',' expression )*
  private static boolean group_by_clause_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "group_by_clause_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!group_by_clause_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "group_by_clause_3", pos)) break;
    }
    return true;
  }

  // ',' expression
  private static boolean group_by_clause_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "group_by_clause_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( HAVING expression )?
  private static boolean group_by_clause_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "group_by_clause_4")) return false;
    group_by_clause_4_0(builder, level + 1);
    return true;
  }

  // HAVING expression
  private static boolean group_by_clause_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "group_by_clause_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, HAVING);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  static boolean index_name(PsiBuilder builder, int level) {
    return name(builder, level + 1);
  }

  /* ********************************************************** */
  // column_name ( COLLATE collation_name )? ( ASC | DESC )?
  public static boolean indexed_column(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "indexed_column")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, INDEXED_COLUMN, "<indexed column>");
    result = column_name(builder, level + 1);
    result = result && indexed_column_1(builder, level + 1);
    result = result && indexed_column_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( COLLATE collation_name )?
  private static boolean indexed_column_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "indexed_column_1")) return false;
    indexed_column_1_0(builder, level + 1);
    return true;
  }

  // COLLATE collation_name
  private static boolean indexed_column_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "indexed_column_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COLLATE);
    result = result && collation_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ASC | DESC )?
  private static boolean indexed_column_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "indexed_column_2")) return false;
    indexed_column_2_0(builder, level + 1);
    return true;
  }

  // ASC | DESC
  private static boolean indexed_column_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "indexed_column_2_0")) return false;
    boolean result;
    result = consumeToken(builder, ASC);
    if (!result) result = consumeToken(builder, DESC);
    return result;
  }

  /* ********************************************************** */
  // '(' column_name ( ',' column_name )* ')'
  public static boolean insert_columns(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_columns")) return false;
    if (!nextTokenIs(builder, LPAREN)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && column_name(builder, level + 1);
    result = result && insert_columns_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, INSERT_COLUMNS, result);
    return result;
  }

  // ( ',' column_name )*
  private static boolean insert_columns_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_columns_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!insert_columns_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "insert_columns_2", pos)) break;
    }
    return true;
  }

  // ',' column_name
  private static boolean insert_columns_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_columns_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // ( INSERT ( OR ( REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE ))? | REPLACE ) INTO
  //   single_table_statement_table insert_columns?
  //   ( select_statement | with_clause_select_statement | DEFAULT VALUES )
  public static boolean insert_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement")) return false;
    if (!nextTokenIs(builder, "<insert statement>", INSERT, REPLACE)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, INSERT_STATEMENT, "<insert statement>");
    result = insert_statement_0(builder, level + 1);
    result = result && consumeToken(builder, INTO);
    result = result && single_table_statement_table(builder, level + 1);
    result = result && insert_statement_3(builder, level + 1);
    result = result && insert_statement_4(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // INSERT ( OR ( REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE ))? | REPLACE
  private static boolean insert_statement_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = insert_statement_0_0(builder, level + 1);
    if (!result) result = consumeToken(builder, REPLACE);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // INSERT ( OR ( REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE ))?
  private static boolean insert_statement_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, INSERT);
    result = result && insert_statement_0_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( OR ( REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE ))?
  private static boolean insert_statement_0_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_0_0_1")) return false;
    insert_statement_0_0_1_0(builder, level + 1);
    return true;
  }

  // OR ( REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE )
  private static boolean insert_statement_0_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_0_0_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, OR);
    result = result && insert_statement_0_0_1_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // REPLACE |  ROLLBACK |  ABORT |  FAIL |  IGNORE
  private static boolean insert_statement_0_0_1_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_0_0_1_0_1")) return false;
    boolean result;
    result = consumeToken(builder, REPLACE);
    if (!result) result = consumeToken(builder, ROLLBACK);
    if (!result) result = consumeToken(builder, ABORT);
    if (!result) result = consumeToken(builder, FAIL);
    if (!result) result = consumeToken(builder, IGNORE);
    return result;
  }

  // insert_columns?
  private static boolean insert_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_3")) return false;
    insert_columns(builder, level + 1);
    return true;
  }

  // select_statement | with_clause_select_statement | DEFAULT VALUES
  private static boolean insert_statement_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "insert_statement_4")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = select_statement(builder, level + 1);
    if (!result) result = with_clause_select_statement(builder, level + 1);
    if (!result) result = parseTokens(builder, 0, DEFAULT, VALUES);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // ON expression | USING '(' column_name ( ',' column_name )* ')'
  public static boolean join_constraint(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_constraint")) return false;
    if (!nextTokenIs(builder, "<join constraint>", ON, USING)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, JOIN_CONSTRAINT, "<join constraint>");
    result = join_constraint_0(builder, level + 1);
    if (!result) result = join_constraint_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ON expression
  private static boolean join_constraint_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_constraint_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ON);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // USING '(' column_name ( ',' column_name )* ')'
  private static boolean join_constraint_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_constraint_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, USING, LPAREN);
    result = result && column_name(builder, level + 1);
    result = result && join_constraint_1_3(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_name )*
  private static boolean join_constraint_1_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_constraint_1_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!join_constraint_1_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "join_constraint_1_3", pos)) break;
    }
    return true;
  }

  // ',' column_name
  private static boolean join_constraint_1_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_constraint_1_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // ',' | ( NATURAL )? ( LEFT ( OUTER )? | INNER | CROSS )? JOIN
  public static boolean join_operator(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, JOIN_OPERATOR, "<join operator>");
    result = consumeToken(builder, COMMA);
    if (!result) result = join_operator_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( NATURAL )? ( LEFT ( OUTER )? | INNER | CROSS )? JOIN
  private static boolean join_operator_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = join_operator_1_0(builder, level + 1);
    result = result && join_operator_1_1(builder, level + 1);
    result = result && consumeToken(builder, JOIN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( NATURAL )?
  private static boolean join_operator_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1_0")) return false;
    consumeToken(builder, NATURAL);
    return true;
  }

  // ( LEFT ( OUTER )? | INNER | CROSS )?
  private static boolean join_operator_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1_1")) return false;
    join_operator_1_1_0(builder, level + 1);
    return true;
  }

  // LEFT ( OUTER )? | INNER | CROSS
  private static boolean join_operator_1_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = join_operator_1_1_0_0(builder, level + 1);
    if (!result) result = consumeToken(builder, INNER);
    if (!result) result = consumeToken(builder, CROSS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // LEFT ( OUTER )?
  private static boolean join_operator_1_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LEFT);
    result = result && join_operator_1_1_0_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( OUTER )?
  private static boolean join_operator_1_1_0_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "join_operator_1_1_0_0_1")) return false;
    consumeToken(builder, OUTER);
    return true;
  }

  /* ********************************************************** */
  // LIMIT expression ( ( OFFSET | ',' ) expression )?
  public static boolean limit_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "limit_clause")) return false;
    if (!nextTokenIs(builder, LIMIT)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LIMIT);
    result = result && expression(builder, level + 1, -1);
    result = result && limit_clause_2(builder, level + 1);
    exit_section_(builder, marker, LIMIT_CLAUSE, result);
    return result;
  }

  // ( ( OFFSET | ',' ) expression )?
  private static boolean limit_clause_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "limit_clause_2")) return false;
    limit_clause_2_0(builder, level + 1);
    return true;
  }

  // ( OFFSET | ',' ) expression
  private static boolean limit_clause_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "limit_clause_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = limit_clause_2_0_0(builder, level + 1);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // OFFSET | ','
  private static boolean limit_clause_2_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "limit_clause_2_0_0")) return false;
    boolean result;
    result = consumeToken(builder, OFFSET);
    if (!result) result = consumeToken(builder, COMMA);
    return result;
  }

  /* ********************************************************** */
  // NUMERIC_LITERAL
  //   | string_literal // X marks a blob literal
  //   | NULL
  //   | CURRENT_TIME
  //   | CURRENT_DATE
  //   | CURRENT_TIMESTAMP
  static boolean literal_value(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "literal_value")) return false;
    boolean result;
    result = consumeToken(builder, NUMERIC_LITERAL);
    if (!result) result = string_literal(builder, level + 1);
    if (!result) result = consumeToken(builder, NULL);
    if (!result) result = consumeToken(builder, CURRENT_TIME);
    if (!result) result = consumeToken(builder, CURRENT_DATE);
    if (!result) result = consumeToken(builder, CURRENT_TIMESTAMP);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean module_argument(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "module_argument")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, MODULE_ARGUMENT, "<module argument>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean module_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "module_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, MODULE_NAME, "<module name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // IDENTIFIER | BRACKET_LITERAL | BACKTICK_LITERAL | string_literal
  static boolean name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "name")) return false;
    boolean result;
    result = consumeToken(builder, IDENTIFIER);
    if (!result) result = consumeToken(builder, BRACKET_LITERAL);
    if (!result) result = consumeToken(builder, BACKTICK_LITERAL);
    if (!result) result = string_literal(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // ORDER BY ordering_term ( ',' ordering_term )*
  public static boolean order_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "order_clause")) return false;
    if (!nextTokenIs(builder, ORDER)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, ORDER, BY);
    result = result && ordering_term(builder, level + 1);
    result = result && order_clause_3(builder, level + 1);
    exit_section_(builder, marker, ORDER_CLAUSE, result);
    return result;
  }

  // ( ',' ordering_term )*
  private static boolean order_clause_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "order_clause_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!order_clause_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "order_clause_3", pos)) break;
    }
    return true;
  }

  // ',' ordering_term
  private static boolean order_clause_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "order_clause_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && ordering_term(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // expression ( COLLATE collation_name )? ( ASC | DESC )?
  public static boolean ordering_term(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "ordering_term")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, ORDERING_TERM, "<ordering term>");
    result = expression(builder, level + 1, -1);
    result = result && ordering_term_1(builder, level + 1);
    result = result && ordering_term_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( COLLATE collation_name )?
  private static boolean ordering_term_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "ordering_term_1")) return false;
    ordering_term_1_0(builder, level + 1);
    return true;
  }

  // COLLATE collation_name
  private static boolean ordering_term_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "ordering_term_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COLLATE);
    result = result && collation_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ASC | DESC )?
  private static boolean ordering_term_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "ordering_term_2")) return false;
    ordering_term_2_0(builder, level + 1);
    return true;
  }

  // ASC | DESC
  private static boolean ordering_term_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "ordering_term_2_0")) return false;
    boolean result;
    result = consumeToken(builder, ASC);
    if (!result) result = consumeToken(builder, DESC);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean pragma_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, PRAGMA_NAME, "<pragma name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // PRAGMA ( database_name '.' )? pragma_name ( '=' pragma_value | '(' pragma_value ')' )?
  public static boolean pragma_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement")) return false;
    if (!nextTokenIs(builder, PRAGMA)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, PRAGMA);
    result = result && pragma_statement_1(builder, level + 1);
    result = result && pragma_name(builder, level + 1);
    result = result && pragma_statement_3(builder, level + 1);
    exit_section_(builder, marker, PRAGMA_STATEMENT, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean pragma_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_1")) return false;
    pragma_statement_1_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean pragma_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( '=' pragma_value | '(' pragma_value ')' )?
  private static boolean pragma_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_3")) return false;
    pragma_statement_3_0(builder, level + 1);
    return true;
  }

  // '=' pragma_value | '(' pragma_value ')'
  private static boolean pragma_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = pragma_statement_3_0_0(builder, level + 1);
    if (!result) result = pragma_statement_3_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '=' pragma_value
  private static boolean pragma_statement_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_3_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, EQ);
    result = result && pragma_value(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' pragma_value ')'
  private static boolean pragma_statement_3_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_statement_3_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && pragma_value(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // signed_number | name | string_literal | ON | NO | FULL | DELETE | EXCLUSIVE | DEFAULT
  public static boolean pragma_value(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "pragma_value")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, PRAGMA_VALUE, "<pragma value>");
    result = signed_number(builder, level + 1);
    if (!result) result = name(builder, level + 1);
    if (!result) result = string_literal(builder, level + 1);
    if (!result) result = consumeToken(builder, ON);
    if (!result) result = consumeToken(builder, NO);
    if (!result) result = consumeToken(builder, FULL);
    if (!result) result = consumeToken(builder, DELETE);
    if (!result) result = consumeToken(builder, EXCLUSIVE);
    if (!result) result = consumeToken(builder, DEFAULT);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // REINDEX ( collation_name | ( database_name '.' )? ( defined_table_name | index_name ) )?
  public static boolean reindex_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement")) return false;
    if (!nextTokenIs(builder, REINDEX)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, REINDEX);
    result = result && reindex_statement_1(builder, level + 1);
    exit_section_(builder, marker, REINDEX_STATEMENT, result);
    return result;
  }

  // ( collation_name | ( database_name '.' )? ( defined_table_name | index_name ) )?
  private static boolean reindex_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1")) return false;
    reindex_statement_1_0(builder, level + 1);
    return true;
  }

  // collation_name | ( database_name '.' )? ( defined_table_name | index_name )
  private static boolean reindex_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = collation_name(builder, level + 1);
    if (!result) result = reindex_statement_1_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )? ( defined_table_name | index_name )
  private static boolean reindex_statement_1_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = reindex_statement_1_0_1_0(builder, level + 1);
    result = result && reindex_statement_1_0_1_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean reindex_statement_1_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1_0_1_0")) return false;
    reindex_statement_1_0_1_0_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean reindex_statement_1_0_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1_0_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // defined_table_name | index_name
  private static boolean reindex_statement_1_0_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "reindex_statement_1_0_1_1")) return false;
    boolean result;
    result = defined_table_name(builder, level + 1);
    if (!result) result = index_name(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // RELEASE ( SAVEPOINT )? savepoint_name
  public static boolean release_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "release_statement")) return false;
    if (!nextTokenIs(builder, RELEASE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, RELEASE);
    result = result && release_statement_1(builder, level + 1);
    result = result && savepoint_name(builder, level + 1);
    exit_section_(builder, marker, RELEASE_STATEMENT, result);
    return result;
  }

  // ( SAVEPOINT )?
  private static boolean release_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "release_statement_1")) return false;
    consumeToken(builder, SAVEPOINT);
    return true;
  }

  /* ********************************************************** */
  // RENAME COLUMN? column_name TO name
  static boolean rename_column_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rename_column_statement")) return false;
    if (!nextTokenIs(builder, RENAME)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, RENAME);
    result = result && rename_column_statement_1(builder, level + 1);
    result = result && column_name(builder, level + 1);
    result = result && consumeToken(builder, TO);
    result = result && name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // COLUMN?
  private static boolean rename_column_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rename_column_statement_1")) return false;
    consumeToken(builder, COLUMN);
    return true;
  }

  /* ********************************************************** */
  // RENAME TO table_definition_name
  static boolean rename_table_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rename_table_statement")) return false;
    if (!nextTokenIs(builder, RENAME)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, RENAME, TO);
    result = result && table_definition_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // '*'
  //   | selected_table_name '.' '*'
  //   | expression ( ( AS )? column_alias_name )?
  public static boolean result_column(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, RESULT_COLUMN, "<result column>");
    result = consumeToken(builder, STAR);
    if (!result) result = result_column_1(builder, level + 1);
    if (!result) result = result_column_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // selected_table_name '.' '*'
  private static boolean result_column_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = selected_table_name(builder, level + 1);
    result = result && consumeTokens(builder, 0, DOT, STAR);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // expression ( ( AS )? column_alias_name )?
  private static boolean result_column_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = expression(builder, level + 1, -1);
    result = result && result_column_2_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ( AS )? column_alias_name )?
  private static boolean result_column_2_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column_2_1")) return false;
    result_column_2_1_0(builder, level + 1);
    return true;
  }

  // ( AS )? column_alias_name
  private static boolean result_column_2_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column_2_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = result_column_2_1_0_0(builder, level + 1);
    result = result && column_alias_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( AS )?
  private static boolean result_column_2_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_column_2_1_0_0")) return false;
    consumeToken(builder, AS);
    return true;
  }

  /* ********************************************************** */
  // result_column ( ',' result_column )*
  public static boolean result_columns(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_columns")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, RESULT_COLUMNS, "<result columns>");
    result = result_column(builder, level + 1);
    result = result && result_columns_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( ',' result_column )*
  private static boolean result_columns_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_columns_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!result_columns_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "result_columns_1", pos)) break;
    }
    return true;
  }

  // ',' result_column
  private static boolean result_columns_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "result_columns_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && result_column(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // ROLLBACK ( TRANSACTION )? ( TO ( SAVEPOINT )? savepoint_name )?
  public static boolean rollback_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rollback_statement")) return false;
    if (!nextTokenIs(builder, ROLLBACK)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ROLLBACK);
    result = result && rollback_statement_1(builder, level + 1);
    result = result && rollback_statement_2(builder, level + 1);
    exit_section_(builder, marker, ROLLBACK_STATEMENT, result);
    return result;
  }

  // ( TRANSACTION )?
  private static boolean rollback_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rollback_statement_1")) return false;
    consumeToken(builder, TRANSACTION);
    return true;
  }

  // ( TO ( SAVEPOINT )? savepoint_name )?
  private static boolean rollback_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rollback_statement_2")) return false;
    rollback_statement_2_0(builder, level + 1);
    return true;
  }

  // TO ( SAVEPOINT )? savepoint_name
  private static boolean rollback_statement_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rollback_statement_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, TO);
    result = result && rollback_statement_2_0_1(builder, level + 1);
    result = result && savepoint_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( SAVEPOINT )?
  private static boolean rollback_statement_2_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rollback_statement_2_0_1")) return false;
    consumeToken(builder, SAVEPOINT);
    return true;
  }

  /* ********************************************************** */
  // statement ';'?
  static boolean root(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "root")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = statement(builder, level + 1);
    result = result && root_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ';'?
  private static boolean root_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "root_1")) return false;
    consumeToken(builder, SEMICOLON);
    return true;
  }

  /* ********************************************************** */
  // name
  public static boolean savepoint_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "savepoint_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SAVEPOINT_NAME, "<savepoint name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // SAVEPOINT savepoint_name
  public static boolean savepoint_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "savepoint_statement")) return false;
    if (!nextTokenIs(builder, SAVEPOINT)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, SAVEPOINT);
    result = result && savepoint_name(builder, level + 1);
    exit_section_(builder, marker, SAVEPOINT_STATEMENT, result);
    return result;
  }

  /* ********************************************************** */
  // select_core_select | select_core_values
  public static boolean select_core(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core")) return false;
    if (!nextTokenIs(builder, "<select core>", SELECT, VALUES)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SELECT_CORE, "<select core>");
    result = select_core_select(builder, level + 1);
    if (!result) result = select_core_values(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // SELECT ( DISTINCT | ALL )? result_columns from_clause? where_clause? group_by_clause?
  public static boolean select_core_select(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select")) return false;
    if (!nextTokenIs(builder, SELECT)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, SELECT);
    result = result && select_core_select_1(builder, level + 1);
    result = result && result_columns(builder, level + 1);
    result = result && select_core_select_3(builder, level + 1);
    result = result && select_core_select_4(builder, level + 1);
    result = result && select_core_select_5(builder, level + 1);
    exit_section_(builder, marker, SELECT_CORE_SELECT, result);
    return result;
  }

  // ( DISTINCT | ALL )?
  private static boolean select_core_select_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select_1")) return false;
    select_core_select_1_0(builder, level + 1);
    return true;
  }

  // DISTINCT | ALL
  private static boolean select_core_select_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select_1_0")) return false;
    boolean result;
    result = consumeToken(builder, DISTINCT);
    if (!result) result = consumeToken(builder, ALL);
    return result;
  }

  // from_clause?
  private static boolean select_core_select_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select_3")) return false;
    from_clause(builder, level + 1);
    return true;
  }

  // where_clause?
  private static boolean select_core_select_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select_4")) return false;
    where_clause(builder, level + 1);
    return true;
  }

  // group_by_clause?
  private static boolean select_core_select_5(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_select_5")) return false;
    group_by_clause(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // VALUES '(' expression ( ',' expression )* ')' ( ',' '(' expression ( ',' expression )* ')' )*
  public static boolean select_core_values(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values")) return false;
    if (!nextTokenIs(builder, VALUES)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, VALUES, LPAREN);
    result = result && expression(builder, level + 1, -1);
    result = result && select_core_values_3(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    result = result && select_core_values_5(builder, level + 1);
    exit_section_(builder, marker, SELECT_CORE_VALUES, result);
    return result;
  }

  // ( ',' expression )*
  private static boolean select_core_values_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!select_core_values_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "select_core_values_3", pos)) break;
    }
    return true;
  }

  // ',' expression
  private static boolean select_core_values_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' '(' expression ( ',' expression )* ')' )*
  private static boolean select_core_values_5(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_5")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!select_core_values_5_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "select_core_values_5", pos)) break;
    }
    return true;
  }

  // ',' '(' expression ( ',' expression )* ')'
  private static boolean select_core_values_5_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_5_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, COMMA, LPAREN);
    result = result && expression(builder, level + 1, -1);
    result = result && select_core_values_5_0_3(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' expression )*
  private static boolean select_core_values_5_0_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_5_0_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!select_core_values_5_0_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "select_core_values_5_0_3", pos)) break;
    }
    return true;
  }

  // ',' expression
  private static boolean select_core_values_5_0_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_core_values_5_0_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // select_core (compound_operator select_core)* order_clause? limit_clause?
  public static boolean select_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_statement")) return false;
    if (!nextTokenIs(builder, "<select statement>", SELECT, VALUES)) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SELECT_STATEMENT, "<select statement>");
    result = select_core(builder, level + 1);
    result = result && select_statement_1(builder, level + 1);
    result = result && select_statement_2(builder, level + 1);
    result = result && select_statement_3(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // (compound_operator select_core)*
  private static boolean select_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_statement_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!select_statement_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "select_statement_1", pos)) break;
    }
    return true;
  }

  // compound_operator select_core
  private static boolean select_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_statement_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = compound_operator(builder, level + 1);
    result = result && select_core(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // order_clause?
  private static boolean select_statement_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_statement_2")) return false;
    order_clause(builder, level + 1);
    return true;
  }

  // limit_clause?
  private static boolean select_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_statement_3")) return false;
    limit_clause(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // '(' &(SELECT|VALUES|WITH) subquery_greedy ')' ( ( AS )? table_alias_name )?
  public static boolean select_subquery(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery")) return false;
    if (!nextTokenIs(builder, LPAREN)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, SELECT_SUBQUERY, null);
    result = consumeToken(builder, LPAREN);
    result = result && select_subquery_1(builder, level + 1);
    pinned = result; // pin = 2
    result = result && report_error_(builder, subquery_greedy(builder, level + 1));
    result = pinned && report_error_(builder, consumeToken(builder, RPAREN)) && result;
    result = pinned && select_subquery_4(builder, level + 1) && result;
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  // &(SELECT|VALUES|WITH)
  private static boolean select_subquery_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _AND_);
    result = select_subquery_1_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // SELECT|VALUES|WITH
  private static boolean select_subquery_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery_1_0")) return false;
    boolean result;
    result = consumeToken(builder, SELECT);
    if (!result) result = consumeToken(builder, VALUES);
    if (!result) result = consumeToken(builder, WITH);
    return result;
  }

  // ( ( AS )? table_alias_name )?
  private static boolean select_subquery_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery_4")) return false;
    select_subquery_4_0(builder, level + 1);
    return true;
  }

  // ( AS )? table_alias_name
  private static boolean select_subquery_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = select_subquery_4_0_0(builder, level + 1);
    result = result && table_alias_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( AS )?
  private static boolean select_subquery_4_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "select_subquery_4_0_0")) return false;
    consumeToken(builder, AS);
    return true;
  }

  /* ********************************************************** */
  // name
  public static boolean selected_table_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "selected_table_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SELECTED_TABLE_NAME, "<selected table name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // ( '+' | '-' )? NUMERIC_LITERAL
  public static boolean signed_number(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "signed_number")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SIGNED_NUMBER, "<signed number>");
    result = signed_number_0(builder, level + 1);
    result = result && consumeToken(builder, NUMERIC_LITERAL);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( '+' | '-' )?
  private static boolean signed_number_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "signed_number_0")) return false;
    signed_number_0_0(builder, level + 1);
    return true;
  }

  // '+' | '-'
  private static boolean signed_number_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "signed_number_0_0")) return false;
    boolean result;
    result = consumeToken(builder, PLUS);
    if (!result) result = consumeToken(builder, MINUS);
    return result;
  }

  /* ********************************************************** */
  // ( database_name '.' )? defined_table_name
  public static boolean single_table_statement_table(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "single_table_statement_table")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, SINGLE_TABLE_STATEMENT_TABLE, "<single table statement table>");
    result = single_table_statement_table_0(builder, level + 1);
    result = result && defined_table_name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( database_name '.' )?
  private static boolean single_table_statement_table_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "single_table_statement_table_0")) return false;
    single_table_statement_table_0_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean single_table_statement_table_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "single_table_statement_table_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // explain_prefix ?
  //   (
  //   select_statement
  //   | update_statement
  //   | insert_statement
  //   | delete_statement
  //   | with_clause_statement
  //   | alter_table_statement
  //   | analyze_statement
  //   | attach_statement
  //   | begin_statement
  //   | commit_statement
  //   | create_index_statement
  //   | create_table_statement
  //   | create_trigger_statement
  //   | create_view_statement
  //   | create_virtual_table_statement
  //   | detach_statement
  //   | drop_index_statement
  //   | drop_table_statement
  //   | drop_trigger_statement
  //   | drop_view_statement
  //   | pragma_statement
  //   | reindex_statement
  //   | release_statement
  //   | rollback_statement
  //   | savepoint_statement
  //   | vacuum_statement
  //   )
  static boolean statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "statement")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, null, "<statement>");
    result = statement_0(builder, level + 1);
    result = result && statement_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // explain_prefix ?
  private static boolean statement_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "statement_0")) return false;
    explain_prefix(builder, level + 1);
    return true;
  }

  // select_statement
  //   | update_statement
  //   | insert_statement
  //   | delete_statement
  //   | with_clause_statement
  //   | alter_table_statement
  //   | analyze_statement
  //   | attach_statement
  //   | begin_statement
  //   | commit_statement
  //   | create_index_statement
  //   | create_table_statement
  //   | create_trigger_statement
  //   | create_view_statement
  //   | create_virtual_table_statement
  //   | detach_statement
  //   | drop_index_statement
  //   | drop_table_statement
  //   | drop_trigger_statement
  //   | drop_view_statement
  //   | pragma_statement
  //   | reindex_statement
  //   | release_statement
  //   | rollback_statement
  //   | savepoint_statement
  //   | vacuum_statement
  private static boolean statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "statement_1")) return false;
    boolean result;
    result = select_statement(builder, level + 1);
    if (!result) result = update_statement(builder, level + 1);
    if (!result) result = insert_statement(builder, level + 1);
    if (!result) result = delete_statement(builder, level + 1);
    if (!result) result = with_clause_statement(builder, level + 1);
    if (!result) result = alter_table_statement(builder, level + 1);
    if (!result) result = analyze_statement(builder, level + 1);
    if (!result) result = attach_statement(builder, level + 1);
    if (!result) result = begin_statement(builder, level + 1);
    if (!result) result = commit_statement(builder, level + 1);
    if (!result) result = create_index_statement(builder, level + 1);
    if (!result) result = create_table_statement(builder, level + 1);
    if (!result) result = create_trigger_statement(builder, level + 1);
    if (!result) result = create_view_statement(builder, level + 1);
    if (!result) result = create_virtual_table_statement(builder, level + 1);
    if (!result) result = detach_statement(builder, level + 1);
    if (!result) result = drop_index_statement(builder, level + 1);
    if (!result) result = drop_table_statement(builder, level + 1);
    if (!result) result = drop_trigger_statement(builder, level + 1);
    if (!result) result = drop_view_statement(builder, level + 1);
    if (!result) result = pragma_statement(builder, level + 1);
    if (!result) result = reindex_statement(builder, level + 1);
    if (!result) result = release_statement(builder, level + 1);
    if (!result) result = rollback_statement(builder, level + 1);
    if (!result) result = savepoint_statement(builder, level + 1);
    if (!result) result = vacuum_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // SINGLE_QUOTE_STRING_LITERAL | DOUBLE_QUOTE_STRING_LITERAL
  static boolean string_literal(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "string_literal")) return false;
    if (!nextTokenIs(builder, "", DOUBLE_QUOTE_STRING_LITERAL, SINGLE_QUOTE_STRING_LITERAL)) return false;
    boolean result;
    result = consumeToken(builder, SINGLE_QUOTE_STRING_LITERAL);
    if (!result) result = consumeToken(builder, DOUBLE_QUOTE_STRING_LITERAL);
    return result;
  }

  /* ********************************************************** */
  // select_statement | with_clause_select_statement
  static boolean subquery_greedy(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "subquery_greedy")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = select_statement(builder, level + 1);
    if (!result) result = with_clause_select_statement(builder, level + 1);
    exit_section_(builder, level, marker, result, false, AndroidSqlParser::subquery_recover);
    return result;
  }

  /* ********************************************************** */
  // !')'
  static boolean subquery_recover(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "subquery_recover")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !consumeToken(builder, RPAREN);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean table_alias_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_alias_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TABLE_ALIAS_NAME, "<table alias name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // ( CONSTRAINT  name )?
  //   ( ( PRIMARY KEY | UNIQUE ) '(' indexed_column ( ',' indexed_column )* ')' conflict_clause
  //   | CHECK '(' expression ')'
  //   | FOREIGN KEY '(' column_name ( ',' column_name )* ')' foreign_key_clause )
  public static boolean table_constraint(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TABLE_CONSTRAINT, "<table constraint>");
    result = table_constraint_0(builder, level + 1);
    result = result && table_constraint_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( CONSTRAINT  name )?
  private static boolean table_constraint_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_0")) return false;
    table_constraint_0_0(builder, level + 1);
    return true;
  }

  // CONSTRAINT  name
  private static boolean table_constraint_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, CONSTRAINT);
    result = result && name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( PRIMARY KEY | UNIQUE ) '(' indexed_column ( ',' indexed_column )* ')' conflict_clause
  //   | CHECK '(' expression ')'
  //   | FOREIGN KEY '(' column_name ( ',' column_name )* ')' foreign_key_clause
  private static boolean table_constraint_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = table_constraint_1_0(builder, level + 1);
    if (!result) result = table_constraint_1_1(builder, level + 1);
    if (!result) result = table_constraint_1_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( PRIMARY KEY | UNIQUE ) '(' indexed_column ( ',' indexed_column )* ')' conflict_clause
  private static boolean table_constraint_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = table_constraint_1_0_0(builder, level + 1);
    result = result && consumeToken(builder, LPAREN);
    result = result && indexed_column(builder, level + 1);
    result = result && table_constraint_1_0_3(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    result = result && conflict_clause(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // PRIMARY KEY | UNIQUE
  private static boolean table_constraint_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = parseTokens(builder, 0, PRIMARY, KEY);
    if (!result) result = consumeToken(builder, UNIQUE);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' indexed_column )*
  private static boolean table_constraint_1_0_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_0_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!table_constraint_1_0_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "table_constraint_1_0_3", pos)) break;
    }
    return true;
  }

  // ',' indexed_column
  private static boolean table_constraint_1_0_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_0_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && indexed_column(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // CHECK '(' expression ')'
  private static boolean table_constraint_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, CHECK, LPAREN);
    result = result && expression(builder, level + 1, -1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // FOREIGN KEY '(' column_name ( ',' column_name )* ')' foreign_key_clause
  private static boolean table_constraint_1_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, FOREIGN, KEY, LPAREN);
    result = result && column_name(builder, level + 1);
    result = result && table_constraint_1_2_4(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    result = result && foreign_key_clause(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_name )*
  private static boolean table_constraint_1_2_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_2_4")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!table_constraint_1_2_4_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "table_constraint_1_2_4", pos)) break;
    }
    return true;
  }

  // ',' column_name
  private static boolean table_constraint_1_2_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_constraint_1_2_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean table_definition_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_definition_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TABLE_DEFINITION_NAME, "<table definition name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean table_or_index_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_or_index_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TABLE_OR_INDEX_NAME, "<table or index name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // from_table | select_subquery | '(' table_or_subquery ')'
  public static boolean table_or_subquery(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_or_subquery")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TABLE_OR_SUBQUERY, "<table or subquery>");
    result = from_table(builder, level + 1);
    if (!result) result = select_subquery(builder, level + 1);
    if (!result) result = table_or_subquery_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // '(' table_or_subquery ')'
  private static boolean table_or_subquery_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "table_or_subquery_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && table_or_subquery(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean trigger_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "trigger_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TRIGGER_NAME, "<trigger name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // name ( '(' signed_number ')' | '(' signed_number ',' signed_number ')' )?
  public static boolean type_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TYPE_NAME, "<type name>");
    result = name(builder, level + 1);
    result = result && type_name_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( '(' signed_number ')' | '(' signed_number ',' signed_number ')' )?
  private static boolean type_name_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_name_1")) return false;
    type_name_1_0(builder, level + 1);
    return true;
  }

  // '(' signed_number ')' | '(' signed_number ',' signed_number ')'
  private static boolean type_name_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_name_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = type_name_1_0_0(builder, level + 1);
    if (!result) result = type_name_1_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' signed_number ')'
  private static boolean type_name_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_name_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && signed_number(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' signed_number ',' signed_number ')'
  private static boolean type_name_1_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_name_1_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && signed_number(builder, level + 1);
    result = result && consumeToken(builder, COMMA);
    result = result && signed_number(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // UPDATE ( OR ROLLBACK | OR ABORT | OR REPLACE | OR FAIL | OR IGNORE )? single_table_statement_table ( INDEXED BY index_name | NOT INDEXED )?
  //   SET column_name '=' expression ( ',' column_name '=' expression )*
  //   where_clause?
  public static boolean update_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement")) return false;
    if (!nextTokenIs(builder, UPDATE)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, UPDATE_STATEMENT, null);
    result = consumeToken(builder, UPDATE);
    pinned = result; // pin = 1
    result = result && report_error_(builder, update_statement_1(builder, level + 1));
    result = pinned && report_error_(builder, single_table_statement_table(builder, level + 1)) && result;
    result = pinned && report_error_(builder, update_statement_3(builder, level + 1)) && result;
    result = pinned && report_error_(builder, consumeToken(builder, SET)) && result;
    result = pinned && report_error_(builder, column_name(builder, level + 1)) && result;
    result = pinned && report_error_(builder, consumeToken(builder, EQ)) && result;
    result = pinned && report_error_(builder, expression(builder, level + 1, -1)) && result;
    result = pinned && report_error_(builder, update_statement_8(builder, level + 1)) && result;
    result = pinned && update_statement_9(builder, level + 1) && result;
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  // ( OR ROLLBACK | OR ABORT | OR REPLACE | OR FAIL | OR IGNORE )?
  private static boolean update_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_1")) return false;
    update_statement_1_0(builder, level + 1);
    return true;
  }

  // OR ROLLBACK | OR ABORT | OR REPLACE | OR FAIL | OR IGNORE
  private static boolean update_statement_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = parseTokens(builder, 0, OR, ROLLBACK);
    if (!result) result = parseTokens(builder, 0, OR, ABORT);
    if (!result) result = parseTokens(builder, 0, OR, REPLACE);
    if (!result) result = parseTokens(builder, 0, OR, FAIL);
    if (!result) result = parseTokens(builder, 0, OR, IGNORE);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( INDEXED BY index_name | NOT INDEXED )?
  private static boolean update_statement_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_3")) return false;
    update_statement_3_0(builder, level + 1);
    return true;
  }

  // INDEXED BY index_name | NOT INDEXED
  private static boolean update_statement_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = update_statement_3_0_0(builder, level + 1);
    if (!result) result = parseTokens(builder, 0, NOT, INDEXED);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // INDEXED BY index_name
  private static boolean update_statement_3_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_3_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, INDEXED, BY);
    result = result && index_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_name '=' expression )*
  private static boolean update_statement_8(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_8")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!update_statement_8_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "update_statement_8", pos)) break;
    }
    return true;
  }

  // ',' column_name '=' expression
  private static boolean update_statement_8_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_8_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_name(builder, level + 1);
    result = result && consumeToken(builder, EQ);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // where_clause?
  private static boolean update_statement_9(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "update_statement_9")) return false;
    where_clause(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // VACUUM
  public static boolean vacuum_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "vacuum_statement")) return false;
    if (!nextTokenIs(builder, VACUUM)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, VACUUM);
    exit_section_(builder, marker, VACUUM_STATEMENT, result);
    return result;
  }

  /* ********************************************************** */
  // name
  public static boolean view_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "view_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, VIEW_NAME, "<view name>");
    result = name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // WHERE expression
  public static boolean where_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "where_clause")) return false;
    if (!nextTokenIs(builder, WHERE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, WHERE);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, WHERE_CLAUSE, result);
    return result;
  }

  /* ********************************************************** */
  // &WITH with_clause_greedy
  public static boolean with_clause(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause")) return false;
    if (!nextTokenIs(builder, WITH)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = with_clause_0(builder, level + 1);
    result = result && with_clause_greedy(builder, level + 1);
    exit_section_(builder, marker, WITH_CLAUSE, result);
    return result;
  }

  // &WITH
  private static boolean with_clause_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _AND_);
    result = consumeToken(builder, WITH);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // WITH ( RECURSIVE )? with_clause_table ( ',' with_clause_table )*
  static boolean with_clause_greedy(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_greedy")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = consumeToken(builder, WITH);
    pinned = result; // pin = 1
    result = result && report_error_(builder, with_clause_greedy_1(builder, level + 1));
    result = pinned && report_error_(builder, with_clause_table(builder, level + 1)) && result;
    result = pinned && with_clause_greedy_3(builder, level + 1) && result;
    exit_section_(builder, level, marker, result, pinned, AndroidSqlParser::with_clause_recover);
    return result || pinned;
  }

  // ( RECURSIVE )?
  private static boolean with_clause_greedy_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_greedy_1")) return false;
    consumeToken(builder, RECURSIVE);
    return true;
  }

  // ( ',' with_clause_table )*
  private static boolean with_clause_greedy_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_greedy_3")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!with_clause_greedy_3_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "with_clause_greedy_3", pos)) break;
    }
    return true;
  }

  // ',' with_clause_table
  private static boolean with_clause_greedy_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_greedy_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && with_clause_table(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // !(DELETE | INSERT | REPLACE | SELECT | UPDATE | VALUES | ')')
  static boolean with_clause_recover(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_recover")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !with_clause_recover_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // DELETE | INSERT | REPLACE | SELECT | UPDATE | VALUES | ')'
  private static boolean with_clause_recover_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_recover_0")) return false;
    boolean result;
    result = consumeToken(builder, DELETE);
    if (!result) result = consumeToken(builder, INSERT);
    if (!result) result = consumeToken(builder, REPLACE);
    if (!result) result = consumeToken(builder, SELECT);
    if (!result) result = consumeToken(builder, UPDATE);
    if (!result) result = consumeToken(builder, VALUES);
    if (!result) result = consumeToken(builder, RPAREN);
    return result;
  }

  /* ********************************************************** */
  // with_clause select_statement
  public static boolean with_clause_select_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_select_statement")) return false;
    if (!nextTokenIs(builder, WITH)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = with_clause(builder, level + 1);
    result = result && select_statement(builder, level + 1);
    exit_section_(builder, marker, WITH_CLAUSE_SELECT_STATEMENT, result);
    return result;
  }

  /* ********************************************************** */
  // with_clause (delete_statement | insert_statement | update_statement | select_statement)
  public static boolean with_clause_statement(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_statement")) return false;
    if (!nextTokenIs(builder, WITH)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = with_clause(builder, level + 1);
    result = result && with_clause_statement_1(builder, level + 1);
    exit_section_(builder, marker, WITH_CLAUSE_STATEMENT, result);
    return result;
  }

  // delete_statement | insert_statement | update_statement | select_statement
  private static boolean with_clause_statement_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_statement_1")) return false;
    boolean result;
    result = delete_statement(builder, level + 1);
    if (!result) result = insert_statement(builder, level + 1);
    if (!result) result = update_statement(builder, level + 1);
    if (!result) result = select_statement(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // with_clause_table_def AS with_clause_table_def_subquery
  public static boolean with_clause_table(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, WITH_CLAUSE_TABLE, "<with clause table>");
    result = with_clause_table_def(builder, level + 1);
    result = result && consumeToken(builder, AS);
    result = result && with_clause_table_def_subquery(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // table_definition_name ( '(' column_definition_name ( ',' column_definition_name )* ')' )?
  public static boolean with_clause_table_def(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, WITH_CLAUSE_TABLE_DEF, "<with clause table def>");
    result = table_definition_name(builder, level + 1);
    result = result && with_clause_table_def_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( '(' column_definition_name ( ',' column_definition_name )* ')' )?
  private static boolean with_clause_table_def_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_1")) return false;
    with_clause_table_def_1_0(builder, level + 1);
    return true;
  }

  // '(' column_definition_name ( ',' column_definition_name )* ')'
  private static boolean with_clause_table_def_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && column_definition_name(builder, level + 1);
    result = result && with_clause_table_def_1_0_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' column_definition_name )*
  private static boolean with_clause_table_def_1_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_1_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!with_clause_table_def_1_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "with_clause_table_def_1_0_2", pos)) break;
    }
    return true;
  }

  // ',' column_definition_name
  private static boolean with_clause_table_def_1_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_1_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && column_definition_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // '(' &(SELECT|VALUES|WITH) subquery_greedy ')'
  static boolean with_clause_table_def_subquery(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_subquery")) return false;
    if (!nextTokenIs(builder, LPAREN)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = consumeToken(builder, LPAREN);
    result = result && with_clause_table_def_subquery_1(builder, level + 1);
    pinned = result; // pin = 2
    result = result && report_error_(builder, subquery_greedy(builder, level + 1));
    result = pinned && consumeToken(builder, RPAREN) && result;
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  // &(SELECT|VALUES|WITH)
  private static boolean with_clause_table_def_subquery_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_subquery_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _AND_);
    result = with_clause_table_def_subquery_1_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // SELECT|VALUES|WITH
  private static boolean with_clause_table_def_subquery_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "with_clause_table_def_subquery_1_0")) return false;
    boolean result;
    result = consumeToken(builder, SELECT);
    if (!result) result = consumeToken(builder, VALUES);
    if (!result) result = consumeToken(builder, WITH);
    return result;
  }

  /* ********************************************************** */
  // Expression root: expression
  // Operator priority table:
  // 0: ATOM(raise_function_expression)
  // 1: BINARY(or_expression)
  // 2: BINARY(and_expression)
  // 3: ATOM(case_expression)
  // 4: ATOM(exists_expression)
  // 5: POSTFIX(in_expression)
  // 6: POSTFIX(isnull_expression)
  // 7: BINARY(like_expression)
  // 8: PREFIX(cast_expression)
  // 9: ATOM(function_call_expression)
  // 10: BINARY(equivalence_expression) BINARY(between_expression)
  // 11: BINARY(comparison_expression)
  // 12: BINARY(bit_expression)
  // 13: BINARY(add_expression)
  // 14: BINARY(mul_expression)
  // 15: BINARY(concat_expression)
  // 16: PREFIX(unary_expression)
  // 17: POSTFIX(collate_expression)
  // 18: ATOM(literal_expression)
  // 19: ATOM(column_ref_expression)
  // 20: PREFIX(paren_expression)
  public static boolean expression(PsiBuilder builder, int level, int priority) {
    if (!recursion_guard_(builder, level, "expression")) return false;
    addVariant(builder, "<expression>");
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, "<expression>");
    result = raise_function_expression(builder, level + 1);
    if (!result) result = case_expression(builder, level + 1);
    if (!result) result = exists_expression(builder, level + 1);
    if (!result) result = cast_expression(builder, level + 1);
    if (!result) result = function_call_expression(builder, level + 1);
    if (!result) result = unary_expression(builder, level + 1);
    if (!result) result = literal_expression(builder, level + 1);
    if (!result) result = column_ref_expression(builder, level + 1);
    if (!result) result = paren_expression(builder, level + 1);
    pinned = result;
    result = result && expression_0(builder, level + 1, priority);
    exit_section_(builder, level, marker, null, result, pinned, null);
    return result || pinned;
  }

  public static boolean expression_0(PsiBuilder builder, int level, int priority) {
    if (!recursion_guard_(builder, level, "expression_0")) return false;
    boolean result = true;
    while (true) {
      Marker marker = enter_section_(builder, level, _LEFT_, null);
      if (priority < 1 && consumeTokenSmart(builder, OR)) {
        result = expression(builder, level, 1);
        exit_section_(builder, level, marker, OR_EXPRESSION, result, true, null);
      }
      else if (priority < 2 && consumeTokenSmart(builder, AND)) {
        result = expression(builder, level, 2);
        exit_section_(builder, level, marker, AND_EXPRESSION, result, true, null);
      }
      else if (priority < 5 && in_expression_0(builder, level + 1)) {
        result = true;
        exit_section_(builder, level, marker, IN_EXPRESSION, result, true, null);
      }
      else if (priority < 6 && isnull_expression_0(builder, level + 1)) {
        result = true;
        exit_section_(builder, level, marker, ISNULL_EXPRESSION, result, true, null);
      }
      else if (priority < 7 && like_expression_0(builder, level + 1)) {
        result = report_error_(builder, expression(builder, level, 7));
        result = like_expression_1(builder, level + 1) && result;
        exit_section_(builder, level, marker, LIKE_EXPRESSION, result, true, null);
      }
      else if (priority < 10 && equivalence_expression_0(builder, level + 1)) {
        result = expression(builder, level, 10);
        exit_section_(builder, level, marker, EQUIVALENCE_EXPRESSION, result, true, null);
      }
      else if (priority < 10 && between_expression_0(builder, level + 1)) {
        result = report_error_(builder, expression(builder, level, 10));
        result = between_expression_1(builder, level + 1) && result;
        exit_section_(builder, level, marker, BETWEEN_EXPRESSION, result, true, null);
      }
      else if (priority < 11 && comparison_expression_0(builder, level + 1)) {
        result = expression(builder, level, 11);
        exit_section_(builder, level, marker, COMPARISON_EXPRESSION, result, true, null);
      }
      else if (priority < 12 && bit_expression_0(builder, level + 1)) {
        result = expression(builder, level, 12);
        exit_section_(builder, level, marker, BIT_EXPRESSION, result, true, null);
      }
      else if (priority < 13 && add_expression_0(builder, level + 1)) {
        result = expression(builder, level, 13);
        exit_section_(builder, level, marker, ADD_EXPRESSION, result, true, null);
      }
      else if (priority < 14 && mul_expression_0(builder, level + 1)) {
        result = expression(builder, level, 14);
        exit_section_(builder, level, marker, MUL_EXPRESSION, result, true, null);
      }
      else if (priority < 15 && consumeTokenSmart(builder, CONCAT)) {
        result = expression(builder, level, 15);
        exit_section_(builder, level, marker, CONCAT_EXPRESSION, result, true, null);
      }
      else if (priority < 17 && collate_expression_0(builder, level + 1)) {
        result = true;
        exit_section_(builder, level, marker, COLLATE_EXPRESSION, result, true, null);
      }
      else {
        exit_section_(builder, level, marker, null, false, false, null);
        break;
      }
    }
    return result;
  }

  // RAISE '(' ( IGNORE | ( ROLLBACK | ABORT | FAIL ) ',' error_message ) ')'
  public static boolean raise_function_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "raise_function_expression")) return false;
    if (!nextTokenIsSmart(builder, RAISE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokensSmart(builder, 0, RAISE, LPAREN);
    result = result && raise_function_expression_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, RAISE_FUNCTION_EXPRESSION, result);
    return result;
  }

  // IGNORE | ( ROLLBACK | ABORT | FAIL ) ',' error_message
  private static boolean raise_function_expression_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "raise_function_expression_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, IGNORE);
    if (!result) result = raise_function_expression_2_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ROLLBACK | ABORT | FAIL ) ',' error_message
  private static boolean raise_function_expression_2_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "raise_function_expression_2_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = raise_function_expression_2_1_0(builder, level + 1);
    result = result && consumeToken(builder, COMMA);
    result = result && error_message(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ROLLBACK | ABORT | FAIL
  private static boolean raise_function_expression_2_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "raise_function_expression_2_1_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, ROLLBACK);
    if (!result) result = consumeTokenSmart(builder, ABORT);
    if (!result) result = consumeTokenSmart(builder, FAIL);
    return result;
  }

  // CASE expression? ( WHEN expression THEN expression )+ ( ELSE expression )? END
  public static boolean case_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression")) return false;
    if (!nextTokenIsSmart(builder, CASE)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, CASE);
    result = result && case_expression_1(builder, level + 1);
    result = result && case_expression_2(builder, level + 1);
    result = result && case_expression_3(builder, level + 1);
    result = result && consumeToken(builder, END);
    exit_section_(builder, marker, CASE_EXPRESSION, result);
    return result;
  }

  // expression?
  private static boolean case_expression_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression_1")) return false;
    expression(builder, level + 1, -1);
    return true;
  }

  // ( WHEN expression THEN expression )+
  private static boolean case_expression_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = case_expression_2_0(builder, level + 1);
    while (result) {
      int pos = current_position_(builder);
      if (!case_expression_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "case_expression_2", pos)) break;
    }
    exit_section_(builder, marker, null, result);
    return result;
  }

  // WHEN expression THEN expression
  private static boolean case_expression_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, WHEN);
    result = result && expression(builder, level + 1, -1);
    result = result && consumeToken(builder, THEN);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ELSE expression )?
  private static boolean case_expression_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression_3")) return false;
    case_expression_3_0(builder, level + 1);
    return true;
  }

  // ELSE expression
  private static boolean case_expression_3_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "case_expression_3_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, ELSE);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ( NOT )? EXISTS )? '(' expression_subquery ')'
  public static boolean exists_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "exists_expression")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, EXISTS_EXPRESSION, "<exists expression>");
    result = exists_expression_0(builder, level + 1);
    result = result && consumeToken(builder, LPAREN);
    result = result && expression_subquery(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( ( NOT )? EXISTS )?
  private static boolean exists_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "exists_expression_0")) return false;
    exists_expression_0_0(builder, level + 1);
    return true;
  }

  // ( NOT )? EXISTS
  private static boolean exists_expression_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "exists_expression_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = exists_expression_0_0_0(builder, level + 1);
    result = result && consumeToken(builder, EXISTS);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( NOT )?
  private static boolean exists_expression_0_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "exists_expression_0_0_0")) return false;
    consumeTokenSmart(builder, NOT);
    return true;
  }

  // ( NOT )? IN ( '(' ( expression_subquery | expression ( ',' expression )* )? ')' | ( database_name '.' )? defined_table_name )
  private static boolean in_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = in_expression_0_0(builder, level + 1);
    result = result && consumeToken(builder, IN);
    result = result && in_expression_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( NOT )?
  private static boolean in_expression_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_0")) return false;
    consumeTokenSmart(builder, NOT);
    return true;
  }

  // '(' ( expression_subquery | expression ( ',' expression )* )? ')' | ( database_name '.' )? defined_table_name
  private static boolean in_expression_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = in_expression_0_2_0(builder, level + 1);
    if (!result) result = in_expression_0_2_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '(' ( expression_subquery | expression ( ',' expression )* )? ')'
  private static boolean in_expression_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, LPAREN);
    result = result && in_expression_0_2_0_1(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( expression_subquery | expression ( ',' expression )* )?
  private static boolean in_expression_0_2_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0_1")) return false;
    in_expression_0_2_0_1_0(builder, level + 1);
    return true;
  }

  // expression_subquery | expression ( ',' expression )*
  private static boolean in_expression_0_2_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = expression_subquery(builder, level + 1);
    if (!result) result = in_expression_0_2_0_1_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // expression ( ',' expression )*
  private static boolean in_expression_0_2_0_1_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0_1_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = expression(builder, level + 1, -1);
    result = result && in_expression_0_2_0_1_0_1_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( ',' expression )*
  private static boolean in_expression_0_2_0_1_0_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0_1_0_1_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!in_expression_0_2_0_1_0_1_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "in_expression_0_2_0_1_0_1_1", pos)) break;
    }
    return true;
  }

  // ',' expression
  private static boolean in_expression_0_2_0_1_0_1_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_0_1_0_1_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, COMMA);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )? defined_table_name
  private static boolean in_expression_0_2_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = in_expression_0_2_1_0(builder, level + 1);
    result = result && defined_table_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( database_name '.' )?
  private static boolean in_expression_0_2_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_1_0")) return false;
    in_expression_0_2_1_0_0(builder, level + 1);
    return true;
  }

  // database_name '.'
  private static boolean in_expression_0_2_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "in_expression_0_2_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ISNULL | NOTNULL | NOT NULL
  private static boolean isnull_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "isnull_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, ISNULL);
    if (!result) result = consumeTokenSmart(builder, NOTNULL);
    if (!result) result = parseTokensSmart(builder, 0, NOT, NULL);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // NOT? ( LIKE | GLOB | REGEXP | MATCH )
  private static boolean like_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "like_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = like_expression_0_0(builder, level + 1);
    result = result && like_expression_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // NOT?
  private static boolean like_expression_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "like_expression_0_0")) return false;
    consumeTokenSmart(builder, NOT);
    return true;
  }

  // LIKE | GLOB | REGEXP | MATCH
  private static boolean like_expression_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "like_expression_0_1")) return false;
    boolean result;
    result = consumeTokenSmart(builder, LIKE);
    if (!result) result = consumeTokenSmart(builder, GLOB);
    if (!result) result = consumeTokenSmart(builder, REGEXP);
    if (!result) result = consumeTokenSmart(builder, MATCH);
    return result;
  }

  // ( ESCAPE expression )?
  private static boolean like_expression_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "like_expression_1")) return false;
    like_expression_1_0(builder, level + 1);
    return true;
  }

  // ESCAPE expression
  private static boolean like_expression_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "like_expression_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ESCAPE);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  public static boolean cast_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "cast_expression")) return false;
    if (!nextTokenIsSmart(builder, CAST)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, null);
    result = parseTokensSmart(builder, 0, CAST, LPAREN);
    pinned = result;
    result = pinned && expression(builder, level, 8);
    result = pinned && report_error_(builder, cast_expression_1(builder, level + 1)) && result;
    exit_section_(builder, level, marker, CAST_EXPRESSION, result, pinned, null);
    return result || pinned;
  }

  // AS type_name ')'
  private static boolean cast_expression_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "cast_expression_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, AS);
    result = result && type_name(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // function_name '(' ( ( DISTINCT )? expression ( ',' expression )* | '*' )? ')'
  public static boolean function_call_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FUNCTION_CALL_EXPRESSION, "<function call expression>");
    result = parseFunctionName(builder, level + 1);
    result = result && consumeToken(builder, LPAREN);
    result = result && function_call_expression_2(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ( ( DISTINCT )? expression ( ',' expression )* | '*' )?
  private static boolean function_call_expression_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2")) return false;
    function_call_expression_2_0(builder, level + 1);
    return true;
  }

  // ( DISTINCT )? expression ( ',' expression )* | '*'
  private static boolean function_call_expression_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = function_call_expression_2_0_0(builder, level + 1);
    if (!result) result = consumeTokenSmart(builder, STAR);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( DISTINCT )? expression ( ',' expression )*
  private static boolean function_call_expression_2_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = function_call_expression_2_0_0_0(builder, level + 1);
    result = result && expression(builder, level + 1, -1);
    result = result && function_call_expression_2_0_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ( DISTINCT )?
  private static boolean function_call_expression_2_0_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2_0_0_0")) return false;
    consumeTokenSmart(builder, DISTINCT);
    return true;
  }

  // ( ',' expression )*
  private static boolean function_call_expression_2_0_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2_0_0_2")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!function_call_expression_2_0_0_2_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "function_call_expression_2_0_0_2", pos)) break;
    }
    return true;
  }

  // ',' expression
  private static boolean function_call_expression_2_0_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_call_expression_2_0_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, COMMA);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '==' | '=' | '!=' | '<>' | IS NOT?
  private static boolean equivalence_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "equivalence_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, EQEQ);
    if (!result) result = consumeTokenSmart(builder, EQ);
    if (!result) result = consumeTokenSmart(builder, NOT_EQ);
    if (!result) result = consumeTokenSmart(builder, UNEQ);
    if (!result) result = equivalence_expression_0_4(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // IS NOT?
  private static boolean equivalence_expression_0_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "equivalence_expression_0_4")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, IS);
    result = result && equivalence_expression_0_4_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // NOT?
  private static boolean equivalence_expression_0_4_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "equivalence_expression_0_4_1")) return false;
    consumeTokenSmart(builder, NOT);
    return true;
  }

  // NOT? BETWEEN
  private static boolean between_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "between_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = between_expression_0_0(builder, level + 1);
    result = result && consumeToken(builder, BETWEEN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // NOT?
  private static boolean between_expression_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "between_expression_0_0")) return false;
    consumeTokenSmart(builder, NOT);
    return true;
  }

  // AND expression
  private static boolean between_expression_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "between_expression_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, AND);
    result = result && expression(builder, level + 1, -1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // '<' | '<=' | '>' | '>='
  private static boolean comparison_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "comparison_expression_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, LT);
    if (!result) result = consumeTokenSmart(builder, LTE);
    if (!result) result = consumeTokenSmart(builder, GT);
    if (!result) result = consumeTokenSmart(builder, GTE);
    return result;
  }

  // '<<' | '>>' | '&' | '|'
  private static boolean bit_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "bit_expression_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, SHL);
    if (!result) result = consumeTokenSmart(builder, SHR);
    if (!result) result = consumeTokenSmart(builder, AMP);
    if (!result) result = consumeTokenSmart(builder, BAR);
    return result;
  }

  // '+' | '-'
  private static boolean add_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "add_expression_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, PLUS);
    if (!result) result = consumeTokenSmart(builder, MINUS);
    return result;
  }

  // '*' | '/' | '%'
  private static boolean mul_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "mul_expression_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, STAR);
    if (!result) result = consumeTokenSmart(builder, DIV);
    if (!result) result = consumeTokenSmart(builder, MOD);
    return result;
  }

  public static boolean unary_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "unary_expression")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, null);
    result = unary_expression_0(builder, level + 1);
    pinned = result;
    result = pinned && expression(builder, level, 16);
    exit_section_(builder, level, marker, UNARY_EXPRESSION, result, pinned, null);
    return result || pinned;
  }

  // '-' | '+' | '~' | NOT
  private static boolean unary_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "unary_expression_0")) return false;
    boolean result;
    result = consumeTokenSmart(builder, MINUS);
    if (!result) result = consumeTokenSmart(builder, PLUS);
    if (!result) result = consumeTokenSmart(builder, TILDE);
    if (!result) result = consumeTokenSmart(builder, NOT);
    return result;
  }

  // COLLATE collation_name
  private static boolean collate_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "collate_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokenSmart(builder, COLLATE);
    result = result && collation_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // literal_value | bind_parameter
  public static boolean literal_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "literal_expression")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, LITERAL_EXPRESSION, "<literal expression>");
    result = literal_value(builder, level + 1);
    if (!result) result = bind_parameter(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // database_name '.' selected_table_name '.' column_name
  //   | selected_table_name '.' column_name
  //   | column_name
  public static boolean column_ref_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_ref_expression")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, COLUMN_REF_EXPRESSION, "<column ref expression>");
    result = column_ref_expression_0(builder, level + 1);
    if (!result) result = column_ref_expression_1(builder, level + 1);
    if (!result) result = column_name(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // database_name '.' selected_table_name '.' column_name
  private static boolean column_ref_expression_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_ref_expression_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = database_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    result = result && selected_table_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // selected_table_name '.' column_name
  private static boolean column_ref_expression_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "column_ref_expression_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = selected_table_name(builder, level + 1);
    result = result && consumeToken(builder, DOT);
    result = result && column_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  public static boolean paren_expression(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "paren_expression")) return false;
    if (!nextTokenIsSmart(builder, LPAREN)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, null);
    result = consumeTokenSmart(builder, LPAREN);
    pinned = result;
    result = pinned && expression(builder, level, -1);
    result = pinned && report_error_(builder, consumeToken(builder, RPAREN)) && result;
    exit_section_(builder, level, marker, PAREN_EXPRESSION, result, pinned, null);
    return result || pinned;
  }

}
