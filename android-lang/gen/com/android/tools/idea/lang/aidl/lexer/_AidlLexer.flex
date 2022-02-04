package com.android.tools.idea.lang.aidl.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes.*;

%%

%{
  public _AidlLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _AidlLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

SPACE=[ \t\n\x0B\f\r]+
COMMENT="//"[^\r\n]*
BLOCK_COMMENT="/"[*][^*]*[*]+([^/*][^*]*[*]+)*"/"
INTVALUE=[0-9]+[lL]?(u8)?
HEXVALUE=0[xX][\da-fA-F]+[lL]?(u8)?
FLOATVALUE=[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?f?
CHARVALUE='.'
C_STR=\"([^\"]|\\.)*\"
IDENTIFIER=[_a-zA-Z][a-zA-Z_0-9]*

%%
<YYINITIAL> {
  {WHITE_SPACE}        { return WHITE_SPACE; }

  "parcelable"         { return PARCELABLE_KEYWORD; }
  "import"             { return IMPORT_KEYWORD; }
  "package"            { return PACKAGE_KEYWORD; }
  "in"                 { return IN_KEYWORD; }
  "out"                { return OUT_KEYWORD; }
  "inout"              { return INOUT_KEYWORD; }
  "cpp_header"         { return CPP_HEADER_KEYWORD; }
  "const"              { return CONST_KEYWORD; }
  "true"               { return TRUE_KEYWORD; }
  "false"              { return FALSE_KEYWORD; }
  "interface"          { return INTERFACE_KEYWORD; }
  "oneway"             { return ONEWAY_KEYWORD; }
  "enum"               { return ENUM_KEYWORD; }
  "union"              { return UNION_KEYWORD; }
  "("                  { return LPAREN; }
  ")"                  { return RPAREN; }
  "<"                  { return LT; }
  ">"                  { return GT; }
  "{"                  { return LBRACE; }
  "}"                  { return RBRACE; }
  "["                  { return LBRACKET; }
  "]"                  { return RBRACKET; }
  ":"                  { return COLON; }
  ";"                  { return SEMICOLON; }
  ","                  { return COMMA; }
  "."                  { return DOT; }
  "="                  { return ASSIGN; }
  "+"                  { return PLUS; }
  "-"                  { return MINUS; }
  "*"                  { return MULTIPLY; }
  "/"                  { return DIVIDE; }
  "%"                  { return MODULO; }
  "&"                  { return BITWISE_AND; }
  "|"                  { return BITWISE_OR; }
  "^"                  { return BITWISE_XOR; }
  "<<"                 { return LSHIFT; }
  ">>"                 { return RSHIFT; }
  "&&"                 { return LOGICAL_AND; }
  "||"                 { return LOGICAL_OR; }
  "!"                  { return NOT; }
  "~"                  { return BITWISE_COMPLEMENT; }
  "<="                 { return LEQ; }
  ">="                 { return GEQ; }
  "=="                 { return EQUALITY; }
  "!="                 { return NEQ; }
  "void"               { return VOID_KEYWORD; }
  "boolean"            { return BOOLEAN_KEYWORD; }
  "byte"               { return BYTE_KEYWORD; }
  "char"               { return CHAR_KEYWORD; }
  "short"              { return SHORT_KEYWORD; }
  "int"                { return INT_KEYWORD; }
  "long"               { return LONG_KEYWORD; }
  "float"              { return FLOAT_KEYWORD; }
  "double"             { return DOUBLE_KEYWORD; }
  "@"                  { return AT; }

  {SPACE}              { return SPACE; }
  {COMMENT}            { return COMMENT; }
  {BLOCK_COMMENT}      { return BLOCK_COMMENT; }
  {INTVALUE}           { return INTVALUE; }
  {HEXVALUE}           { return HEXVALUE; }
  {FLOATVALUE}         { return FLOATVALUE; }
  {CHARVALUE}          { return CHARVALUE; }
  {C_STR}              { return C_STR; }
  {IDENTIFIER}         { return IDENTIFIER; }

}

[^] { return BAD_CHARACTER; }
