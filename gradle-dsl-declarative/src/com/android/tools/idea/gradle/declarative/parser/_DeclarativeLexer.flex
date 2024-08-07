package com.android.tools.idea.gradle.declarative.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.*;

%%

%{
  public _DeclarativeLexer() {
    this((java.io.Reader)null);
  }

  private int commentLevel = 0;

  private void startBlockComment() {
      commentLevel = 1;
      yybegin(IN_BLOCK_COMMENT);
  }
%}

%public
%class _DeclarativeLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT="//".*
BLOCK_COMMENT_START="/*"
BLOCK_COMMENT_END="*/"
BOOLEAN=(true|false)
NUMBER_LITERAL=([1-9]([0-9]|_)*[0-9])|[0-9]
HEX_DIGIT=[0-9a-fA-F]
HEX_LITERAL=0[xX] {HEX_DIGIT} ([0-9a-fA-F_]* {HEX_DIGIT})?
BIN_LITERAL=0[bB][0,1]([0,1_]*[0,1])?
INTEGER_LITERAL={NUMBER_LITERAL} | {HEX_LITERAL} | {BIN_LITERAL}
LONG_LITERAL=({NUMBER_LITERAL} | {HEX_LITERAL} | {BIN_LITERAL}) [lL]
UNSIGNED_LONG=({NUMBER_LITERAL} | {HEX_LITERAL} | {BIN_LITERAL}) [uU] [lL]
UNSIGNED_INTEGER=({NUMBER_LITERAL} | {HEX_LITERAL} | {BIN_LITERAL}) [uU]
LETTER=[a-zA-Z]
TOKEN=({LETTER} | "_") ({LETTER} | "_" | [0-9])* | `[^\r\n`]+`

STRING_LITERAL={MULTILINE_STRING_LITERAL} | {LINE_STRING_LITERAL}
LINE_STRING_LITERAL=\" {LINE_STRING_CONTENT}* \"?
MULTILINE_STRING_LITERAL=\"\"\" {MULTILINE_STRING_CONTENT}* (\"\"\")?
LINE_STRING_CONTENT=[^\\\"\r\n]+ | {ESCAPE_IDENTIFIER} | {UNI_CHARACTER_LITERAL}
MULTILINE_STRING_CONTENT=[^\"]|(\"[^\"])|(\"\"[^\"])
ESCAPE_IDENTIFIER=\\[tbrn\'\"\\$]
UNI_CHARACTER_LITERAL=\\u {HEX_DIGIT} {HEX_DIGIT} {HEX_DIGIT} {HEX_DIGIT}
%state IN_BLOCK_COMMENT

%%
<IN_BLOCK_COMMENT> {
  {BLOCK_COMMENT_START} { commentLevel++; }
  {BLOCK_COMMENT_END}  { if (--commentLevel == 0) { yybegin(YYINITIAL); return BLOCK_COMMENT; } }
  [^*/]+               { }
  [^]                  { }
}

<YYINITIAL> {
  {WHITE_SPACE}            { return WHITE_SPACE; }

  "="                      { return OP_EQ; }
  "."                      { return OP_DOT; }
  "{"                      { return OP_LBRACE; }
  "}"                      { return OP_RBRACE; }
  "("                      { return OP_LPAREN; }
  ")"                      { return OP_RPAREN; }
  ","                      { return OP_COMMA; }
  "BLOCK_COMMENT"          { return BLOCK_COMMENT; }
  "null"                   { return NULL; }

  {LINE_COMMENT}           { return LINE_COMMENT; }
  "/*"                     { startBlockComment(); }
  {STRING_LITERAL}         { return STRING_LITERAL; }
  {BOOLEAN}                { return BOOLEAN; }
  {TOKEN}                  { return TOKEN; }
  {INTEGER_LITERAL}        { return INTEGER_LITERAL; }
  {LONG_LITERAL}           { return LONG_LITERAL; }
  {UNSIGNED_LONG}          { return UNSIGNED_LONG; }
  {UNSIGNED_INTEGER}       { return UNSIGNED_INTEGER; }
}

[^] { return BAD_CHARACTER; }
