package com.android.tools.idea.lang.aidl.lexer;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
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
%unicode 2.0

EOL="\r"|"\n"|"\r\n"
LINE_WS=[\ \t\f]
WHITE_SPACE=({LINE_WS}|{EOL})+

COMMENT="//"[^\r\n]*
BLOCK_COMMENT=[/][*][^*]*[*]+([^/*][^*]*[*]+)*[/]
IDVALUE=(0|[1-9][0-9]*)
IDENTIFIER=[_a-zA-Z][_a-zA-Z0-9]*

%%
<YYINITIAL> {
  {WHITE_SPACE}        { return com.intellij.psi.TokenType.WHITE_SPACE; }

  "import"             { return IMPORT_KEYWORD; }
  "package"            { return PACKAGE_KEYWORD; }
  "parcelable"         { return PARCELABLE_KEYWORD; }
  "interface"          { return INTERFACE_KEYWORD; }
  "flattenable"        { return FLATTENABLE_KEYWORD; }
  "rpc"                { return RPC_KEYWORD; }
  "in"                 { return IN_KEYWORD; }
  "out"                { return OUT_KEYWORD; }
  "inout"              { return INOUT_KEYWORD; }
  "oneway"             { return ONEWAY_KEYWORD; }
  "void"               { return VOID_KEYWORD; }
  "{"                  { return LCURLY; }
  "}"                  { return RCURLY; }
  "("                  { return LPARENTH; }
  ")"                  { return RPARENTH; }
  "["                  { return LBRACKET; }
  "]"                  { return RBRACKET; }
  ","                  { return COMMA; }
  "="                  { return EQUALS; }
  ";"                  { return SEMICOLON; }
  "<"                  { return LT; }
  ">"                  { return GT; }
  "boolean"            { return BOOLEAN_KEYWORD; }
  "byte"               { return BYTE_KEYWORD; }
  "char"               { return CHAR_KEYWORD; }
  "short"              { return SHORT_KEYWORD; }
  "int"                { return INT_KEYWORD; }
  "long"               { return LONG_KEYWORD; }
  "float"              { return FLOAT_KEYWORD; }
  "double"             { return DOUBLE_KEYWORD; }
  "ONEWAY"             { return ONEWAY; }

  {COMMENT}            { return COMMENT; }
  {BLOCK_COMMENT}      { return BLOCK_COMMENT; }
  {IDVALUE}            { return IDVALUE; }
  {IDENTIFIER}         { return IDENTIFIER; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
