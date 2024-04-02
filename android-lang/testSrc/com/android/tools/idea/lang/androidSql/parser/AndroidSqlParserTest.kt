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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.AndroidParsingTestCase
import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.lang.androidSql.AndroidSqlPairedBraceMatcher
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.lang.LanguageBraceMatching
import com.intellij.psi.TokenType

abstract class AndroidSqlParserTest : AndroidParsingTestCase(AndroidSqlFileType.INSTANCE.defaultExtension, AndroidSqlParserDefinition()) {
  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  override fun setUp() {
    super.setUp()
    // b/110189571: ParsingTestCase puts in place a new root area and registers just a few extension points in it. Our parser implementation
    // ends up using LanguageBraceMatching which is not registered by ParsingTestCase and so LanguageBraceMatching.myCache ends up empty
    // (because there was no registered extension point with the right name to get instances for) and the empty cache is not flushed at
    // the end of the test (because only registered extension points get notified that the root area got replaced back to the default one).
    // With the line below we register the right object for the duration of this test and also mak sure its cache gets cleared before
    // EditingTest runs.
    addExplicitExtension(LanguageBraceMatching.INSTANCE, AndroidSqlLanguage.INSTANCE, AndroidSqlPairedBraceMatcher()
    )
  }

  /**
   * Checks that the given text parses correctly.
   *
   * For now the PSI hierarchy is not finalized, so there's no point checking the tree shape.
   */
  protected fun check(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = AndroidSqlLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }
}

class MiscParserTest : AndroidSqlParserTest() {
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
        AndroidSqlUpdateStatementImpl(UPDATE_STATEMENT)
          PsiElement(UPDATE)('update')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('${CompletionUtil.DUMMY_IDENTIFIER_TRIMMED}')
          PsiErrorElement:'.', INDEXED, NOT or SET expected
            <empty list>
      """.trimIndent(),
      toParseTreeText("update ${CompletionUtil.DUMMY_IDENTIFIER}")
    )

    assertEquals(
      """
      FILE
        AndroidSqlUpdateStatementImpl(UPDATE_STATEMENT)
          PsiElement(UPDATE)('update')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('foo')
          PsiElement(SET)('set')
          AndroidSqlColumnNameImpl(COLUMN_NAME)
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
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NUMBERED_PARAMETER)('?')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = ?")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NUMBERED_PARAMETER)('?1')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = ?1")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':userId')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = :userId")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)('@userId')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = @userId")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)('${'$'}userId')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = \$userId")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':userId')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user WHERE id = :userId")
    )
  }

  fun testPragmas() {
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('auto_vacuum')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(FULL)('FULL')
      """.trimIndent(),
      toParseTreeText("PRAGMA auto_vacuum=FULL")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlSignedNumberImpl(SIGNED_NUMBER)
              PsiElement(NUMERIC_LITERAL)('1')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=1")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(ON)('ON')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=ON")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(ON)('on')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=on")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('OFF')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=OFF")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('off')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=off")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('yes')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=yes")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('YES')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=YES")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(NO)('no')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=no")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(NO)('NO')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=NO")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
              PsiElement(TRUE)('true')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=true")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
              PsiElement(TRUE)('TRUE')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=TRUE")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
              PsiElement(FALSE)('false')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=false")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_keys')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
              PsiElement(FALSE)('FALSE')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_keys=FALSE")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('data_store_directory')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''foo'')
      """.trimIndent(),
      toParseTreeText("PRAGMA data_store_directory='foo'")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('encoding')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(DOUBLE_QUOTE_STRING_LITERAL)('"UTF-8"')
      """.trimIndent(),
      toParseTreeText("PRAGMA encoding=\"UTF-8\"")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('foreign_key_check')
          PsiElement(()('(')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            PsiElement(IDENTIFIER)('my_table')
          PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("PRAGMA foreign_key_check(my_table)")
    )
    assertEquals(
      """
      FILE
        AndroidSqlPragmaStatementImpl(PRAGMA_STATEMENT)
          PsiElement(PRAGMA)('PRAGMA')
          AndroidSqlPragmaNameImpl(PRAGMA_NAME)
            PsiElement(IDENTIFIER)('optimize')
          PsiElement(=)('=')
          AndroidSqlPragmaValueImpl(PRAGMA_VALUE)
            AndroidSqlSignedNumberImpl(SIGNED_NUMBER)
              PsiElement(NUMERIC_LITERAL)('0xfffe')
      """.trimIndent(),
      toParseTreeText("PRAGMA optimize=0xfffe")
    )
  }

  fun testLiteralsAndIdentifiers() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('select')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''age'')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('from')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
      """.trimIndent(),
      toParseTreeText("select 'age' from user")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('select')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('age')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('from')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''user'')
      """.trimIndent(),
      toParseTreeText("select age from 'user'")
    )
    assertEquals(
      """
      FILE
        AndroidSqlUpdateStatementImpl(UPDATE_STATEMENT)
          PsiElement(UPDATE)('UPDATE')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''table'')
          PsiElement(SET)('SET')
          AndroidSqlColumnNameImpl(COLUMN_NAME)
            PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''order'')
          PsiElement(=)('=')
          AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
            PsiElement(NULL)('NULL')
      """.trimIndent(),
      toParseTreeText("UPDATE 'table' SET 'order' = NULL")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('select')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(BACKTICK_LITERAL)('`age`')
      """.trimIndent(),
      toParseTreeText("select `age`")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('select')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':age')
      """.trimIndent(),
      toParseTreeText("select :age")
    )
  }

  fun testTokenReplacement() {
    assertEquals(
      """
      FILE
        AndroidSqlInsertStatementImpl(INSERT_STATEMENT)
          PsiElement(REPLACE)('REPLACE')
          PsiElement(INTO)('INTO')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('books')
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreValuesImpl(SELECT_CORE_VALUES)
                PsiElement(VALUES)('VALUES')
                PsiElement(()('(')
                AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                  PsiElement(NUMERIC_LITERAL)('1')
                PsiElement(comma)(',')
                AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                  PsiElement(NUMERIC_LITERAL)('2')
                PsiElement(comma)(',')
                AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                  PsiElement(NUMERIC_LITERAL)('3')
                PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("REPLACE INTO books VALUES(1,2,3)")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlFunctionCallExpressionImpl(FUNCTION_CALL_EXPRESSION)
                    PsiElement(IDENTIFIER)('REPLACE')
                    PsiElement(()('(')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''a'')
                    PsiElement(comma)(',')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''b'')
                    PsiElement(comma)(',')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''aa'')
                    PsiElement())(')')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('books')
      """.trimIndent(),
      toParseTreeText("SELECT REPLACE('a','b','aa') FROM books")
    )
  }

  fun testJoins() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
                AndroidSqlJoinOperatorImpl(JOIN_OPERATOR)
                  PsiElement(comma)(',')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('book')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user, book")
    )
  }

  fun testSubqueries() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlSelectSubqueryImpl(SELECT_SUBQUERY)
                    PsiElement(()('(')
                    AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                      AndroidSqlSelectCoreImpl(SELECT_CORE)
                        AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                          PsiElement(SELECT)('SELECT')
                          AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                            AndroidSqlResultColumnImpl(RESULT_COLUMN)
                              PsiElement(*)('*')
                          AndroidSqlFromClauseImpl(FROM_CLAUSE)
                            PsiElement(FROM)('FROM')
                            AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                              AndroidSqlFromTableImpl(FROM_TABLE)
                                AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                  PsiElement(IDENTIFIER)('user')
                            AndroidSqlJoinOperatorImpl(JOIN_OPERATOR)
                              PsiElement(comma)(',')
                            AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                              AndroidSqlFromTableImpl(FROM_TABLE)
                                AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                  PsiElement(IDENTIFIER)('book')
                    PsiElement())(')')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':id')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM (SELECT * FROM user, book) WHERE id = :id")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
              AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                  PsiElement(IDENTIFIER)('minmax')
              PsiElement(AS)('AS')
              PsiElement(()('(')
              AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                AndroidSqlSelectCoreImpl(SELECT_CORE)
                  AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                    PsiElement(SELECT)('SELECT')
                    AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                      AndroidSqlResultColumnImpl(RESULT_COLUMN)
                        AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                          PsiElement(()('(')
                          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                            AndroidSqlSelectCoreImpl(SELECT_CORE)
                              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                PsiElement(SELECT)('SELECT')
                                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                    AndroidSqlFunctionCallExpressionImpl(FUNCTION_CALL_EXPRESSION)
                                      PsiElement(IDENTIFIER)('min')
                                      PsiElement(()('(')
                                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('a')
                                      PsiElement())(')')
                                    PsiElement(AS)('as')
                                    AndroidSqlColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                      PsiElement(IDENTIFIER)('min_a')
                                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                                  PsiElement(FROM)('FROM')
                                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                    AndroidSqlFromTableImpl(FROM_TABLE)
                                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                        PsiElement(IDENTIFIER)('Aaa')
                          PsiElement())(')')
                      PsiElement(comma)(',')
                      AndroidSqlResultColumnImpl(RESULT_COLUMN)
                        AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                          PsiElement(()('(')
                          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                            AndroidSqlSelectCoreImpl(SELECT_CORE)
                              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                PsiElement(SELECT)('SELECT')
                                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                    AndroidSqlFunctionCallExpressionImpl(FUNCTION_CALL_EXPRESSION)
                                      PsiElement(IDENTIFIER)('max')
                                      PsiElement(()('(')
                                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('a')
                                      PsiElement())(')')
                                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                                  PsiElement(FROM)('FROM')
                                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                    AndroidSqlFromTableImpl(FROM_TABLE)
                                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                        PsiElement(IDENTIFIER)('Aaa')
                          PsiElement())(')')
                        PsiElement(AS)('as')
                        AndroidSqlColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                          PsiElement(IDENTIFIER)('max_a')
              PsiElement())(')')
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    PsiElement(*)('*')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('Aaa')
                AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                  PsiElement(WHERE)('WHERE')
                  AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('a')
                    PsiElement(=)('=')
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                        AndroidSqlSelectCoreImpl(SELECT_CORE)
                          AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                            PsiElement(SELECT)('SELECT')
                            AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                              AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                  AndroidSqlColumnNameImpl(COLUMN_NAME)
                                    PsiElement(IDENTIFIER)('foo')
                            AndroidSqlFromClauseImpl(FROM_CLAUSE)
                              PsiElement(FROM)('FROM')
                              AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                                AndroidSqlFromTableImpl(FROM_TABLE)
                                  AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                                    PsiElement(IDENTIFIER)('minmax')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText(
        "WITH minmax AS (SELECT (SELECT min(a) as min_a FROM Aaa), (SELECT max(a) FROM Aaa) as max_a) SELECT * FROM Aaa WHERE a=(SELECT foo FROM minmax)")
    )
  }

  fun testRenameTable() {
    assertEquals(
      """
      FILE
        AndroidSqlAlterTableStatementImpl(ALTER_TABLE_STATEMENT)
          PsiElement(ALTER)('ALTER')
          PsiElement(TABLE)('TABLE')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('myTable')
          PsiElement(RENAME)('RENAME')
          PsiElement(TO)('TO')
          AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
            PsiElement(IDENTIFIER)('myNewTable')
      """.trimIndent(),
      toParseTreeText("ALTER TABLE myTable RENAME TO myNewTable")
    )
  }

  fun testAddColumn() {
    assertEquals(
      """
      FILE
        AndroidSqlAlterTableStatementImpl(ALTER_TABLE_STATEMENT)
          PsiElement(ALTER)('ALTER')
          PsiElement(TABLE)('TABLE')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('employees')
          PsiElement(ADD)('ADD')
          AndroidSqlColumnDefinitionImpl(COLUMN_DEFINITION)
            AndroidSqlColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('status')
            AndroidSqlTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('VARCHAR')
      """.trimIndent(),
      toParseTreeText("ALTER TABLE employees ADD status VARCHAR")
    )
  }

  fun testColumnRenaming() {
    assertEquals(
      """
      FILE
        AndroidSqlAlterTableStatementImpl(ALTER_TABLE_STATEMENT)
          PsiElement(ALTER)('ALTER')
          PsiElement(TABLE)('TABLE')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('myTable')
          PsiElement(RENAME)('RENAME')
          PsiElement(COLUMN)('COLUMN')
          AndroidSqlColumnNameImpl(COLUMN_NAME)
            PsiElement(IDENTIFIER)('columnNameOld')
          PsiElement(TO)('TO')
          PsiElement(IDENTIFIER)('columnNameNew')
      """.trimIndent(),
      toParseTreeText("ALTER TABLE myTable RENAME COLUMN columnNameOld TO columnNameNew")
    )

    assertEquals(
      """
      FILE
        AndroidSqlAlterTableStatementImpl(ALTER_TABLE_STATEMENT)
          PsiElement(ALTER)('ALTER')
          PsiElement(TABLE)('TABLE')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('myTable')
          PsiElement(RENAME)('RENAME')
          AndroidSqlColumnNameImpl(COLUMN_NAME)
            PsiElement(IDENTIFIER)('columnNameOld')
          PsiElement(TO)('TO')
          PsiElement(IDENTIFIER)('columnNameNew')
      """.trimIndent(),
      toParseTreeText("ALTER TABLE myTable RENAME columnNameOld TO columnNameNew")
    )
  }

  // Regression test for b/243679694
  fun testRowValue() {
    check("SELECT abc, def FROM some_table WHERE (abc, def) NOT IN (SELECT abc, def FROM other_table)")
  }

  fun testBooleanLiterals() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('foo')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('bar')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
                      PsiElement(TRUE)('TRUE')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM foo WHERE bar = TRUE")
    )

    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('foo')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('bar')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBooleanLiteralImpl(BOOLEAN_LITERAL)
                      PsiElement(FALSE)('FALSE')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM foo WHERE bar = FALSE")
    )
  }

  fun testTempTable() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('name')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('from')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDatabaseNameImpl(DATABASE_NAME)
                      PsiElement(TEMP)('TEMP')
                    PsiElement(.)('.')
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('ConfigPackagesToKeep')
      """.trimIndent(),
      toParseTreeText("SELECT name from TEMP.ConfigPackagesToKeep")
    )
  }
}

class ErrorMessagesTest : AndroidSqlParserTest() {
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
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('id')
                  PsiElement(=)('=')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
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
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
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
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiErrorElement:<table definition name> or RECURSIVE expected, got 'SELECT'
              <empty list>
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiErrorElement:'(' or AS expected, got 'SELECT'
              <empty list>
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'SELECT'
              <empty list>
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'foo'
              PsiElement(IDENTIFIER)('foo')
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS foo SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            PsiElement(IDENTIFIER)('ids')
            PsiElement(AS)('AS')
            PsiErrorElement:'(' expected, got 'foo'
              PsiElement(IDENTIFIER)('foo')
            PsiElement(WHERE)('WHERE')
            PsiElement(IDENTIFIER)('makes')
            PsiElement(NO)('no')
            PsiElement(IDENTIFIER)('sense')
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS foo WHERE makes no sense SELECT foo FROM bar")
    )
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
              AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                  PsiElement(IDENTIFIER)('ids')
              PsiElement(AS)('AS')
              PsiElement(()('(')
              AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                AndroidSqlSelectCoreImpl(SELECT_CORE)
                  AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                    PsiElement(SELECT)('SELECT')
                    AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                      AndroidSqlResultColumnImpl(RESULT_COLUMN)
                        AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                          AndroidSqlColumnNameImpl(COLUMN_NAME)
                            PsiElement(IDENTIFIER)('something')
                        AndroidSqlColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                          PsiElement(IDENTIFIER)('stupid')
                    AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                      PsiElement(WHERE)('WHERE')
                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('doesnt')
              PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT or ORDER expected, got 'parse'
                PsiElement(IDENTIFIER)('parse')
              PsiElement())(')')
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('foo')
                AndroidSqlFromClauseImpl(FROM_CLAUSE)
                  PsiElement(FROM)('FROM')
                  AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                    AndroidSqlFromTableImpl(FROM_TABLE)
                      AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                        PsiElement(IDENTIFIER)('bar')
      """.trimIndent(),
      toParseTreeText("WITH ids AS (SELECT something stupid WHERE doesnt parse) SELECT foo FROM bar")
    )
  }

  fun testSubqueries() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('user')
                    AndroidSqlTableAliasNameImpl(TABLE_ALIAS_NAME)
                      PsiElement(IDENTIFIER)('u')
                AndroidSqlJoinOperatorImpl(JOIN_OPERATOR)
                  PsiElement(JOIN)('JOIN')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlSelectSubqueryImpl(SELECT_SUBQUERY)
                    PsiElement(()('(')
                    AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                      AndroidSqlSelectCoreImpl(SELECT_CORE)
                        AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                          PsiElement(SELECT)('SELECT')
                          AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                            AndroidSqlResultColumnImpl(RESULT_COLUMN)
                              AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                AndroidSqlColumnNameImpl(COLUMN_NAME)
                                  PsiElement(IDENTIFIER)('something')
                              AndroidSqlColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                PsiElement(IDENTIFIER)('stupid')
                          AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                            PsiElement(WHERE)('WHERE')
                            AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                              AndroidSqlColumnNameImpl(COLUMN_NAME)
                                PsiElement(IDENTIFIER)('doesnt')
                    PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT or ORDER expected, got 'parse'
                      PsiElement(IDENTIFIER)('parse')
                    PsiElement())(')')
                    AndroidSqlTableAliasNameImpl(TABLE_ALIAS_NAME)
                      PsiElement(IDENTIFIER)('x')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlSelectedTableNameImpl(SELECTED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('u')
                    PsiElement(.)('.')
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('name')
                  PsiElement(IS)('IS')
                  PsiElement(NOT)('NOT')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NULL)('NULL')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM user u JOIN (SELECT something stupid WHERE doesnt parse) x WHERE u.name IS NOT NULL")
    )
  }

  fun testNestedWith() {
    assertEquals(
      """
      FILE
        AndroidSqlWithClauseStatementImpl(WITH_CLAUSE_STATEMENT)
          AndroidSqlWithClauseImpl(WITH_CLAUSE)
            PsiElement(WITH)('WITH')
            AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
              AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
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
          AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
            AndroidSqlSelectCoreImpl(SELECT_CORE)
              AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                PsiElement(SELECT)('SELECT')
                AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                  AndroidSqlResultColumnImpl(RESULT_COLUMN)
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('32')
      """.trimIndent(),
      toParseTreeText("WITH x AS (WITH y doesn parse) SELECT 32")
    )
  }

  fun testSubqueriesInExpressions() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlConcatExpressionImpl(CONCAT_EXPRESSION)
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        AndroidSqlWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('x')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                              AndroidSqlSelectCoreImpl(SELECT_CORE)
                                AndroidSqlSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('17')
                                  PsiElement())(')')
                            PsiElement())(')')
                        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                          AndroidSqlSelectCoreImpl(SELECT_CORE)
                            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('x')
                      PsiElement())(')')
                    PsiElement(||)('||')
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        AndroidSqlWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                              AndroidSqlSelectCoreImpl(SELECT_CORE)
                                AndroidSqlSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('42')
                                  PsiElement())(')')
                            PsiElement())(')')
                        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                          AndroidSqlSelectCoreImpl(SELECT_CORE)
                            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('y')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT (WITH x AS (VALUES(17)) SELECT x) || (WITH y AS (VALUES(42)) SELECT y)")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlConcatExpressionImpl(CONCAT_EXPRESSION)
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      PsiElement(WITH)('WITH')
                      PsiElement(IDENTIFIER)('x')
                      PsiElement(AS)('AS')
                      PsiErrorElement:<select statement> expected, got ')'
                        <empty list>
                      PsiElement())(')')
                    PsiElement(||)('||')
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        AndroidSqlWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                              AndroidSqlSelectCoreImpl(SELECT_CORE)
                                AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                                  PsiElement(SELECT)('SELECT')
                                  AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                    AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                                          PsiElement(IDENTIFIER)('does')
                                      AndroidSqlColumnAliasNameImpl(COLUMN_ALIAS_NAME)
                                        PsiElement(IDENTIFIER)('parse')
                            PsiErrorElement:<compound operator>, FROM, GROUP, LIMIT, ORDER, WHERE or comma expected, got 'at'
                              PsiElement(IDENTIFIER)('at')
                            PsiElement(ALL)('all')
                            PsiElement())(')')
                        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                          AndroidSqlSelectCoreImpl(SELECT_CORE)
                            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('y')
                      PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT (WITH x AS ) || (WITH y AS (SELECT does parse at all) SELECT y)")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlAddExpressionImpl(ADD_EXPRESSION)
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        AndroidSqlWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('x')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                              AndroidSqlSelectCoreImpl(SELECT_CORE)
                                AndroidSqlSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('17')
                                  PsiElement())(')')
                            PsiElement())(')')
                        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                          AndroidSqlSelectCoreImpl(SELECT_CORE)
                            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                                      PsiElement(IDENTIFIER)('x')
                      PsiElement())(')')
                    PsiElement(+)('+')
                    AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
                      PsiElement(()('(')
                      AndroidSqlWithClauseSelectStatementImpl(WITH_CLAUSE_SELECT_STATEMENT)
                        AndroidSqlWithClauseImpl(WITH_CLAUSE)
                          PsiElement(WITH)('WITH')
                          AndroidSqlWithClauseTableImpl(WITH_CLAUSE_TABLE)
                            AndroidSqlWithClauseTableDefImpl(WITH_CLAUSE_TABLE_DEF)
                              AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
                                PsiElement(IDENTIFIER)('y')
                            PsiElement(AS)('AS')
                            PsiElement(()('(')
                            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                              AndroidSqlSelectCoreImpl(SELECT_CORE)
                                AndroidSqlSelectCoreValuesImpl(SELECT_CORE_VALUES)
                                  PsiElement(VALUES)('VALUES')
                                  PsiElement(()('(')
                                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                                    PsiElement(NUMERIC_LITERAL)('42')
                                  PsiElement())(')')
                            PsiElement())(')')
                        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
                          AndroidSqlSelectCoreImpl(SELECT_CORE)
                            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                              PsiElement(SELECT)('SELECT')
                              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                                    AndroidSqlColumnNameImpl(COLUMN_NAME)
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
        AndroidSqlDeleteStatementImpl(DELETE_STATEMENT)
          PsiElement(DELETE)('DELETE')
          PsiElement(FROM)('FROM')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('t')
          AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
            PsiElement(WHERE)('WHERE')
            AndroidSqlAndExpressionImpl(AND_EXPRESSION)
              AndroidSqlParenExpressionImpl(PAREN_EXPRESSION)
                PsiElement(()('(')
                AndroidSqlInExpressionImpl(IN_EXPRESSION)
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('1')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    AndroidSqlBindParameterImpl(BIND_PARAMETER)
                      PsiElement(NAMED_PARAMETER)(':ids')
                  PsiElement())(')')
                PsiElement())(')')
              PsiElement(AND)('AND')
              AndroidSqlParenExpressionImpl(PAREN_EXPRESSION)
                PsiElement(()('(')
                AndroidSqlInExpressionImpl(IN_EXPRESSION)
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('2')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
                  PsiElement(comma)(',')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('4')
                  PsiElement())(')')
                PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("DELETE FROM t WHERE (1 IN (:ids)) AND (2 IN (3,4))")
    )
  }

  fun testLikeExpressions() {
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('foo')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlLikeExpressionImpl(LIKE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('bar')
                  PsiElement(LIKE)('LIKE')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''baz'')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM foo WHERE bar LIKE 'baz'")
    )

    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('foo')
              AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                PsiElement(WHERE)('WHERE')
                AndroidSqlLikeExpressionImpl(LIKE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('bar')
                  PsiElement(NOT)('NOT')
                  PsiElement(LIKE)('LIKE')
                  AndroidSqlParenExpressionImpl(PAREN_EXPRESSION)
                    PsiElement(()('(')
                    AndroidSqlConcatExpressionImpl(CONCAT_EXPRESSION)
                      AndroidSqlConcatExpressionImpl(CONCAT_EXPRESSION)
                        AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                          PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''%'')
                        PsiElement(||)('||')
                        AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                          AndroidSqlBindParameterImpl(BIND_PARAMETER)
                            PsiElement(NUMBERED_PARAMETER)('?')
                      PsiElement(||)('||')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''%'')
                    PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("SELECT * FROM foo WHERE bar NOT LIKE ('%' || ? || '%')")
    )
  }

  fun testPriorities() {
    assertEquals(
      """
      FILE
        AndroidSqlDeleteStatementImpl(DELETE_STATEMENT)
          PsiElement(DELETE)('DELETE')
          PsiElement(FROM)('FROM')
          AndroidSqlSingleTableStatementTableImpl(SINGLE_TABLE_STATEMENT_TABLE)
            AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
              PsiElement(IDENTIFIER)('t')
          AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
            PsiElement(WHERE)('WHERE')
            AndroidSqlOrExpressionImpl(OR_EXPRESSION)
              AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                  AndroidSqlColumnNameImpl(COLUMN_NAME)
                    PsiElement(IDENTIFIER)('a')
                PsiElement(=)('=')
                AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                  PsiElement(NUMERIC_LITERAL)('1')
              PsiElement(OR)('OR')
              AndroidSqlAndExpressionImpl(AND_EXPRESSION)
                AndroidSqlInExpressionImpl(IN_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('b')
                  PsiElement(IN)('IN')
                  PsiElement(()('(')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('2')
                  PsiElement(comma)(',')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
                  PsiElement())(')')
                PsiElement(AND)('AND')
                AndroidSqlLikeExpressionImpl(LIKE_EXPRESSION)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('c')
                  PsiElement(LIKE)('LIKE')
                  AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                    PsiElement(NUMERIC_LITERAL)('3')
      """.trimIndent(),
      toParseTreeText("DELETE FROM t WHERE a = 1 OR b IN (2,3) AND c LIKE 3")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlLikeExpressionImpl(LIKE_EXPRESSION)
                    AndroidSqlAddExpressionImpl(ADD_EXPRESSION)
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('10')
                      PsiElement(+)('+')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('10')
                    PsiElement(LIKE)('LIKE')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('10')
      """.trimIndent(),
      toParseTreeText("SELECT 10 + 10 LIKE 10")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlAndExpressionImpl(AND_EXPRESSION)
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
                    PsiElement(AND)('AND')
                    AndroidSqlLikeExpressionImpl(LIKE_EXPRESSION)
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                      PsiElement(LIKE)('LIKE')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
      """.trimIndent(),
      toParseTreeText("SELECT 1 AND 2 LIKE 2")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                    AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                      PsiElement(==)('==')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('2')
                    PsiElement(==)('==')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
      """.trimIndent(),
      toParseTreeText("SELECT 2 == 2 == 1")
    )
    assertEquals(
      """
      FILE
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlCaseExpressionImpl(CASE_EXPRESSION)
                    PsiElement(CASE)('CASE')
                    AndroidSqlAndExpressionImpl(AND_EXPRESSION)
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('1')
                      PsiElement(AND)('AND')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(NUMERIC_LITERAL)('0')
                    PsiElement(WHEN)('WHEN')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(NUMERIC_LITERAL)('1')
                    PsiElement(THEN)('THEN')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                      PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''true'')
                    PsiElement(ELSE)('ELSE')
                    AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
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
        AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
          AndroidSqlSelectCoreImpl(SELECT_CORE)
            AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
              PsiElement(SELECT)('SELECT')
              AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                    AndroidSqlColumnNameImpl(COLUMN_NAME)
                      PsiElement(IDENTIFIER)('rowId')
                PsiElement(comma)(',')
                AndroidSqlResultColumnImpl(RESULT_COLUMN)
                  PsiElement(*)('*')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('mail')
      """.trimIndent(),
      toParseTreeText("SELECT rowId, * FROM mail")
    )

    assertEquals(
      """
      FILE
        AndroidSqlCreateTableStatementImpl(CREATE_TABLE_STATEMENT)
          PsiElement(CREATE)('CREATE')
          PsiElement(TABLE)('TABLE')
          PsiElement(IF)('IF')
          PsiElement(NOT)('NOT')
          PsiElement(EXISTS)('EXISTS')
          AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
            PsiElement(IDENTIFIER)('wordcount')
          PsiElement(()('(')
          AndroidSqlColumnDefinitionImpl(COLUMN_DEFINITION)
            AndroidSqlColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('word')
            AndroidSqlTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('TEXT')
            AndroidSqlColumnConstraintImpl(COLUMN_CONSTRAINT)
              PsiElement(PRIMARY)('PRIMARY')
              PsiElement(KEY)('KEY')
              AndroidSqlConflictClauseImpl(CONFLICT_CLAUSE)
                <empty list>
          PsiElement(comma)(',')
          AndroidSqlColumnDefinitionImpl(COLUMN_DEFINITION)
            AndroidSqlColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('cnt')
            AndroidSqlTypeNameImpl(TYPE_NAME)
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
        AndroidSqlCreateTableStatementImpl(CREATE_TABLE_STATEMENT)
          PsiElement(CREATE)('CREATE')
          PsiElement(TABLE)('TABLE')
          PsiElement(IF)('IF')
          PsiElement(NOT)('NOT')
          PsiElement(EXISTS)('EXISTS')
          AndroidSqlTableDefinitionNameImpl(TABLE_DEFINITION_NAME)
            PsiElement(IDENTIFIER)('wordcount')
          PsiElement(()('(')
          AndroidSqlColumnDefinitionImpl(COLUMN_DEFINITION)
            AndroidSqlColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('word')
            AndroidSqlTypeNameImpl(TYPE_NAME)
              PsiElement(IDENTIFIER)('TEXT')
            AndroidSqlColumnConstraintImpl(COLUMN_CONSTRAINT)
              PsiElement(PRIMARY)('PRIMARY')
              PsiElement(KEY)('KEY')
              AndroidSqlConflictClauseImpl(CONFLICT_CLAUSE)
                <empty list>
          PsiElement(comma)(',')
          AndroidSqlColumnDefinitionImpl(COLUMN_DEFINITION)
            AndroidSqlColumnDefinitionNameImpl(COLUMN_DEFINITION_NAME)
              PsiElement(IDENTIFIER)('cnt')
            AndroidSqlTypeNameImpl(TYPE_NAME)
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
