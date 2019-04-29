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
import com.android.tools.idea.lang.roomSql.RoomPairedBraceMatcher
import com.android.tools.idea.lang.roomSql.RoomSqlLanguage
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.lang.LanguageBraceMatching
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

abstract class RoomSqlParserTest : ParsingTestCase("no_data_path_needed", ROOM_SQL_FILE_TYPE.defaultExtension, RoomSqlParserDefinition()) {
  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  override fun setUp() {
    super.setUp()
    // b/110189571: ParsingTestCase puts in place a new root area and registers just a few extension points in it. Our parser implementation
    // ends up using LanguageBraceMatching which is not registered by ParsingTestCase and so LanguageBraceMatching.myCache ends up empty
    // (because there was no registered extension point with the right name to get instances for) and the empty cache is not flushed at
    // the end of the test (because only registered extension points get notified that the root area got replaced back to the default one).
    // With the line below we register the right object for the duration of this test and also mak sure its cache gets cleared before
    // EditingTest runs.
    addExplicitExtension(LanguageBraceMatching.INSTANCE, RoomSqlLanguage.INSTANCE, RoomPairedBraceMatcher()
    )
  }

  /**
   * Checks that the given text parses correctly.
   *
   * For now the PSI hierarchy is not finalized, so there's no point checking the tree shape.
   */
  protected fun check(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = RoomSqlLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }

  protected fun toParseTreeText(input: String): String {
    val psiFile = createPsiFile("in-memory", input)
    return toParseTreeText(psiFile, true, false).trim()
  }

  private fun getErrorMessage(input: String): String? {
    val psiFile = createPsiFile("in-memory", input)
    return getErrorMessage(psiFile)
  }

  private fun getErrorMessage(psiFile: PsiFile?) = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)?.errorDescription

}

class MiscParserTest : RoomSqlParserTest() {
  fun testSanity() {
    try {
      check("foo")
      fail()
    }
    catch (e: AssertionError) {
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

    check(
      """
      select foo, 3.4e+2 from bar inner join otherTable group by name having expr
      union all select *, user.* from myTable, user limit 1 offset :page
      """
    )
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

  fun testUpdatePartial() {
    assertEquals(
      """
      FILE
        RoomUpdateStatementImpl(UPDATE_STATEMENT)
          PsiElement(UPDATE)('update')
          RoomSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('${CompletionUtil.DUMMY_IDENTIFIER_TRIMMED}')
          PsiErrorElement:'.', INDEXED, NOT or SET expected
            <empty list>
      """.trimIndent(),
      toParseTreeText("update ${CompletionUtil.DUMMY_IDENTIFIER}")
    )

    assertEquals(
      """
      FILE
        RoomUpdateStatementImpl(UPDATE_STATEMENT)
          PsiElement(UPDATE)('update')
          RoomSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('foo')
          PsiElement(SET)('set')
          RoomColumnNameImpl(COLUMN_NAME)
            PsiElement(IDENTIFIER)('${CompletionUtil.DUMMY_IDENTIFIER_TRIMMED}')
          PsiErrorElement:'=' expected
            <empty list>
      """.trimIndent(),
      toParseTreeText("update foo set ${CompletionUtil.DUMMY_IDENTIFIER}")
    )
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

  fun testParameters() {
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = ?")
    )
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = ?1")
    )
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = :userId")
    )
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = @userId")
    )
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = \$userId")
    )
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = :userId")
    )
  }

  fun testPragmas() {
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('auto_vacuum')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(FULL)('FULL')
      """.trimIndent(),
      toParseTreeText("PRAGMA auto_vacuum=FULL")
    )
    assertEquals(
      """
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
      toParseTreeText("PRAGMA foreign_keys=1")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(ON)('ON')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=ON")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(ON)('on')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=on")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('OFF')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=OFF")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('off')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=off")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('yes')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=yes")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('YES')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=YES")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(NO)('no')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=no")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(NO)('NO')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=NO")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('true')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=true")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('TRUE')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=TRUE")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('false')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=false")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('FALSE')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=FALSE")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('data_store_directory')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''foo'')
      """.trimIndent(),
      toParseTreeText("PRAGMA data_store_directory='foo'")
    )
    assertEquals(
      """
      FILE
        RoomPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          RoomPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('encoding')
          PsiElement(=)('=')
          RoomPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(DOUBLE_QUOTE_STRING_LITERAL)('"UTF-8"')
      """.trimIndent(),
      toParseTreeText("PRAGMA encoding=\"UTF-8\"")
    )
    assertEquals(
      """
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
      toParseTreeText("PRAGMA foreign_key_check(my_table)")
    )
    assertEquals(
      """
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
      toParseTreeText("PRAGMA optimize=0xfffe")
    )
  }

  fun testLiteralsAndIdentifiers() {
    assertEquals(
      """
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
      toParseTreeText("select 'age' from user")
    )
    assertEquals(
      """
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
      toParseTreeText("select age from 'user'")
    )
    assertEquals(
      """
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
      toParseTreeText("UPDATE 'table' SET 'order' = NULL")
    )
    assertEquals(
      """
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
      toParseTreeText("select `age`")
    )
    assertEquals(
      """
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
      toParseTreeText("select :age")
    )
  }

  fun testJoins() {
    assertEquals(
      """
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
                RoomJoinOperatorImpl(JOIN_OPERATOR)
                  PsiElement(comma)(',')
                RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  RoomFromTableImpl(FROM_TABLE)
                    RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('book')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user, book")
    )
  }

  fun testSubqueries() {
    assertEquals(
      """
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
                  RoomSelectSubqueryImpl(SELECT_SUBQUERY)
                    PsiElement(()('(')
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
                            RoomJoinOperatorImpl(JOIN_OPERATOR)
                              PsiElement(comma)(',')
                            RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                              RoomFromTableImpl(FROM_TABLE)
                                RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                  PsiElement(IDENTIFIER)('book')
                    PsiElement())(')')
              RoomWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    RoomBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':id')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM (SELECT * FROM user, book) WHERE id = :id")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
              RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                  PsiElement(IDENTIFIER)('minmax')
              PsiElement(AS)('AS')
              PsiElement(()('(')
              RoomSelectStatementImpl(SELECT_STATEMENT)
                RoomSelectCoreImpl(SELECT_CORE)
                  RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                    PsiElement(SELECT)('SELECT')
                    RoomResultColumnsImpl(RESULT_COLUMNS)
                      RoomResultColumnImpl(RESULT_COLUMN)
                        RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                          PsiElement(()('(')
                          RoomSelectStatementImpl(SELECT_STATEMENT)
                            RoomSelectCoreImpl(SELECT_CORE)
                              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                PsiElement(SELECT)('SELECT')
                                RoomResultColumnsImpl(RESULT_COLUMNS)
                                  RoomResultColumnImpl(RESULT_COLUMN)
                                    RoomFunctionCallExpressionImpl(FUNCTION_CALL_EXPRESSION)
                                      RoomFunctionNameImpl(FUNCTION_NAME)
                                        PsiElement(IDENTIFIER)('min')
                                      PsiElement(()('(')
                                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        RoomColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('a')
                                      PsiElement())(')')
                                    PsiElement(AS)('as')
                                    RoomColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                      PsiElement(IDENTIFIER)('min_a')
                                RoomFromClauseImpl(FROM_CLAUSE)
                                  PsiElement(FROM)('FROM')
                                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                    RoomFromTableImpl(FROM_TABLE)
                                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                        PsiElement(IDENTIFIER)('Aaa')
                          PsiElement())(')')
                      PsiElement(comma)(',')
                      RoomResultColumnImpl(RESULT_COLUMN)
                        RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                          PsiElement(()('(')
                          RoomSelectStatementImpl(SELECT_STATEMENT)
                            RoomSelectCoreImpl(SELECT_CORE)
                              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                PsiElement(SELECT)('SELECT')
                                RoomResultColumnsImpl(RESULT_COLUMNS)
                                  RoomResultColumnImpl(RESULT_COLUMN)
                                    RoomFunctionCallExpressionImpl(FUNCTION_CALL_EXPRESSION)
                                      RoomFunctionNameImpl(FUNCTION_NAME)
                                        PsiElement(IDENTIFIER)('max')
                                      PsiElement(()('(')
                                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        RoomColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('a')
                                      PsiElement())(')')
                                RoomFromClauseImpl(FROM_CLAUSE)
                                  PsiElement(FROM)('FROM')
                                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                    RoomFromTableImpl(FROM_TABLE)
                                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                        PsiElement(IDENTIFIER)('Aaa')
                          PsiElement())(')')
                        PsiElement(AS)('as')
                        RoomColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                          PsiElement(IDENTIFIER)('max_a')
              PsiElement())(')')
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
                        PsiElement(IDENTIFIER)('Aaa')
                RoomWhereClauseImpl(WHERE_CLAUSE)
                  PsiElement(WHERE)('WHERE')
                  RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('a')
                    PsiElement(=)('=')
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomSelectStatementImpl(SELECT_STATEMENT)
                        RoomSelectCoreImpl(SELECT_CORE)
                          RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                            PsiElement(SELECT)('SELECT')
                            RoomResultColumnsImpl(RESULT_COLUMNS)
                              RoomResultColumnImpl(RESULT_COLUMN)
                                RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                  RoomColumnNameImpl(COLUMN_NAME)
                                    PsiElement(IDENTIFIER)('foo')
                            RoomFromClauseImpl(FROM_CLAUSE)
                              PsiElement(FROM)('FROM')
                              RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                RoomFromTableImpl(FROM_TABLE)
                                  RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                    PsiElement(IDENTIFIER)('minmax')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText(
        "WITH minmax AS (SELECT (SELECT min(a) as min_a FROM Aaa), (SELECT max(a) FROM Aaa) as max_a) SELECT * FROM Aaa WHERE a=(SELECT foo FROM minmax)")
    )
  }
}

class ErrorMessagesTest : RoomSqlParserTest() {
  fun testNonsense() {
    assertEquals(
      """
      FILE
        PsiErrorElement:<statement> expected, got 'foo'
          PsiElement(IDENTIFIER)('foo')
      """.trimIndent(),
      toParseTreeText("foo")
    )
  }

  fun testQuestionMarkWildcard() {
    assertEquals(
      """
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
      toParseTreeText("SELECT * FROM user WHERE id = ?")
    )
  }

  fun testJustSelect() {
    assertEquals(
      """
      FILE
        PsiElement(SELECT)('SELECT')
        PsiErrorElement:<result column>, ALL or DISTINCT expected
          <empty list>
      """.trimIndent(),
      toParseTreeText("SELECT ")
    )
  }

  fun testMissingFrom() {
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('foo')
        PsiElement(FROM)('FROM')
        PsiErrorElement:<table or subquery> expected
          <empty list>
      """.trimIndent(),
      toParseTreeText("SELECT foo FROM ")
    )
  }

  fun testInvalidWith() {
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiErrorElement:<table definition name> or RECURSIVE expected, got 'SELECT'
              <empty list>
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiErrorElement:'(' or AS expected, got 'SELECT'
              <empty list>
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'SELECT'
              <empty list>
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'foo'
              PsiElement(IDENTIFIER)('foo')
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS foo SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'foo'
              PsiElement(IDENTIFIER)('foo')
            PsiElement(WHERE)('WHERE')
            PsiElement(IDENTIFIER)('makes')
            PsiElement(NO)('no')
            PsiElement(IDENTIFIER)('sense')
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS foo WHERE makes no sense SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
              RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                  PsiElement(IDENTIFIER)('ids')
              PsiElement(AS)('AS')
              PsiElement(()('(')
              RoomSelectStatementImpl(SELECT_STATEMENT)
                RoomSelectCoreImpl(SELECT_CORE)
                  RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                    PsiElement(SELECT)('SELECT')
                    RoomResultColumnsImpl(RESULT_COLUMNS)
                      RoomResultColumnImpl(RESULT_COLUMN)
                        RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                          RoomColumnNameImpl(COLUMN_NAME)
                            PsiElement(IDENTIFIER)('something')
                        RoomColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                          PsiElement(IDENTIFIER)('stupid')
                    RoomWhereClauseImpl(WHERE_CLAUSE)
                      PsiElement(WHERE)('WHERE')
                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        RoomColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('doesnt')
              PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT or ORDER expected, got 'parse'
                PsiElement(IDENTIFIER)('parse')
              PsiElement())(')')
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      RoomColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                RoomFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    RoomFromTableImpl(FROM_TABLE)
                      RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS (SELECT something stupid WHERE doesnt parse) SELECT foo FROM bar")
    )
  }

  fun testSubqueries() {
    assertEquals(
      """
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
                    RoomTableAliasNameImpl(TABLE_ALIAS_NAME)
                      PsiElement(IDENTIFIER)('u')
                RoomJoinOperatorImpl(JOIN_OPERATOR)
                  PsiElement(JOIN)('JOIN')
                RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  RoomSelectSubqueryImpl(SELECT_SUBQUERY)
                    PsiElement(()('(')
                    RoomSelectStatementImpl(SELECT_STATEMENT)
                      RoomSelectCoreImpl(SELECT_CORE)
                        RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                          PsiElement(SELECT)('SELECT')
                          RoomResultColumnsImpl(RESULT_COLUMNS)
                            RoomResultColumnImpl(RESULT_COLUMN)
                              RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                RoomColumnNameImpl(COLUMN_NAME)
                                  PsiElement(IDENTIFIER)('something')
                              RoomColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                PsiElement(IDENTIFIER)('stupid')
                          RoomWhereClauseImpl(WHERE_CLAUSE)
                            PsiElement(WHERE)('WHERE')
                            RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                              RoomColumnNameImpl(COLUMN_NAME)
                                PsiElement(IDENTIFIER)('doesnt')
                    PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT or ORDER expected, got 'parse'
                      PsiElement(IDENTIFIER)('parse')
                    PsiElement())(')')
                    RoomTableAliasNameImpl(TABLE_ALIAS_NAME)
                      PsiElement(IDENTIFIER)('x')
              RoomWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomSelectedTableNameImpl(SELECTED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('u')
                    PsiElement(.)('.')
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('name')
                  PsiElement(IS)('IS')
                  PsiElement(NOT)('NOT')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NULL)('NULL')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user u JOIN (SELECT something stupid WHERE doesnt parse) x WHERE u.name IS NOT NULL")
    )
  }

  fun testNestedWith() {
    assertEquals(
      """
      FILE
        RoomWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          RoomWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
              RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                  PsiElement(IDENTIFIER)('x')
              PsiElement(AS)('AS')
              PsiElement(()('(')
              PsiElement(WITH)('WITH')
              PsiElement(IDENTIFIER)('y')
              PsiElement(IDENTIFIER)('doesn')
              PsiElement(IDENTIFIER)('parse')
              PsiErrorElement:<select statement> expected, got ')'
                <empty list>
              PsiElement())(')')
          RoomSelectStatementImpl(SELECT_STATEMENT)
            RoomSelectCoreImpl(SELECT_CORE)
              RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                RoomResultColumnsImpl(RESULT_COLUMNS)
                  RoomResultColumnImpl(RESULT_COLUMN)
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('32')
      """.trimIndent(),
      toParseTreeText("WITH x AS (WITH y doesn parse) SELECT 32")
    )
  }

  fun testSubqueriesInExpressions() {
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomConcatExpressionImpl(CONCAT_EXPRESSION)
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        RoomWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('x')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            RoomSelectStatementImpl(SELECT_STATEMENT)
                              RoomSelectCoreImpl(SELECT_CORE)
                                RoomSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('17')
                                  PsiElement())(')')
                            PsiElement())(')')
                        RoomSelectStatementImpl(SELECT_STATEMENT)
                          RoomSelectCoreImpl(SELECT_CORE)
                            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              RoomResultColumnsImpl(RESULT_COLUMNS)
                                RoomResultColumnImpl(RESULT_COLUMN)
                                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    RoomColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('x')
                      PsiElement())(')')
                    PsiElement(||)('||')
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        RoomWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            RoomSelectStatementImpl(SELECT_STATEMENT)
                              RoomSelectCoreImpl(SELECT_CORE)
                                RoomSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('42')
                                  PsiElement())(')')
                            PsiElement())(')')
                        RoomSelectStatementImpl(SELECT_STATEMENT)
                          RoomSelectCoreImpl(SELECT_CORE)
                            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              RoomResultColumnsImpl(RESULT_COLUMNS)
                                RoomResultColumnImpl(RESULT_COLUMN)
                                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    RoomColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('y')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT (WITH x AS (VALUES(17)) SELECT x) || (WITH y AS (VALUES(42)) SELECT y)")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomConcatExpressionImpl(CONCAT_EXPRESSION)
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      PsiElement(WITH)('WITH')
                      PsiElement(IDENTIFIER)('x')
                      PsiElement(AS)('AS')
                      PsiErrorElement:<select statement> expected, got ')'
                        <empty list>
                      PsiElement())(')')
                    PsiElement(||)('||')
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        RoomWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            RoomSelectStatementImpl(SELECT_STATEMENT)
                              RoomSelectCoreImpl(SELECT_CORE)
                                RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                  PsiElement(SELECT)('SELECT')
                                  RoomResultColumnsImpl(RESULT_COLUMNS)
                                    RoomResultColumnImpl(RESULT_COLUMN)
                                      RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        RoomColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('does')
                                      RoomColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                        PsiElement(IDENTIFIER)('parse')
                            PsiErrorElement:<compound operator>, FROM, GROUP, LIMIT, ORDER, WHERE or comma expected, got 'at'
                              PsiElement(IDENTIFIER)('at')
                            PsiElement(ALL)('all')
                            PsiElement())(')')
                        RoomSelectStatementImpl(SELECT_STATEMENT)
                          RoomSelectCoreImpl(SELECT_CORE)
                            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              RoomResultColumnsImpl(RESULT_COLUMNS)
                                RoomResultColumnImpl(RESULT_COLUMN)
                                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    RoomColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('y')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT (WITH x AS ) || (WITH y AS (SELECT does parse at all) SELECT y)")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomAddExpressionImpl(ADD_EXPRESSION)
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        RoomWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('x')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            RoomSelectStatementImpl(SELECT_STATEMENT)
                              RoomSelectCoreImpl(SELECT_CORE)
                                RoomSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('17')
                                  PsiElement())(')')
                            PsiElement())(')')
                        RoomSelectStatementImpl(SELECT_STATEMENT)
                          RoomSelectCoreImpl(SELECT_CORE)
                            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              RoomResultColumnsImpl(RESULT_COLUMNS)
                                RoomResultColumnImpl(RESULT_COLUMN)
                                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    RoomColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('x')
                      PsiElement())(')')
                    PsiElement(+)('+')
                    RoomExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      RoomWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        RoomWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          RoomWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            RoomWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            RoomSelectStatementImpl(SELECT_STATEMENT)
                              RoomSelectCoreImpl(SELECT_CORE)
                                RoomSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('42')
                                  PsiElement())(')')
                            PsiElement())(')')
                        RoomSelectStatementImpl(SELECT_STATEMENT)
                          RoomSelectCoreImpl(SELECT_CORE)
                            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              RoomResultColumnsImpl(RESULT_COLUMNS)
                                RoomResultColumnImpl(RESULT_COLUMN)
                                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    RoomColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('y')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT (WITH x AS (VALUES(17)) SELECT x) + (WITH y AS (VALUES(42)) SELECT y)")
    )
  }

  fun testJustDelete() {
    assertEquals(
      """
      FILE
        PsiElement(DELETE)('DELETE')
        PsiErrorElement:FROM expected
          <empty list>
      """.trimIndent(),
      toParseTreeText("DELETE ")
    )
  }

  fun testInvalidDelete() {
    assertEquals(
      """
      FILE
        PsiElement(DELETE)('DELETE')
        PsiElement(FROM)('FROM')
        PsiErrorElement:<single table statement table> expected
          <empty list>
      """.trimIndent(),
      toParseTreeText("DELETE FROM")
    )
  }

  fun testForeignKeyTriggers() {
    check(
      """
      CREATE TABLE IF NOT EXISTS foo
          (`id` INTEGER NOT NULL, `name` TEXT COLLATE NOCASE, PRIMARY KEY(`id`),
      FOREIGN KEY(`name`) REFERENCES `Entity1`(`name`)
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED)
      """
    )

    check(
      """
      CREATE TABLE IF NOT EXISTS foo
          (`id` INTEGER NOT NULL, `name` TEXT COLLATE NOCASE, PRIMARY KEY(`id`),
      FOREIGN KEY(`name`) REFERENCES `Entity1`(`name`)
      ON DELETE NO ACTION)
      """
    )

    check(
      """
      CREATE TABLE IF NOT EXISTS foo
          (`id` INTEGER NOT NULL, `name` TEXT COLLATE NOCASE, PRIMARY KEY(`id`),
      FOREIGN KEY(`name`) REFERENCES `Entity1`(`name`)
      DEFERRABLE INITIALLY DEFERRED)
      """
    )
  }

  fun testInExpressions() {
    assertEquals(
      """
      FILE
        RoomDeleteStatementImpl(DELETE_STATEMENT)
          PsiElement(DELETE)('DELETE')
          PsiElement(FROM)('FROM')
          RoomSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('t')
          RoomWhereClauseImpl(WHERE_CLAUSE)
            PsiElement(WHERE)('WHERE')
            RoomAndExpressionImpl(AND_EXPRESSION)
              RoomParenExpressionImpl(PAREN_EXPRESSION)
                PsiElement(()('(')
                RoomInExpressionImpl(IN_EXPRESSION)
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('1')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    RoomBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':ids')
                  PsiElement())(')')
                PsiElement())(')')
              PsiElement(AND)('AND')
              RoomParenExpressionImpl(PAREN_EXPRESSION)
                PsiElement(()('(')
                RoomInExpressionImpl(IN_EXPRESSION)
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('2')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
                  PsiElement(comma)(',')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('4')
                  PsiElement())(')')
                PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("DELETE FROM t WHERE (1 IN (:ids)) AND (2 IN (3,4))")
    )
  }

  fun testPriorities() {
    assertEquals(
      """
      FILE
        RoomDeleteStatementImpl(DELETE_STATEMENT)
          PsiElement(DELETE)('DELETE')
          PsiElement(FROM)('FROM')
          RoomSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('t')
          RoomWhereClauseImpl(WHERE_CLAUSE)
            PsiElement(WHERE)('WHERE')
            RoomOrExpressionImpl(OR_EXPRESSION)
              RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                  RoomColumnNameImpl(COLUMN_NAME)
                    PsiElement(IDENTIFIER)('a')
                PsiElement(=)('=')
                RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                  PsiElement(NUMERIC_LITERAL)('1')
              PsiElement(OR)('OR')
              RoomAndExpressionImpl(AND_EXPRESSION)
                RoomInExpressionImpl(IN_EXPRESSION)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('b')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('2')
                  PsiElement(comma)(',')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
                  PsiElement())(')')
                PsiElement(AND)('AND')
                RoomLikeExpressionImpl(LIKE_EXPRESSION)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('c')
                  PsiElement(LIKE)('LIKE')
                  RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
      """.trimIndent(),
      toParseTreeText("DELETE FROM t WHERE a = 1 OR b IN (2,3) AND c LIKE 3")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomLikeExpressionImpl(LIKE_EXPRESSION)
                    RoomAddExpressionImpl(ADD_EXPRESSION)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('10')
                      PsiElement(+)('+')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('10')
                    PsiElement(LIKE)('LIKE')
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('10')
      """.trimIndent(),
      toParseTreeText("SELECT 10 + 10 LIKE 10")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomAndExpressionImpl(AND_EXPRESSION)
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
                    PsiElement(AND)('AND')
                    RoomLikeExpressionImpl(LIKE_EXPRESSION)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                      PsiElement(LIKE)('LIKE')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
      """.trimIndent(),
      toParseTreeText("SELECT 1 AND 2 LIKE 2")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                    RoomEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                      PsiElement(==)('==')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                    PsiElement(==)('==')
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
      """.trimIndent(),
      toParseTreeText("SELECT 2 == 2 == 1")
    )
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomCaseExpressionImpl(CASE_EXPRESSION)
                    PsiElement(CASE)('CASE')
                    RoomAndExpressionImpl(AND_EXPRESSION)
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('1')
                      PsiElement(AND)('AND')
                      RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('0')
                    PsiElement(WHEN)('WHEN')
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
                    PsiElement(THEN)('THEN')
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''true'')
                    PsiElement(ELSE)('ELSE')
                    RoomLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''false'')
                    PsiElement(END)('END')
      """.trimIndent(),
      toParseTreeText("SELECT CASE 1 AND 0 WHEN 1 THEN 'true' ELSE 'false' END")
    )
  }

  fun testRowId() {
    assertEquals(
      """
      FILE
        RoomSelectStatementImpl(SELECT_STATEMENT)
          RoomSelectCoreImpl(SELECT_CORE)
            RoomSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              RoomResultColumnsImpl(RESULT_COLUMNS)
                RoomResultColumnImpl(RESULT_COLUMN)
                  RoomColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    RoomColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('rowId')
                PsiElement(comma)(',')
                RoomResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              RoomFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                RoomTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  RoomFromTableImpl(FROM_TABLE)
                    RoomDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('mail')
      """.trimIndent(),
      toParseTreeText("SELECT rowId, * FROM mail")
    )

    assertEquals(
      """
      FILE
        RoomCreateTableStatementImpl(CREATE_TABLE_STATEMENT)
          PsiElement(CREATE)('CREATE')
          PsiElement(TABLE)('TABLE')
          PsiElement(IF)('IF')
          PsiElement(NOT)('NOT')
          PsiElement(EXISTS)('EXISTS')
          RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
            PsiElement(IDENTIFIER)('wordcount')
          PsiElement(()('(')
          RoomColumnDefinitionImpl(COLUMN_DEFINITION)
            RoomColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('word')
            RoomTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('TEXT')
            RoomColumnConstraintImpl(COLUMN_CONSTRAINT)
              PsiElement(PRIMARY)('PRIMARY')
              PsiElement(KEY)('KEY')
              RoomConflictClauseImpl(CONFLICT_CLAUSE)
                <empty list>
          PsiElement(comma)(',')
          RoomColumnDefinitionImpl(COLUMN_DEFINITION)
            RoomColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('cnt')
            RoomTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('INTEGER')
          PsiElement())(')')
          PsiElement(WITHOUT)('WITHOUT')
          PsiElement(IDENTIFIER)('ROWID')
      """.trimIndent(),
      toParseTreeText("CREATE TABLE IF NOT EXISTS wordcount(word TEXT PRIMARY KEY, cnt INTEGER) WITHOUT ROWID")
    )

    assertEquals(
      """
      FILE
        RoomCreateTableStatementImpl(CREATE_TABLE_STATEMENT)
          PsiElement(CREATE)('CREATE')
          PsiElement(TABLE)('TABLE')
          PsiElement(IF)('IF')
          PsiElement(NOT)('NOT')
          PsiElement(EXISTS)('EXISTS')
          RoomTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
            PsiElement(IDENTIFIER)('wordcount')
          PsiElement(()('(')
          RoomColumnDefinitionImpl(COLUMN_DEFINITION)
            RoomColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('word')
            RoomTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('TEXT')
            RoomColumnConstraintImpl(COLUMN_CONSTRAINT)
              PsiElement(PRIMARY)('PRIMARY')
              PsiElement(KEY)('KEY')
              RoomConflictClauseImpl(CONFLICT_CLAUSE)
                <empty list>
          PsiElement(comma)(',')
          RoomColumnDefinitionImpl(COLUMN_DEFINITION)
            RoomColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('cnt')
            RoomTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('INTEGER')
          PsiElement())(')')
        PsiElement(WITHOUT)('WITHOUT')
        PsiErrorElement:ROWID expected, got 'MADEUP'
          PsiElement(IDENTIFIER)('MADEUP')
      """.trimIndent(),
      toParseTreeText("CREATE TABLE IF NOT EXISTS wordcount(word TEXT PRIMARY KEY, cnt INTEGER) WITHOUT MADEUP")
    )
  }
}
