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

/*
 * Defines tokens in the Logcat Filter Query Language. The language is based on the Buganizer query language specific fields can be queried
 * independently but also, a general query. For example:
 *
 *    foo bar tag: MyTag package: com.example.app
 *
 * Matches log lines that
 *
 *   TAG.contains("MyTag") && PACKAGE.contains("com.example.app") && line.contains("foo bar")
 *
 * Definitions:
 *   term: A top level entity which can either be a string value or a key-value pair
 *   key-term: A key-value term. Matches a field named by the key with the value.
 *   value-term: A top level entity representing a string. Matches the entire log line with the value.
 *
 * There are 2 types of keys. String keys can accept quoted or unquoted values while regular keys can only take an unquoted value with no
 * whitespace. String keys can also be negated and can specify a regex match:
 * String keys examples:
 *     tag: foo
 *     tag: fo\ o
 *     tag: 'foo'
 *     tag: 'fo\'o'
 *     tag: "foo"
 *     tag: "fo\"o"
 *     -tag: foo
 *     tag~: foo|bar
 *
 * Logical operations & (and), | (or) are supported as well as parenthesis.
 *
 * Implicit grouping:
 * Terms without logical operations between them are treated as an implicit AND unless they are value terms:
 *
 *   foo bar tag: MyTag -> line.contains("foo bar") && tag.contains("MyTag")
 *
 * This file is used by Grammar-Kit to generate the lexer, parser, node types and PSI classes.
 */
package com.android.tools.idea.logcat.filters.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.android.tools.idea.logcat.filters.parser.*;
import com.intellij.psi.TokenType;

%%

%class LogcatFilterLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

WHITE_SPACE=\s

COLON = ":"
MINUS = "-"
TILDE = "~"
EQUALS = "="
OR = "|"
AND = "&"
LPAREN = "("
RPAREN = ")"

UNQUOTED_VALUE      = [^'\"\s()] ([^\s():] | "\\ ")*
SINGLE_QUOTED_VALUE = ' ([^'] | \\')* '
DOUBLE_QUOTED_VALUE = \" ([^\"] | \\\")* \"
STRING_VALUE        = {UNQUOTED_VALUE} | {SINGLE_QUOTED_VALUE} | {DOUBLE_QUOTED_VALUE}

// Keys that accept quoted or unquoted strings.
TEXT_KEY
  = "line"
  | "message"
  | "package"
  | "process"
  | "tag"

// Keys that accept unquoted, non-whitespace values
KEY
  = "age"
  | "level"
  | "is"
  | "name"

%state STRING_KVALUE_STATE
%state REGEX_KVALUE_STATE
%state KVALUE_STATE

%%

<YYINITIAL> {
  {MINUS}? {TEXT_KEY} {COLON}          { yybegin(STRING_KVALUE_STATE); return LogcatFilterTypes.STRING_KEY; }
  {MINUS}? {TEXT_KEY} {EQUALS} {COLON} { yybegin(STRING_KVALUE_STATE); return LogcatFilterTypes.STRING_KEY; }
  {MINUS}? {TEXT_KEY} {TILDE} {COLON}  { yybegin(REGEX_KVALUE_STATE); return LogcatFilterTypes.REGEX_KEY; }
  {KEY} {COLON}                        { yybegin(KVALUE_STATE); return LogcatFilterTypes.KEY; }

  {OR}                                 { return LogcatFilterTypes.OR; }
  {AND}                                { return LogcatFilterTypes.AND; }
  {LPAREN}                             { return LogcatFilterTypes.LPAREN; }
  {RPAREN}                             { return LogcatFilterTypes.RPAREN; }

  {STRING_VALUE}                       { return LogcatFilterTypes.VALUE; }
}

{WHITE_SPACE}+                         { return TokenType.WHITE_SPACE; }

<STRING_KVALUE_STATE>   {STRING_VALUE} { yybegin(YYINITIAL); return LogcatFilterTypes.STRING_KVALUE; }

<REGEX_KVALUE_STATE>    {STRING_VALUE} { yybegin(YYINITIAL); return LogcatFilterTypes.REGEX_KVALUE; }

<KVALUE_STATE>  \S+                    { yybegin(YYINITIAL); return LogcatFilterTypes.KVALUE; }

[^]                                    { return TokenType.BAD_CHARACTER; }
