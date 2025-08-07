/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.*;

%%

%{
  public _WFFExpressionLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _WFFExpressionLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

SPACE=[ \t\n\x0B\f\r]+
NUMBER=[0-9]+(\.[0-9]+)?
ID=[:letter:][\w\\.]*
HEX_COLOR=#([:letter:]|[:digit:])*
OPERATORS=\+|-|%|\*|==|=|>=|<=|>|<|\||\|\||&&|&|\~|\!|\!=|"/"
QUOTED_STRING=('([^'\\]|\\.)*'|\"([^\"\\]|\\.)*\")

%%
<YYINITIAL> {
  {WHITE_SPACE}         { return WHITE_SPACE; }

  "("                   { return OPEN_PAREN; }
  ")"                   { return CLOSE_PAREN; }
  "["                   { return OPEN_BRACKET; }
  "]"                   { return CLOSE_BRACKET; }
  ","                   { return COMMA; }
  "?"                   { return QUESTION_MARK; }
  ":"                   { return COLON; }
  "null"                { return NULL; }

  {SPACE}               { return SPACE; }
  {NUMBER}              { return NUMBER; }
  {ID}                  { return ID; }
  {HEX_COLOR}           { return HEX_COLOR; }
  {OPERATORS}           { return OPERATORS; }
  {QUOTED_STRING}       { return QUOTED_STRING; }

}

[^] { return BAD_CHARACTER; }
