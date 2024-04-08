package com.android.tools.idea.gradle.something.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.*;

%%

%{
  public _SomethingLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _SomethingLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT="//".*
NUMBER=[0-9]([0-9]|_+[0-9])*[Ll]?
STRING=\"([^\"\r\n\\]|\\[^\r\n])*\"?
BOOLEAN=(true|false)
TOKEN=[a-z][a-zA-Z0-9]*

%%
<YYINITIAL> {
  {WHITE_SPACE}        { return WHITE_SPACE; }

  "="                  { return OP_EQ; }
  "."                  { return OP_DOT; }
  "{"                  { return OP_LBRACE; }
  "}"                  { return OP_RBRACE; }
  "("                  { return OP_LPAREN; }
  ")"                  { return OP_RPAREN; }
  ","                  { return OP_COMMA; }
  "null"               { return NULL; }

  {LINE_COMMENT}       { return LINE_COMMENT; }
  {NUMBER}             { return NUMBER; }
  {STRING}             { return STRING; }
  {BOOLEAN}            { return BOOLEAN; }
  {TOKEN}              { return TOKEN; }

}

[^] { return BAD_CHARACTER; }
