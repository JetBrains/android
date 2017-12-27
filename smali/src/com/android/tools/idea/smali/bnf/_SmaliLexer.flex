package com.android.tools.idea.smali.bnf;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.intellij.grammar.psi.BnfTypes;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.smali.psi.SmaliTypes.*;

%%

%{
  public _SmaliLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _SmaliLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

COMMENT=#.*
JAVA_IDENTIFIER=L[_a-zA-Z][_a-zA-Z0-9/$]*;
IDENTIFIER=[_a-zA-Z][_a-zA-Z0-9]*
CONSTRUCTOR_INIT=<[_a-zA-Z][_a-zA-Z0-9]*\>
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?
REGULAR_NUMBER=-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]*)?(f)?
HEX_NUMBER=0[xX][0-9a-fA-F]+(L|s|t)?
CHAR='\\u[0-9][0-9]*'

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

  ".class"                    { return DOT_CLASS; }
  ".super"                    { return DOT_SUPER; }
  ".source"                   { return DOT_SOURCE; }
  ".field"                    { return DOT_FIELD; }
  ".method"                   { return DOT_METHOD; }
  ".end method"               { return DOT_METHOD_END; }
  ".annotation"               { return DOT_ANNOTATION; }
  ".end annotation"           { return DOT_ANNOTATION_END; }
  ".implements"               { return DOT_IMPLEMENTS; }
  ".registers"                { return DOT_REGISTERS; }
  ".param"                    { return DOT_PARAM; }
  ".prologue"                 { return DOT_PROLOGUE; }
  ".line"                     { return DOT_LINE; }
  "true"                      { return TRUE; }
  "false"                     { return FALSE; }
  "{"                         { return L_CURLY; }
  "}"                         { return R_CURLY; }
  "("                         { return L_PARENTHESIS; }
  ")"                         { return R_PARENTHESIS; }
  "public"                    { return AM_PUBLIC; }
  "private"                   { return AM_PRIVATE; }
  "protected"                 { return AM_PROTECTED; }
  "static"                    { return AM_STATIC; }
  "final"                     { return AM_FINAL; }
  "synchronized"              { return AM_SYNCHRONIZED; }
  "volatile"                  { return AM_VOLATILE; }
  "transient"                 { return AM_TRANSIENT; }
  "native"                    { return AM_NATIVE; }
  "interface"                 { return AM_INTERFACE; }
  "abstract"                  { return AM_ABSTRACT; }
  "bridge"                    { return AM_BRIDGE; }
  "synthetic"                 { return AM_SYNTHETIC; }

  {COMMENT}                   { return COMMENT; }
  {JAVA_IDENTIFIER}           { return JAVA_IDENTIFIER; }
  {IDENTIFIER}                { return IDENTIFIER; }
  {CONSTRUCTOR_INIT}          { return CONSTRUCTOR_INIT; }
  {DOUBLE_QUOTED_STRING}      { return DOUBLE_QUOTED_STRING; }
  {REGULAR_NUMBER}            { return REGULAR_NUMBER; }
  {HEX_NUMBER}                { return HEX_NUMBER; }
  {CHAR}                      { return CHAR; }

}

[^] { return BAD_CHARACTER; }
