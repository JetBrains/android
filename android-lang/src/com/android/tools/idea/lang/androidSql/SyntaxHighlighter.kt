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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ABORT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ACTION
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ADD
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.AFTER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ALL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ALTER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.AMP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ANALYZE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.AND
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.AS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ASC
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ATTACH
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.AUTOINCREMENT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BACKTICK_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BAR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BEFORE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BEGIN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BETWEEN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BRACKET_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CASCADE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CASE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CAST
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CHECK
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COLLATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COLUMN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COMMA
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COMMENT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COMMIT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CONCAT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CONFLICT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CONSTRAINT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CREATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CROSS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CURRENT_DATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CURRENT_TIME
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CURRENT_TIMESTAMP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DATABASE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DEFAULT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DEFERRABLE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DEFERRED
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DELETE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DESC
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DETACH
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DISTINCT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DIV
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DOT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DROP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EACH
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ELSE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.END
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EQ
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EQEQ
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ESCAPE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EXCEPT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EXCLUSIVE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EXISTS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.EXPLAIN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FAIL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FOR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FOREIGN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FROM
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FULL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.GLOB
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.GROUP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.GT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.GTE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.HAVING
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IDENTIFIER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IF
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IGNORE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IMMEDIATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INDEX
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INDEXED
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INITIALLY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INNER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INSERT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INSTEAD
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INTERSECT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.INTO
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ISNULL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.JOIN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.KEY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LEFT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LIKE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LIMIT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LINE_COMMENT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LPAREN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LTE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.MATCH
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.MINUS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.MOD
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NAMED_PARAMETER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NATURAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NO
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NOT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NOTNULL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NOT_EQ
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NULL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NUMBERED_PARAMETER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NUMERIC_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.OF
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.OFFSET
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ON
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.OR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ORDER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.OUTER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.PLAN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.PLUS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.PRAGMA
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.PRIMARY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.QUERY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RAISE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RECURSIVE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.REFERENCES
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.REGEXP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.REINDEX
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RELEASE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RENAME
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.REPLACE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RESTRICT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RIGHT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ROLLBACK
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.ROW
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RPAREN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SAVEPOINT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SELECT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SEMICOLON
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SET
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SHL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SHR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SINGLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.STAR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TABLE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TEMP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TEMPORARY
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.THEN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TILDE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TO
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TRANSACTION
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TRIGGER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.UNEQ
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.UNION
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.UNIQUE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.UPDATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.USING
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.VACUUM
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.VALUES
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.VIEW
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.VIRTUAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.WHEN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.WHERE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.WITH
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.WITHOUT
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_BACKTICK_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_BRACKET_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

val KEYWORDS = TokenSet.create(
  ABORT, ACTION, ADD, AFTER, ALL, ALTER, ANALYZE, AND, AS, ASC, ATTACH, AUTOINCREMENT, BEFORE, BEGIN, BETWEEN, BY, CASCADE, CASE, CAST,
  CHECK, COLLATE, COLUMN, COMMIT, CONFLICT, CONSTRAINT, CREATE, CROSS, DATABASE, DEFAULT, DEFERRABLE, DEFERRED, DELETE, DESC, DETACH,
  DISTINCT, DROP, EACH, ELSE, END, ESCAPE, EXCEPT, EXCLUSIVE, EXISTS, EXPLAIN, FAIL, FOR, FOREIGN, FROM, FULL, GLOB, GROUP, HAVING, IF,
  IGNORE, IMMEDIATE, IN, INDEX, INDEXED, INITIALLY, INNER, INSERT, INSTEAD, INTERSECT, INTO, IS, ISNULL, JOIN, KEY, LEFT, LIKE, LIMIT,
  MATCH, NATURAL, NO, NOT, NOTNULL, NULL, OF, OFFSET, ON, OR, ORDER, OUTER, PLAN, PRAGMA, PRIMARY, QUERY, RAISE, RECURSIVE, REFERENCES,
  REGEXP, REINDEX, RELEASE, RENAME, REPLACE, RESTRICT, RIGHT, ROLLBACK, ROW, SAVEPOINT, SELECT, SET, TABLE, TEMP, TEMPORARY, THEN,
  TO, TRANSACTION, TRIGGER, UNION, UNIQUE, UPDATE, USING, VACUUM, VALUES, VIEW, VIRTUAL, WHEN, WHERE, WITH, WITHOUT
)

val OPERATORS =
  TokenSet.create(AMP, BAR, CONCAT, DIV, EQ, EQEQ, GT, GTE, LT, LTE, MINUS, MOD, NOT_EQ, PLUS, SHL, SHR, STAR, TILDE, UNEQ)

val IDENTIFIERS = TokenSet.create(
  IDENTIFIER,
  BRACKET_LITERAL,
  BACKTICK_LITERAL,
  UNTERMINATED_BRACKET_LITERAL,
  UNTERMINATED_BACKTICK_LITERAL
)

val STRING_LITERALS = TokenSet.create(
  SINGLE_QUOTE_STRING_LITERAL,
  DOUBLE_QUOTE_STRING_LITERAL,
  UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL,
  UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL
)

val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
val COMMENTS = TokenSet.create(COMMENT, LINE_COMMENT)
val CONSTANTS = TokenSet.create(CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP)


private enum class AndroidSqlTextAttributes(fallback: TextAttributesKey) {
  BAD_CHARACTER(HighlighterColors.BAD_CHARACTER),
  KEYWORD(DefaultLanguageHighlighterColors.KEYWORD),
  NUMBER(DefaultLanguageHighlighterColors.NUMBER),
  PARAMETER(DefaultLanguageHighlighterColors.INSTANCE_FIELD),
  STRING(DefaultLanguageHighlighterColors.STRING),
  BLOCK_COMMENT(DefaultLanguageHighlighterColors.BLOCK_COMMENT),
  LINE_COMMENT(DefaultLanguageHighlighterColors.LINE_COMMENT),
  OPERATOR(DefaultLanguageHighlighterColors.OPERATION_SIGN),
  PARENTHESES(DefaultLanguageHighlighterColors.PARENTHESES),
  DOT(DefaultLanguageHighlighterColors.DOT),
  COMMA(DefaultLanguageHighlighterColors.COMMA),
  SEMICOLON(DefaultLanguageHighlighterColors.SEMICOLON),
  CONSTANT(DefaultLanguageHighlighterColors.CONSTANT),
  IDENTIFIER(DefaultLanguageHighlighterColors.IDENTIFIER),
  ;

  val key = TextAttributesKey.createTextAttributesKey("ANDROID_SQL_$name", fallback)
  val keys = arrayOf(key)
}

private val EMPTY_KEYS = emptyArray<TextAttributesKey>()

class AndroidSqlSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = AndroidSqlLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    in KEYWORDS -> AndroidSqlTextAttributes.KEYWORD.keys
    in OPERATORS -> AndroidSqlTextAttributes.OPERATOR.keys
    in CONSTANTS -> AndroidSqlTextAttributes.CONSTANT.keys
    in STRING_LITERALS -> AndroidSqlTextAttributes.STRING.keys
    in IDENTIFIERS -> AndroidSqlTextAttributes.IDENTIFIER.keys
    NUMERIC_LITERAL -> AndroidSqlTextAttributes.NUMBER.keys
    NAMED_PARAMETER -> AndroidSqlTextAttributes.PARAMETER.keys
    NUMBERED_PARAMETER -> AndroidSqlTextAttributes.NUMBER.keys
    LINE_COMMENT -> AndroidSqlTextAttributes.LINE_COMMENT.keys
    COMMENT -> AndroidSqlTextAttributes.BLOCK_COMMENT.keys
    LPAREN, RPAREN -> AndroidSqlTextAttributes.PARENTHESES.keys
    DOT -> AndroidSqlTextAttributes.DOT.keys
    COMMA -> AndroidSqlTextAttributes.COMMA.keys
    SEMICOLON -> AndroidSqlTextAttributes.SEMICOLON.keys
    TokenType.BAD_CHARACTER -> AndroidSqlTextAttributes.BAD_CHARACTER.keys
    else -> EMPTY_KEYS
  }

}

class AndroidSqlSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = AndroidSqlSyntaxHighlighter()
}

