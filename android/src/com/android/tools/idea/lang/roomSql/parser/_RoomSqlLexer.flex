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
package com.android.tools.idea.lang.roomSql.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*;

%%

%{
  public _RoomSqlLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _RoomSqlLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%caseless

WHITE_SPACE=\s+

COMMENT="/*" ( ([^"*"]|[\r\n])* ("*"+ [^"*""/"] )? )* ("*" | "*"+"/")?
IDENTIFIER=([:letter:]|_)([:letter:]|[:digit:]|_)*
LINE_COMMENT=--[^\r\n]*
NUMERIC_LITERAL=(([0-9]+(\.[0-9]*)?|\.[0-9]+)(E(\+|-)?[0-9]+)?)|(0x[0-9a-f]+)
PARAMETER_NAME=:{IDENTIFIER}

// Unterminated strings are BAD_CHARACTER tokens.
STRING_BAD_SINGLE=X?\'(\'\'|[^\'])*
SINGLE_QUOTE_STRING_LITERAL={STRING_BAD_SINGLE} \'
STRING_BAD_DOUBLE=X?\"(\"\"|[^\"])*
DOUBLE_QUOTE_STRING_LITERAL={STRING_BAD_DOUBLE} \"
BACKTICK_LITERAL_BAD=\`(\`\`|[^\`])*
BACKTICK_LITERAL={BACKTICK_LITERAL_BAD} \`
BRACKET_LITERAL_BAD=\[[^\]]*
BRACKET_LITERAL={BRACKET_LITERAL_BAD} \]

%%
<YYINITIAL> {
  {WHITE_SPACE}                       { return WHITE_SPACE; }

  "!="                                { return NOT_EQ; }
  "%"                                 { return MOD; }
  "&"                                 { return AMP; }
  "("                                 { return LPAREN; }
  ")"                                 { return RPAREN; }
  "*"                                 { return STAR; }
  "+"                                 { return PLUS; }
  ","                                 { return COMMA; }
  "-"                                 { return MINUS; }
  "."                                 { return DOT; }
  "/"                                 { return DIV; }
  ";"                                 { return SEMICOLON; }
  "<"                                 { return LT; }
  "<<"                                { return SHL; }
  "<="                                { return LTE; }
  "<>"                                { return UNEQ; }
  "="                                 { return EQ; }
  "=="                                { return EQEQ; }
  ">"                                 { return GT; }
  ">="                                { return GTE; }
  ">>"                                { return SHR; }
  "ABORT"                             { return ABORT; }
  "ACTION"                            { return ACTION; }
  "ADD"                               { return ADD; }
  "AFTER"                             { return AFTER; }
  "ALL"                               { return ALL; }
  "ALTER"                             { return ALTER; }
  "ANALYZE"                           { return ANALYZE; }
  "AND"                               { return AND; }
  "AS"                                { return AS; }
  "ASC"                               { return ASC; }
  "ATTACH"                            { return ATTACH; }
  "AUTOINCREMENT"                     { return AUTOINCREMENT; }
  "BEFORE"                            { return BEFORE; }
  "BEGIN"                             { return BEGIN; }
  "BETWEEN"                           { return BETWEEN; }
  "BY"                                { return BY; }
  "CASCADE"                           { return CASCADE; }
  "CASE"                              { return CASE; }
  "CAST"                              { return CAST; }
  "CHECK"                             { return CHECK; }
  "COLLATE"                           { return COLLATE; }
  "COLUMN"                            { return COLUMN; }
  "COMMIT"                            { return COMMIT; }
  "CONFLICT"                          { return CONFLICT; }
  "CONSTRAINT"                        { return CONSTRAINT; }
  "CREATE"                            { return CREATE; }
  "CROSS"                             { return CROSS; }
  "CURRENT_DATE"                      { return CURRENT_DATE; }
  "CURRENT_TIME"                      { return CURRENT_TIME; }
  "CURRENT_TIMESTAMP"                 { return CURRENT_TIMESTAMP; }
  "DATABASE"                          { return DATABASE; }
  "DEFAULT"                           { return DEFAULT; }
  "DEFERRABLE"                        { return DEFERRABLE; }
  "DEFERRED"                          { return DEFERRED; }
  "DELETE"                            { return DELETE; }
  "DESC"                              { return DESC; }
  "DETACH"                            { return DETACH; }
  "DISTINCT"                          { return DISTINCT; }
  "DROP"                              { return DROP; }
  "EACH"                              { return EACH; }
  "ELSE"                              { return ELSE; }
  "END"                               { return END; }
  "ESCAPE"                            { return ESCAPE; }
  "EXCEPT"                            { return EXCEPT; }
  "EXCLUSIVE"                         { return EXCLUSIVE; }
  "EXISTS"                            { return EXISTS; }
  "EXPLAIN"                           { return EXPLAIN; }
  "FAIL"                              { return FAIL; }
  "FOR"                               { return FOR; }
  "FOREIGN"                           { return FOREIGN; }
  "FROM"                              { return FROM; }
  "GLOB"                              { return GLOB; }
  "GROUP"                             { return GROUP; }
  "HAVING"                            { return HAVING; }
  "IF"                                { return IF; }
  "IGNORE"                            { return IGNORE; }
  "IMMEDIATE"                         { return IMMEDIATE; }
  "IN"                                { return IN; }
  "INDEX"                             { return INDEX; }
  "INDEXED"                           { return INDEXED; }
  "INITIALLY"                         { return INITIALLY; }
  "INNER"                             { return INNER; }
  "INSERT"                            { return INSERT; }
  "INSTEAD"                           { return INSTEAD; }
  "INTERSECT"                         { return INTERSECT; }
  "INTO"                              { return INTO; }
  "IS"                                { return IS; }
  "ISNULL"                            { return ISNULL; }
  "JOIN"                              { return JOIN; }
  "KEY"                               { return KEY; }
  "LEFT"                              { return LEFT; }
  "LIKE"                              { return LIKE; }
  "LIMIT"                             { return LIMIT; }
  "MATCH"                             { return MATCH; }
  "NATURAL"                           { return NATURAL; }
  "NO"                                { return NO; }
  "NOT"                               { return NOT; }
  "NOTNULL"                           { return NOTNULL; }
  "NULL"                              { return NULL; }
  "OF"                                { return OF; }
  "OFFSET"                            { return OFFSET; }
  "ON"                                { return ON; }
  "OR"                                { return OR; }
  "ORDER"                             { return ORDER; }
  "OUTER"                             { return OUTER; }
  "PLAN"                              { return PLAN; }
  "PRAGMA"                            { return PRAGMA; }
  "PRIMARY"                           { return PRIMARY; }
  "QUERY"                             { return QUERY; }
  "RAISE"                             { return RAISE; }
  "RECURSIVE"                         { return RECURSIVE; }
  "REFERENCES"                        { return REFERENCES; }
  "REGEXP"                            { return REGEXP; }
  "REINDEX"                           { return REINDEX; }
  "RELEASE"                           { return RELEASE; }
  "RENAME"                            { return RENAME; }
  "REPLACE"                           { return REPLACE; }
  "RESTRICT"                          { return RESTRICT; }
  "ROLLBACK"                          { return ROLLBACK; }
  "ROW"                               { return ROW; }
  "ROWID"                             { return ROWID; }
  "SAVEPOINT"                         { return SAVEPOINT; }
  "SELECT"                            { return SELECT; }
  "SET"                               { return SET; }
  "TABLE"                             { return TABLE; }
  "TEMP"                              { return TEMP; }
  "TEMPORARY"                         { return TEMPORARY; }
  "THEN"                              { return THEN; }
  "TO"                                { return TO; }
  "TRANSACTION"                       { return TRANSACTION; }
  "TRIGGER"                           { return TRIGGER; }
  "UNION"                             { return UNION; }
  "UNIQUE"                            { return UNIQUE; }
  "UPDATE"                            { return UPDATE; }
  "USING"                             { return USING; }
  "VACUUM"                            { return VACUUM; }
  "VALUES"                            { return VALUES; }
  "VIEW"                              { return VIEW; }
  "VIRTUAL"                           { return VIRTUAL; }
  "WHEN"                              { return WHEN; }
  "WHERE"                             { return WHERE; }
  "WITH"                              { return WITH; }
  "WITHOUT"                           { return WITHOUT; }
  "|"                                 { return BAR; }
  "||"                                { return CONCAT; }
  "~"                                 { return TILDE; }

  {BACKTICK_LITERAL}                  { return BACKTICK_LITERAL; }
  {BRACKET_LITERAL}                   { return BRACKET_LITERAL; }
  {COMMENT}                           { return COMMENT; }
  {DOUBLE_QUOTE_STRING_LITERAL}       { return DOUBLE_QUOTE_STRING_LITERAL; }
  {IDENTIFIER}                        { return IDENTIFIER; }
  {LINE_COMMENT}                      { return LINE_COMMENT; }
  {NUMERIC_LITERAL}                   { return NUMERIC_LITERAL; }
  {PARAMETER_NAME}                    { return PARAMETER_NAME; }
  {SINGLE_QUOTE_STRING_LITERAL}       { return SINGLE_QUOTE_STRING_LITERAL; }

}

{STRING_BAD_SINGLE} { return BAD_CHARACTER; }
{STRING_BAD_DOUBLE} { return BAD_CHARACTER; }
{BRACKET_LITERAL_BAD} { return BAD_CHARACTER; }
{BACKTICK_LITERAL_BAD} { return BAD_CHARACTER; }
[^] { return BAD_CHARACTER; }
