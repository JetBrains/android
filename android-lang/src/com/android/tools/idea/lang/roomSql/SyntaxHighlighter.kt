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
package com.android.tools.idea.lang.roomSql

import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ABORT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ACTION
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ADD
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.AFTER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ALL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ALTER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.AMP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ANALYZE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.AND
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.AS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ASC
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ATTACH
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.AUTOINCREMENT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BACKTICK_LITERAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BAR
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BEFORE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BEGIN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BETWEEN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BRACKET_LITERAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.BY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CASCADE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CASE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CAST
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CHECK
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.COLLATE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.COLUMN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.COMMA
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.COMMENT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.COMMIT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CONCAT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CONFLICT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CONSTRAINT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CREATE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CROSS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CURRENT_DATE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CURRENT_TIME
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.CURRENT_TIMESTAMP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DATABASE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DEFAULT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DEFERRABLE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DEFERRED
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DELETE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DESC
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DETACH
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DISTINCT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DIV
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DOT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.DROP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EACH
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ELSE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.END
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EQ
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EQEQ
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ESCAPE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EXCEPT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EXCLUSIVE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EXISTS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.EXPLAIN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.FAIL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.FOR
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.FOREIGN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.FROM
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.FULL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.GLOB
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.GROUP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.GT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.GTE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.HAVING
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IDENTIFIER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IF
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IGNORE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IMMEDIATE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INDEX
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INDEXED
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INITIALLY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INNER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INSERT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INSTEAD
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INTERSECT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.INTO
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.IS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ISNULL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.JOIN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.KEY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LEFT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LIKE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LIMIT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LINE_COMMENT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LPAREN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.LTE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.MATCH
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.MINUS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.MOD
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NAMED_PARAMETER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NATURAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NO
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NOT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NOTNULL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NOT_EQ
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NULL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NUMBERED_PARAMETER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.NUMERIC_LITERAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.OF
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.OFFSET
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ON
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.OR
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ORDER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.OUTER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.PLAN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.PLUS
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.PRAGMA
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.PRIMARY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.QUERY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RAISE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RECURSIVE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.REFERENCES
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.REGEXP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.REINDEX
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RELEASE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RENAME
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.REPLACE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RESTRICT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RIGHT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ROLLBACK
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.ROW
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.RPAREN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SAVEPOINT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SELECT
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SEMICOLON
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SET
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SHL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SHR
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.SINGLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.STAR
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TABLE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TEMP
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TEMPORARY
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.THEN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TILDE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TO
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TRANSACTION
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.TRIGGER
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.UNEQ
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.UNION
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.UNIQUE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.UPDATE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.USING
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.VACUUM
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.VALUES
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.VIEW
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.VIRTUAL
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.WHEN
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.WHERE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.WITH
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.WITHOUT
import com.android.tools.idea.lang.roomSql.psi.UNTERMINATED_BACKTICK_LITERAL
import com.android.tools.idea.lang.roomSql.psi.UNTERMINATED_BRACKET_LITERAL
import com.android.tools.idea.lang.roomSql.psi.UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.roomSql.psi.UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL
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


private enum class RoomSqlTextAttributes(fallback: TextAttributesKey) {
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

  val key = TextAttributesKey.createTextAttributesKey("ROOM_SQL_$name", fallback)
  val keys = arrayOf(key)
}

private val EMPTY_KEYS = emptyArray<TextAttributesKey>()

class RoomSqlSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = RoomSqlLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    in KEYWORDS -> RoomSqlTextAttributes.KEYWORD.keys
    in OPERATORS -> RoomSqlTextAttributes.OPERATOR.keys
    in CONSTANTS -> RoomSqlTextAttributes.CONSTANT.keys
    in STRING_LITERALS -> RoomSqlTextAttributes.STRING.keys
    in IDENTIFIERS -> RoomSqlTextAttributes.IDENTIFIER.keys
    NUMERIC_LITERAL -> RoomSqlTextAttributes.NUMBER.keys
    NAMED_PARAMETER -> RoomSqlTextAttributes.PARAMETER.keys
    NUMBERED_PARAMETER -> RoomSqlTextAttributes.NUMBER.keys
    LINE_COMMENT -> RoomSqlTextAttributes.LINE_COMMENT.keys
    COMMENT -> RoomSqlTextAttributes.BLOCK_COMMENT.keys
    LPAREN, RPAREN -> RoomSqlTextAttributes.PARENTHESES.keys
    DOT -> RoomSqlTextAttributes.DOT.keys
    COMMA -> RoomSqlTextAttributes.COMMA.keys
    SEMICOLON -> RoomSqlTextAttributes.SEMICOLON.keys
    TokenType.BAD_CHARACTER -> RoomSqlTextAttributes.BAD_CHARACTER.keys
    else -> EMPTY_KEYS
  }

}

class RoomSqlSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = RoomSqlSyntaxHighlighter()
}

