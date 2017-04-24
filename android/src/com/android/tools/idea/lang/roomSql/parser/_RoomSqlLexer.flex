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

EOL=\R
WHITE_SPACE=\s+

NUMERIC_LITERAL=([0-9]+(\.[0-9]*)?|\.[0-9]+)(E(\+|-)?[0-9]+)?
NAME_LITERAL=[a-z][a-zA-Z_0-9]*
PARAMETER_NAME=:[a-z][a-zA-Z_0-9]*
STRING_LITERAL=\"[^\"]*\"
BLOB_LITERAL=x\"[^\"]*\"
COMMENT=--[^\r\n]

%%
<YYINITIAL> {
  {WHITE_SPACE}                       { return WHITE_SPACE; }

  "SELECT"                            { return SELECT; }
  "FROM"                              { return FROM; }
  "WHERE"                             { return WHERE; }
  "DISTINCT"                          { return DISTINCT; }
  "DELETE"                            { return DELETE; }
  "INSERT"                            { return INSERT; }
  "INTO"                              { return INTO; }
  "VALUES"                            { return VALUES; }
  "EXPLAIN"                           { return EXPLAIN; }
  "QUERY"                             { return QUERY; }
  "PLAN"                              { return PLAN; }
  "ALTER"                             { return ALTER; }
  "TABLE"                             { return TABLE; }
  "RENAME"                            { return RENAME; }
  "TO"                                { return TO; }
  "ADD"                               { return ADD; }
  "COLUMN"                            { return COLUMN; }
  "ANALYZE"                           { return ANALYZE; }
  "ATTACH"                            { return ATTACH; }
  "DATABASE"                          { return DATABASE; }
  "AS"                                { return AS; }
  "BEGIN"                             { return BEGIN; }
  "DEFERRED"                          { return DEFERRED; }
  "IMMEDIATE"                         { return IMMEDIATE; }
  "EXCLUSIVE"                         { return EXCLUSIVE; }
  "TRANSACTION"                       { return TRANSACTION; }
  "COMMIT"                            { return COMMIT; }
  "END"                               { return END; }
  "ROLLBACK"                          { return ROLLBACK; }
  "SAVEPOINT"                         { return SAVEPOINT; }
  "RELEASE"                           { return RELEASE; }
  "CREATE"                            { return CREATE; }
  "UNIQUE"                            { return UNIQUE; }
  "INDEX"                             { return INDEX; }
  "IF"                                { return IF; }
  "NOT"                               { return NOT; }
  "EXISTS"                            { return EXISTS; }
  "ON"                                { return ON; }
  "COLLATE"                           { return COLLATE; }
  "ASC"                               { return ASC; }
  "DESC"                              { return DESC; }
  "TEMP"                              { return TEMP; }
  "TEMPORARY"                         { return TEMPORARY; }
  "WITHOUT"                           { return WITHOUT; }
  "ROWID"                             { return ROWID; }
  "CONSTRAINT"                        { return CONSTRAINT; }
  "PRIMARY"                           { return PRIMARY; }
  "KEY"                               { return KEY; }
  "AUTOINCREMENT"                     { return AUTOINCREMENT; }
  "NULL"                              { return NULL; }
  "CHECK"                             { return CHECK; }
  "DEFAULT"                           { return DEFAULT; }
  "FOREIGN"                           { return FOREIGN; }
  "REFERENCES"                        { return REFERENCES; }
  "UPDATE"                            { return UPDATE; }
  "SET"                               { return SET; }
  "CASCADE"                           { return CASCADE; }
  "RESTRICT"                          { return RESTRICT; }
  "NO"                                { return NO; }
  "ACTION"                            { return ACTION; }
  "MATCH"                             { return MATCH; }
  "DEFERRABLE"                        { return DEFERRABLE; }
  "INITIALLY"                         { return INITIALLY; }
  "CONFLICT"                          { return CONFLICT; }
  "ABORT"                             { return ABORT; }
  "FAIL"                              { return FAIL; }
  "IGNORE"                            { return IGNORE; }
  "REPLACE"                           { return REPLACE; }
  "TRIGGER"                           { return TRIGGER; }
  "BEFORE"                            { return BEFORE; }
  "AFTER"                             { return AFTER; }
  "INSTEAD"                           { return INSTEAD; }
  "OF"                                { return OF; }
  "FOR"                               { return FOR; }
  "EACH"                              { return EACH; }
  "ROW"                               { return ROW; }
  "WHEN"                              { return WHEN; }
  "VIEW"                              { return VIEW; }
  "VIRTUAL"                           { return VIRTUAL; }
  "USING"                             { return USING; }
  "WITH"                              { return WITH; }
  "RECURSIVE"                         { return RECURSIVE; }
  "ORDER"                             { return ORDER; }
  "BY"                                { return BY; }
  "LIMIT"                             { return LIMIT; }
  "OFFSET"                            { return OFFSET; }
  "DETACH"                            { return DETACH; }
  "DROP"                              { return DROP; }
  "CAST"                              { return CAST; }
  "CASE"                              { return CASE; }
  "THEN"                              { return THEN; }
  "ELSE"                              { return ELSE; }
  "RAISE"                             { return RAISE; }
  "CURRENT_TIME"                      { return CURRENT_TIME; }
  "CURRENT_DATE"                      { return CURRENT_DATE; }
  "CURRENT_TIMESTAMP"                 { return CURRENT_TIMESTAMP; }
  "OR"                                { return OR; }
  "PRAGMA"                            { return PRAGMA; }
  "REINDEX"                           { return REINDEX; }
  "ALL"                               { return ALL; }
  "GROUP"                             { return GROUP; }
  "HAVING"                            { return HAVING; }
  "INDEXED"                           { return INDEXED; }
  "NATURAL"                           { return NATURAL; }
  "LEFT"                              { return LEFT; }
  "OUTER"                             { return OUTER; }
  "INNER"                             { return INNER; }
  "CROSS"                             { return CROSS; }
  "JOIN"                              { return JOIN; }
  "UNION"                             { return UNION; }
  "INTERSECT"                         { return INTERSECT; }
  "EXCEPT"                            { return EXCEPT; }
  "VACUUM"                            { return VACUUM; }

  {NUMERIC_LITERAL}                   { return NUMERIC_LITERAL; }
  {NAME_LITERAL}                      { return NAME_LITERAL; }
  {PARAMETER_NAME}                    { return PARAMETER_NAME; }
  {STRING_LITERAL}                    { return STRING_LITERAL; }
  {BLOB_LITERAL}                      { return BLOB_LITERAL; }
  {COMMENT}                           { return COMMENT; }

}

[^] { return BAD_CHARACTER; }
