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
package com.android.tools.idea.lang.roomSql.parser

import com.android.tools.idea.lang.roomSql.ROOM_SQL_FILE_TYPE
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

class RoomSqlParserTest : ParsingTestCase("no_data_path_needed", ROOM_SQL_FILE_TYPE.defaultExtension, RoomSqlParserDefinition()) {

  /**
   * Checks that the given text parses correctly.
   *
   * For now the PSI hierarchy is not finalized, so there's no point checking the tree shape.
   */
  private fun check(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = RoomSqlLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER, { "BAD_CHARACTER ${lexer.tokenText}"})
      lexer.advance()
    }
  }

  private fun toParseTreeText(input: String): String {
    val psiFile = createPsiFile("in-memory", input)
    ensureParsed(psiFile)
    return toParseTreeText(psiFile, true, false).trim()
  }

  private fun getErrorMessage(input: String): String? {
    val psiFile = createPsiFile("in-memory", input)
    ensureParsed(psiFile)
    return getErrorMessage(psiFile)
  }

  private fun getErrorMessage(psiFile: PsiFile?) = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)?.errorDescription

  fun testSanity() {
    try {
      check("foo")
      fail()
    }
    catch(e: AssertionError) {
      // Expected.
    }
  }

  /**
   * Makes sure the lexer is not case sensitive.
   *
   * This needs to be manually fixed with "%caseless" after regenerating the flex file.
   */
  fun testCaseInsensitiveKeywords() {
    check("select foo from bar")
    check("SELECT foo FROM bar")
  }

  fun testSelect() {
    check("select * from myTable")
    check("select *, myTable.*, foo, 'bar', 12 from myTable")
    check("select foo from bar, baz")
    check("select foo from bar join baz")
    check("select foo from bar natural cross join baz on x left outer join quux")
    check("select foo as f from bar left outer join baz on foo.x = baz.x")
    check("select foo from myTable where bar > 17")
    check("select foo from myTable group by bar")
    check("select foo from myTable group by bar having baz")
    check("select foo from bar union all select baz from goo")
    check("select foo from bar order by foo")
    check("select foo from bar limit 1")
    check("select foo from bar limit 1 offset :page")

    check("""
      select foo, 3.4e+2 from bar inner join otherTable group by name having expr
      union all select *, user.* from myTable, user limit 1 offset :page""")
  }

  fun testInsert() {
    check("insert into foo values (1, 'foo')")
    check("insert into foo(a,b) values (1, 'foo')")
    check("insert into foo default values")
    check("insert or replace into foo values (1, 'foo')")
  }

  fun testDelete() {
    check("delete from foo")
    check("delete from foo where id = :id")
  }

  fun testUpdate() {
    check("update foo set bar=42")
    check("update foo set bar = 42, baz = :value, quux=:anotherValue")
    check("update or fail foo set bar=42")
    check("update foo set bar=bar*2 where predicate(bar)")
  }

  fun testExpressions() {
    check("select 2+2")
    check("select 2+bar * 5")
    check("select 5 * -1")
    check("select 2 == :x OR f(:y)")
    check("select 10 + 5 between 1 and 7")
    check("select (:x + 1) * :multiplier")
    check("select case status when 1 then 'foo' when 2 then 'bar' else 'baz' end from user")
    check("select * from user where name is null")
    check("select * from user where name is not null")
    check("select * from user where name like 'Joe'")
    check("select * from user where name like :name")
    check("select cast(name as text) from foo")
    check("select * from t where c in ('foo', 'bar', 'baz')")
    check("select 0xff, 'Mike''s car', :foo")
  }

  fun testComments() {
    check("select 17 -- magic number!")
    check("delete /* for good! */ from foo")
  }

  fun testLiterals() {
    // TODO: The spec doesn't seem to be fully reflected in real sqlite, we need to verify all of that and other combinations.
    check("select 12 from t")
    check("select 12.3 from t")
    check("select 10e-1 from t")
    check("select 0xff from t")

    check("""select 'foo' """)
    check("""select 'foo''bar' """)
    check("""select "foo''bar" """)
    check("""select "foo'bar" """)
    check("""select "foo""bar" """)
    check("""select 'foo"bar' """)

    check("select X'111'") // Blob literal.

    check("select foo, t, t2, T3, :mójArgument from MyTable join ę2")
    check("select [table].[column] from [database].[column]")
    check("""CREATE TABLE "TABLE"("#!@""'☺\", "");""")
  }

  fun testErrorMessages() {
    assertEquals("<statement> expected, got 'foo'", getErrorMessage("foo"))
  }

  fun testParameters() {
    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NUMBERED_PARAMETER)('?')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = ?"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NUMBERED_PARAMETER)('?1')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = ?1"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NAMED_PARAMETER)(':userId')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = :userId"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NAMED_PARAMETER)('@userId')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = @userId"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NAMED_PARAMETER)('${'$'}userId')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = \$userId"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      PsiElement(*)('*')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
                  RoomWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('id')
                      PsiElement(=)('=')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NAMED_PARAMETER)(':userId')
          """.trimIndent(),
        toParseTreeText("SELECT * FROM user WHERE id = :userId"))
  }

  fun testPragmas() {
    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('auto_vacuum')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('FULL')
          """.trimIndent(),
        toParseTreeText("PRAGMA auto_vacuum=FULL"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                RoomSignedNumberImpl(SIGNED_NUMBER)
                  PsiElement(NUMERIC_LITERAL)('1')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=1"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(ON)('ON')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=ON"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(ON)('on')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=on"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('OFF')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=OFF"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('off')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=off"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('yes')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=yes"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('YES')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=YES"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(NO)('no')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=no"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(NO)('NO')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=NO"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('true')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=true"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('TRUE')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=TRUE"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('false')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=false"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_keys')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('FALSE')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_keys=FALSE"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('data_store_directory')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''foo'')
          """.trimIndent(),
        toParseTreeText("PRAGMA data_store_directory='foo'"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('encoding')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(DOUBLE_QUOTE_STRING_LITERAL)('"UTF-8"')
          """.trimIndent(),
        toParseTreeText("PRAGMA encoding=\"UTF-8\""))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('foreign_key_check')
              PsiElement(()('(')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                PsiElement(IDENTIFIER)('my_table')
              PsiElement())(')')
          """.trimIndent(),
        toParseTreeText("PRAGMA foreign_key_check(my_table)"))

    assertEquals("""
          FILE
            RoomPragmaStatementImpl(PRAGMA_STATEMENT)
              PsiElement(PRAGMA)('PRAGMA')
              RoomPragmaNameImpl(PRAGMA_NAME)
                PsiElement(IDENTIFIER)('optimize')
              PsiElement(=)('=')
              RoomPragmaValueImpl(PRAGMA_VALUE)
                RoomSignedNumberImpl(SIGNED_NUMBER)
                  PsiElement(NUMERIC_LITERAL)('0xfffe')
          """.trimIndent(),
        toParseTreeText("PRAGMA optimize=0xfffe"))
  }

  fun testLiteralsAndIdentifiers() {
    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('select')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''age'')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('from')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('user')
          """.trimIndent(),
        toParseTreeText("select 'age' from user"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('select')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('age')
                  RoomFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('from')
                    RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      RoomFromTableImpl(FROM_TABLE)
                        RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''user'')
          """.trimIndent(),
        toParseTreeText("select age from 'user'"))

    assertEquals("""
          FILE
            RoomUpdateStatementImpl(UPDATE_STATEMENT)
              PsiElement(UPDATE)('UPDATE')
              RoomSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
                RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                  PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''table'')
              PsiElement(SET)('SET')
              RoomColumnNameImpl(COLUMN_NAME)
                PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''order'')
              PsiElement(=)('=')
              RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                PsiElement(NULL)('NULL')
          """.trimIndent(),
        toParseTreeText("UPDATE 'table' SET 'order' = NULL"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('select')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(BACKTICK_LITERAL)('`age`')
          """.trimIndent(),
        toParseTreeText("select `age`"))

    assertEquals("""
          FILE
            RoomSelectStatementImpl(SELECT_STATEMENT)
              RoomSelectCoreImpl(SELECT_CORE)
                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('select')
                  RoomResultColumnsImpl(RESULT_COLUMNS)
                    RoomResultColumnImpl(RESULT_COLUMN)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        RoomBindParameterImpl(BIND_PARAMETER)
                          PsiElement(NAMED_PARAMETER)(':age')
          """.trimIndent(),
        toParseTreeText("select :age"))
  }
}
