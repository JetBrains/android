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
    assert(getErrorMessage(input) == null) {
      "Parsing $input failed:\n${toParseTreeText(input)}"
    }

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

  fun testWindowFunctionCallExpressions() {
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
                    PsiElement(IDENTIFIER)('DENSE_RANK')
                    PsiElement(()('(')
                    PsiElement())(')')
                    AndroidSqlOverClauseImpl(OVER_CLAUSE)
                      PsiElement(OVER)('OVER')
                      AndroidSqlWindowDefinitionImpl(WINDOW_DEFINITION)
                        PsiElement(()('(')
                        AndroidSqlPartitionClauseImpl(PARTITION_CLAUSE)
                          PsiElement(PARTITION)('PARTITION')
                          PsiElement(BY)('BY')
                          AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                            AndroidSqlColumnNameImpl(COLUMN_NAME)
                              PsiElement(IDENTIFIER)('department')
                        AndroidSqlOrderClauseImpl(ORDER_CLAUSE)
                          PsiElement(ORDER)('ORDER')
                          PsiElement(BY)('BY')
                          AndroidSqlOrderingTermImpl(ORDERING_TERM)
                            AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                              AndroidSqlColumnNameImpl(COLUMN_NAME)
                                PsiElement(IDENTIFIER)('salary')
                        AndroidSqlFrameSpecImpl(FRAME_SPEC)
                          AndroidSqlFrameClauseImpl(FRAME_CLAUSE)
                            PsiElement(RANGE)('RANGE')
                            PsiElement(BETWEEN)('BETWEEN')
                            PsiElement(UNBOUNDED)('UNBOUNDED')
                            PsiElement(PRECEDING)('PRECEDING')
                            PsiElement(AND)('AND')
                            PsiElement(UNBOUNDED)('UNBOUNDED')
                            PsiElement(FOLLOWING)('FOLLOWING')
                          PsiElement(EXCLUDE)('EXCLUDE')
                          PsiElement(CURRENT)('CURRENT')
                          PsiElement(ROW)('ROW')
                        PsiElement())(')')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('employees')
      """.trimIndent(),
      toParseTreeText(
        """
        SELECT DENSE_RANK()
        OVER (PARTITION BY department
         ORDER BY salary
         RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
         EXCLUDE CURRENT ROW)
        FROM employees
        """
      )
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
                    PsiElement(IDENTIFIER)('FIRST_VALUE')
                    PsiElement(()('(')
                    AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                      AndroidSqlColumnNameImpl(COLUMN_NAME)
                        PsiElement(IDENTIFIER)('salary')
                    PsiElement())(')')
                    AndroidSqlFilterClauseImpl(FILTER_CLAUSE)
                      PsiElement(FILTER)('FILTER')
                      PsiElement(()('(')
                      PsiElement(WHERE)('WHERE')
                      AndroidSqlComparisonExpressionImpl(COMPARISON_EXPRESSION)
                        AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                          AndroidSqlColumnNameImpl(COLUMN_NAME)
                            PsiElement(IDENTIFIER)('hire_date')
                        PsiElement(<)('<')
                        AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                          PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''2023-01-01'')
                      PsiElement())(')')
                    AndroidSqlOverClauseImpl(OVER_CLAUSE)
                      PsiElement(OVER)('OVER')
                      AndroidSqlWindowDefinitionImpl(WINDOW_DEFINITION)
                        PsiElement(()('(')
                        AndroidSqlPartitionClauseImpl(PARTITION_CLAUSE)
                          PsiElement(PARTITION)('PARTITION')
                          PsiElement(BY)('BY')
                          AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                            AndroidSqlColumnNameImpl(COLUMN_NAME)
                              PsiElement(IDENTIFIER)('department')
                        AndroidSqlOrderClauseImpl(ORDER_CLAUSE)
                          PsiElement(ORDER)('ORDER')
                          PsiElement(BY)('BY')
                          AndroidSqlOrderingTermImpl(ORDERING_TERM)
                            AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                              AndroidSqlColumnNameImpl(COLUMN_NAME)
                                PsiElement(IDENTIFIER)('hire_date')
                        PsiElement())(')')
              AndroidSqlFromClauseImpl(FROM_CLAUSE)
                PsiElement(FROM)('FROM')
                AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                  AndroidSqlFromTableImpl(FROM_TABLE)
                    AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                      PsiElement(IDENTIFIER)('employees')
      """.trimIndent(),
      toParseTreeText(
        """
        SELECT FIRST_VALUE(salary) FILTER (WHERE hire_date < '2023-01-01')
        OVER (PARTITION BY department ORDER BY hire_date) FROM employees
        """
      )
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
              PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT, ORDER or WINDOW expected, got 'parse'
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
                    PsiErrorElement:'(', '.', <compound operator>, BETWEEN, GROUP, IN, LIMIT, ORDER or WINDOW expected, got 'parse'
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
                            PsiErrorElement:<compound operator>, FROM, GROUP, LIMIT, ORDER, WHERE, WINDOW or comma expected, got 'at'
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

  /**
   * Tests the parsing of the 'window_function_call_expression' rule.
   * These test cases cover various window functions, including those with and without
   * arguments, with and without frames, and with various window definition clauses.
   */
  fun testWindowFunctions_parse() {
    listOf(
      // ROW_NUMBER (simplest window function)
      "SELECT ROW_NUMBER() OVER () FROM employees",

      // RANK with ORDER BY
      "SELECT RANK() OVER (ORDER BY salary) FROM employees",

      // DENSE_RANK with PARTITION BY
      "SELECT DENSE_RANK() OVER (PARTITION BY department) FROM employees",

      // LAG with offset
      "SELECT LAG(salary, 1) OVER (ORDER BY hire_date) FROM employees",

      // LEAD with default value
      "SELECT LEAD(salary, 1, 0) OVER (ORDER BY hire_date) FROM employees",

      // NTILE
      "SELECT NTILE(4) OVER (ORDER BY salary) FROM employees",

      // FIRST_VALUE
      "SELECT FIRST_VALUE(salary) OVER (PARTITION BY department ORDER BY hire_date) FROM employees",

      // LAST_VALUE
      "SELECT LAST_VALUE(salary) OVER (PARTITION BY department ORDER BY hire_date RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM employees",

      // NTH_VALUE
      "SELECT NTH_VALUE(salary, 2) OVER (ORDER BY hire_date) FROM employees",

      // CUME_DIST
      "SELECT CUME_DIST() OVER (ORDER BY salary) FROM employees",

      // PERCENT_RANK
      "SELECT PERCENT_RANK() OVER (PARTITION BY department ORDER BY salary) FROM employees",

      // STAR
      "SELECT COUNT(*) OVER (PARTITION BY department) FROM employees",

      // STAR and ORDER BY
      "SELECT RANK(*) OVER (ORDER BY salary DESC) FROM employees",

      // window_definition - PARTITION BY multiple
      "SELECT AVG(salary) OVER (PARTITION BY department, city) FROM employees",

      // window_definition - ORDER BY multiple
      "SELECT FIRST_VALUE(salary) OVER (ORDER BY hire_date DESC, salary ASC) FROM employees",

      // frame_clause - frame_single - UNBOUNDED PRECEDING
      "SELECT SUM(salary) OVER (ORDER BY salary ROWS UNBOUNDED PRECEDING) FROM employees",

      // frame_clause - BETWEEN with various frame boundaries
      "SELECT SUM(salary) OVER (ORDER BY salary ROWS BETWEEN 1 PRECEDING AND 2 FOLLOWING) FROM employees",

      // window_definition - frame_spec - ROWS
      "SELECT SUM(salary) OVER (ORDER BY salary ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) FROM employees",

      // window_definition - frame_spec - RANGE
      """
      SELECT AVG(salary)
      OVER (ORDER BY hire_date RANGE BETWEEN '1 year' PRECEDING AND '1 year' FOLLOWING)
      FROM employees
      """,

      // window_definition - frame_spec - GROUPS
      "SELECT SUM(salary) OVER (ORDER BY salary GROUPS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM employees",

      // EXCLUDE - NO OTHERS with ROWS
      """
      SELECT RANK()
      OVER (ORDER BY salary DESC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING EXCLUDE NO OTHERS)
      FROM employees
      """,

      // EXCLUDE - CURRENT ROW with RANGE
      """
      SELECT DENSE_RANK()
      OVER (PARTITION BY department
       ORDER BY salary
       RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
       EXCLUDE CURRENT ROW)
      FROM employees
      """,

      // EXCLUDE - GROUP with ROWS
      """
      SELECT NTILE(4)
      OVER (ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING EXCLUDE GROUP)
      FROM employees""",

      // EXCLUDE - TIES with RANGE
      """
      SELECT RANK()
      OVER (ORDER BY salary RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING EXCLUDE TIES)
      FROM employees
      """,

      // FIRST_VALUE with filter
      """
      SELECT FIRST_VALUE(salary) FILTER (WHERE hire_date < '2023-01-01')
      OVER (PARTITION BY department ORDER BY hire_date) FROM employees
      """,

      // FILTER
      """
      SELECT SUM(salary) FILTER (WHERE hire_date < '2023-01-01')
      OVER (PARTITION BY department) FROM employees
      """,
    ).forEach(::check)
  }

  /**
   * Tests the parsing of the 'simple_function_call_expression' rule.
   * These test cases cover various scenarios including different numbers of arguments,
   * nested function calls, and different types of expressions within the function arguments.
   */
  fun testSimpleFunctions_parse() {
    listOf(
      // No arguments
      "SELECT ABS() FROM employees",

      // One argument
      "SELECT LENGTH(name) FROM employees",

      // Multiple arguments
      "SELECT SUBSTR(name, 2, 3) FROM employees",

      // Nested function call
      "SELECT UPPER(TRIM(name)) FROM employees",

      // Expression as argument
      "SELECT LENGTH(name || ' ' || surname) FROM employees",

      // Bind parameter
      "SELECT INSTR(name, ?1) FROM employees",

      // Literal parameter
      "SELECT LOWER('HELLO') FROM employees",
    ).forEach(::check)
  }

  /**
   * Tests the parsing of aggregate functions.
   * These test cases focus on features specific to aggregate functions that
   * differentiate them from simple functions, such as DISTINCT, ORDER BY, and FILTER.
   */
  fun testAggregateFunctions_parse() {
    listOf(
      // DISTINCT (simple functions don't have DISTINCT)
      "SELECT COUNT(DISTINCT department) FROM employees",

      // ORDER BY (simple functions don't have ORDER BY)
      "SELECT GROUP_CONCAT(name, ', ' ORDER BY hire_date) FROM employees",

      // Multiple ORDER BY parts
      "SELECT GROUP_CONCAT(name, ', ' ORDER BY department ASC, hire_date DESC) FROM employees",

      // ORDER BY and COLLATE
      "SELECT GROUP_CONCAT(name, ', ' ORDER BY name COLLATE NOCASE) FROM employees",

      // FILTER (simple functions don't have FILTER)
      "SELECT SUM(salary) FILTER (WHERE hire_date < '2023-01-01') FROM employees",

      // STAR with FILTER
      "SELECT COUNT(*) FILTER (WHERE region = 'West') FROM employees",

      // Empty parens with FILTER
      "SELECT COUNT() FILTER (WHERE region = 'West') FROM employees",

      // ORDER BY and FILTER
      "SELECT GROUP_CONCAT(name, ', ' ORDER BY hire_date) FILTER (WHERE salary > 50000) FROM employees",

      // ORDER BY, FILTER, and DISTINCT
      """
      SELECT GROUP_CONCAT(DISTINCT department, ', ' ORDER BY department)
      FILTER (WHERE salary > 50000) FROM employees
      """,
    ).forEach(::check)
  }

  /**
   * Tests the parsing of the 'window_clause' within the 'select_core_select' rule.
   * These test cases focus on the window_clause itself and do NOT include any actual
   * window functions, ensuring the parser can handle the window_clause correctly even
   * when no window functions are present in the query.
   */
  fun testWindowClauses_parse() {
    listOf(
      // select_core_select - with window_clause (single window)
      "SELECT name, salary FROM employees WINDOW w AS (PARTITION BY department)",

      // select_core_select - with window_clause (multiple windows)
      """
      SELECT name, salary, department FROM employees
      WINDOW w1 AS (PARTITION BY department), w2 AS (ORDER BY salary DESC)
      """,

      // select_core_select - with window_clause and other clauses
      """
      SELECT DISTINCT name, salary FROM employees
      WHERE salary > 50000
      GROUP BY department
      HAVING COUNT (*) > 1
      WINDOW w AS(ORDER BY hire_date ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
      """,
    ).forEach(::check)
  }
}
