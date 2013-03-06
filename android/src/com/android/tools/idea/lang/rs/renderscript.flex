/*
 * Copyright (C) 2013 The Android Open Source Project
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

package org.jetbrains.android.lang.rs;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/** A lexer for Renderscript files, generated from renderscript.flex using JFlex. */
%%

%class _RenderscriptLexer
%implements FlexLexer

%unicode

%function advance
%type IElementType
%eof{  return;
%eof}

%{
  StringBuilder stringBuilder = new StringBuilder(30);
%}

/* main character classes */
LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]

WhiteSpace = {LineTerminator} | [ \t\f]

/* comments */
Comment = {TraditionalComment} | {EndOfLineComment}
TraditionalComment = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment = "//" {InputCharacter}* {LineTerminator}?

/* identifiers */
Identifier = [:jletter:][:jletterdigit:]*

/* integer literals */
DecIntegerLiteral = 0 | [1-9][0-9]*
DecLongLiteral    = {DecIntegerLiteral} [lL]

HexIntegerLiteral = 0 [xX] 0* {HexDigit} {1,8}
HexLongLiteral    = 0 [xX] 0* {HexDigit} {1,16} [lL]
HexDigit          = [0-9a-fA-F]

OctIntegerLiteral = 0+ [1-3]? {OctDigit} {1,15}
OctLongLiteral    = 0+ 1? {OctDigit} {1,21} [lL]
OctDigit          = [0-7]

/* floating point literals */
FloatLiteral  = ({FLit1}|{FLit2}|{FLit3}) {Exponent}? [fF]
DoubleLiteral = ({FLit1}|{FLit2}|{FLit3}) {Exponent}?

FLit1    = [0-9]+ \. [0-9]*
FLit2    = \. [0-9]+
FLit3    = [0-9]+
Exponent = [eE] [+-]? [0-9]+

CharacterLiteral="'"([^\\\'\r\n]|{EscapeSequence})*("'"|\\)?
StringLiteral=\"([^\\\"\r\n]|{EscapeSequence})*(\"|\\)?
EscapeSequence=\\[^\r\n]

%%

<YYINITIAL> {
  "auto"       |
  "case"       |
  "char"       |
  "const"      |
  "break"      |
  "continue"   |
  "default"    |
  "do"         |
  "double"     |
  "else"       |
  "enum"       |
  "extern"     |
  "float"      |
  "for"        |
  "goto"       |
  "if"         |
  "inline"     |
  "int"        |
  "long"       |
  "register"   |
  "restrict"   |
  "return"     |
  "short"      |
  "signed"     |
  "sizeof"     |
  "static"     |
  "struct"     |
  "switch"     |
  "typedef"    |
  "union"      |
  "unsigned"   |
  "void"       |
  "volatile"   |
  "while"      |
  "_Bool"      |
  "_Complex"   |
  "_Imaginary"        { return RenderscriptTokenType.KEYWORD; }

  "TRUE"       |
  "FALSE"      |
  "null"              { return RenderscriptTokenType.KEYWORD; }

  "("          |
  ")"          |
  "{"          |
  "}"          |
  "["          |
  "]"                 { return RenderscriptTokenType.BRACE; }

  ";"          |
  ","          |
  "."                 { return RenderscriptTokenType.SEPARATOR; }

  "="          |
  "!"          |
  "<"          |
  ">"          |
  "~"          |
  "?"          |
  ":"          |
  "=="         |
  "<="         |
  ">="         |
  "!="         |
  "&&"         |
  "||"         |
  "++"         |
  "--"         |
  "+"          |
  "-"          |
  "*"          |
  "/"          |
  "&"          |
  "|"          |
  "^"          |
  "%"          |
  "<<"         |
  ">>"         |
  "+="         |
  "-="         |
  "*="         |
  "/="         |
  "&="         |
  "|="         |
  "^="         |
  "%="         |
  "<<="        |
  ">>="               { return RenderscriptTokenType.OPERATOR; }

  {DecIntegerLiteral} |
  {DecLongLiteral}    |
  {HexIntegerLiteral} |
  {HexLongLiteral}    |
  {OctIntegerLiteral} |
  {OctLongLiteral}    |
  {FloatLiteral}      |
  {DoubleLiteral}     |
  {DoubleLiteral}[dD] { return RenderscriptTokenType.NUMBER; }

  {Comment}           { return RenderscriptTokenType.COMMENT; }
  {WhiteSpace}        { return TokenType.WHITE_SPACE; }

  {Identifier}        { return RenderscriptTokenType.IDENTIFIER; }
  {CharacterLiteral}  { return RenderscriptTokenType.CHARACTER; }
  {StringLiteral}     { return RenderscriptTokenType.STRING; }
}

.                     { return TokenType.BAD_CHARACTER; }
<<EOF>>               { return null; }