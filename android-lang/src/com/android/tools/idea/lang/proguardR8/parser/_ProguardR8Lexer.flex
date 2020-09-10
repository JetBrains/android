package com.android.tools.idea.lang.proguardR8.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.*;

%%

%{
  public _ProguardR8Lexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _ProguardR8Lexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

WHITE_SPACE=\s+

FLAG_TOKEN=-[a-z]+
FILE_NAME=[\w\-./<>*?]+

UNTERMINATED_SINGLE_QUOTED_STRING = '(\\'|[^'])*
SINGLE_QUOTED_STRING = {UNTERMINATED_SINGLE_QUOTED_STRING} '
UNTERMINATED_DOUBLE_QUOTED_STRING = \"(\\\"|[^\"])*
DOUBLE_QUOTED_STRING = {UNTERMINATED_DOUBLE_QUOTED_STRING} \"

LINE_CMT=#[^\n\r]*

// jletter includes all characters for which the Java function Character.isJavaIdentifierStart returns true and
// jletterdigit all characters for that Character.isJavaIdentifierPart returns true.
JAVA_IDENTIFIER=[:jletter:][:jletterdigit:]*
WILDCARD=(\?|\*{1,2}|<\d+>)
WILDCARD_FOLLOWED_BY_DIGITS_OR_LETTERS= {WILDCARD}[:jletterdigit:]+
// Like JAVA_IDENTIFIER but contain the "?" symbol (no more than one in row) and the "*" (no more than two in row).
JAVA_IDENTIFIER_WITH_WILDCARDS = {JAVA_IDENTIFIER}? (({WILDCARD_FOLLOWED_BY_DIGITS_OR_LETTERS}+{WILDCARD}?)|{WILDCARD})

%state STATE_JAVA_SECTION_HEADER
%state STATE_JAVA_SECTION_BODY
%state STATE_FLAG_ARGS
%state STATE_FILE_NAME

%%
<YYINITIAL> {
    {WHITE_SPACE}                          { return WHITE_SPACE; }
    "@"                                    { yybegin(STATE_FILE_NAME); return AT; }
    {FLAG_TOKEN}                           { yybegin(STATE_FLAG_ARGS); return FLAG_TOKEN; }
    {LINE_CMT}                             { return LINE_CMT; }
}

<STATE_FILE_NAME> {
    {FILE_NAME}                         { yybegin(YYINITIAL); return FILE_NAME; }
    {SINGLE_QUOTED_STRING}              { yybegin(YYINITIAL); return SINGLE_QUOTED_STRING; }
    {DOUBLE_QUOTED_STRING}              { yybegin(YYINITIAL); return DOUBLE_QUOTED_STRING; }
    {UNTERMINATED_SINGLE_QUOTED_STRING} { yybegin(YYINITIAL); return UNTERMINATED_SINGLE_QUOTED_STRING; }
    {UNTERMINATED_DOUBLE_QUOTED_STRING} { yybegin(YYINITIAL); return UNTERMINATED_DOUBLE_QUOTED_STRING; }
}

<STATE_FLAG_ARGS> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "!"                                    { return EM; }
  "{"                                    { yybegin(STATE_JAVA_SECTION_BODY); return OPEN_BRACE; }
  "}"                                    { return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  ";"                                    { return SEMICOLON; }
  ":"                                    { return COLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
  "**"                                   { return DOUBLE_ASTERISK; }
  "*"                                    { return ASTERISK; }
  "@"                                    { yybegin(STATE_JAVA_SECTION_HEADER); return AT; }
  "includedescriptorclasses"             { return INCLUDEDESCRIPTORCLASSES; }
  "includecode"                          { return INCLUDECODE; }
  "allowshrinking"                       { return ALLOWSHRINKING; }
  "allowoptimization"                    { return ALLOWOPTIMIZATION; }
  "allowobfuscation"                     { return ALLOWOBFUSCATION; }
  "public"                               { yybegin(STATE_JAVA_SECTION_HEADER); return PUBLIC; }
  "final"                                { yybegin(STATE_JAVA_SECTION_HEADER); return FINAL; }
  "abstract"                             { yybegin(STATE_JAVA_SECTION_HEADER); return ABSTRACT; }
  "interface"                            { yybegin(STATE_JAVA_SECTION_HEADER); return INTERFACE; }
  "class"                                { yybegin(STATE_JAVA_SECTION_HEADER); return CLASS; }
  "enum"                                 { yybegin(STATE_JAVA_SECTION_HEADER); return ENUM; }

  {FLAG_TOKEN}                        {  yypushback(yytext().length()); yybegin(YYINITIAL); }
  {FILE_NAME}                         { return FILE_NAME; }
  {SINGLE_QUOTED_STRING}              { return SINGLE_QUOTED_STRING; }
  {DOUBLE_QUOTED_STRING}              { return DOUBLE_QUOTED_STRING; }
  {UNTERMINATED_SINGLE_QUOTED_STRING} { return UNTERMINATED_SINGLE_QUOTED_STRING; }
  {UNTERMINATED_DOUBLE_QUOTED_STRING} { return UNTERMINATED_DOUBLE_QUOTED_STRING; }
  {LINE_CMT}                          { return LINE_CMT; }
}

<STATE_JAVA_SECTION_HEADER> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "{"                                    { yybegin(STATE_JAVA_SECTION_BODY); return OPEN_BRACE; }
  "!"                                    { return EM; }
  "}"                                    { yybegin(YYINITIAL); return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  ";"                                    { return SEMICOLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
  "**"                                   { return DOUBLE_ASTERISK; }
  "*"                                    { return ASTERISK; }
  "@"                                    { return AT; }

  "extends"                              { return EXTENDS; }
  "implements"                           { return IMPLEMENTS; }
  "public"                               { return PUBLIC; }
  "final"                                { return FINAL; }
  "abstract"                             { return ABSTRACT; }
  "interface"                            { return INTERFACE; }
  "class"                                { return CLASS; }
  "enum"                                 { return ENUM; }


  {SINGLE_QUOTED_STRING}                 { return SINGLE_QUOTED_CLASS; }
  {DOUBLE_QUOTED_STRING}                 { return DOUBLE_QUOTED_CLASS; }
  {UNTERMINATED_SINGLE_QUOTED_STRING}    { return UNTERMINATED_SINGLE_QUOTED_CLASS; }
  {UNTERMINATED_DOUBLE_QUOTED_STRING}    { return UNTERMINATED_DOUBLE_QUOTED_CLASS; }
  {JAVA_IDENTIFIER}                      { return JAVA_IDENTIFIER; }
  {JAVA_IDENTIFIER_WITH_WILDCARDS}       { return JAVA_IDENTIFIER_WITH_WILDCARDS; }
  {FLAG_TOKEN}                           { yypushback(yytext().length()); yybegin(YYINITIAL);}
  {LINE_CMT}                             { return LINE_CMT; }
}

<STATE_JAVA_SECTION_BODY> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "{"                                    { return OPEN_BRACE; }
  "!"                                    { return EM; }
  "}"                                    { yybegin(YYINITIAL); return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  ";"                                    { return SEMICOLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
  "**"                                   { return DOUBLE_ASTERISK; }
  "*"                                    { return ASTERISK; }
  "@"                                    { return AT; }

  "***"                                  { return ANY_TYPE_; }
  "..."                                  { return ANY_TYPE_AND_NUM_OF_ARGS; }
  "%"                                    { return ANY_PRIMITIVE_TYPE_; }
  "["                                    { return OPEN_BRACKET; }
  "]"                                    { return CLOSE_BRACKET; }
  "boolean"                              { return BOOLEAN; }
  "byte"                                 { return BYTE; }
  "char"                                 { return CHAR; }
  "short"                                { return SHORT; }
  "int"                                  { return INT; }
  "long"                                 { return LONG; }
  "float"                                { return FLOAT; }
  "double"                               { return DOUBLE; }
  "void"                                 { return VOID; }
  "<fields>"                             { return _FIELDS_; }
  "private"                              { return PRIVATE; }
  "protected"                            { return PROTECTED; }
  "static"                               { return STATIC; }
  "volatile"                             { return VOLATILE; }
  "transient"                            { return TRANSIENT; }
  "<init>"                               { return _INIT_; }
  "<clinit>"                             { return _CLINIT_; }
  "return"                               { return RETURN; }
  "synchronized"                         { return SYNCHRONIZED; }
  "native"                               { return NATIVE; }
  "strictfp"                             { return STRICTFP; }
  "synthetic"                            { return SYNTHETIC; }
  "<methods>"                            { return _METHODS_; }
  "public"                               { return PUBLIC; }
  "final"                                { return FINAL; }
  "abstract"                             { return ABSTRACT; }

  {SINGLE_QUOTED_STRING}                 { return SINGLE_QUOTED_CLASS; }
  {DOUBLE_QUOTED_STRING}                 { return DOUBLE_QUOTED_CLASS; }
  {UNTERMINATED_SINGLE_QUOTED_STRING}    { return UNTERMINATED_SINGLE_QUOTED_CLASS; }
  {UNTERMINATED_DOUBLE_QUOTED_STRING}    { return UNTERMINATED_DOUBLE_QUOTED_CLASS; }
  {JAVA_IDENTIFIER}                      { return JAVA_IDENTIFIER; }
  {JAVA_IDENTIFIER_WITH_WILDCARDS}       { return JAVA_IDENTIFIER_WITH_WILDCARDS; }
  {LINE_CMT}                             { return LINE_CMT; }
}


[^]  { return BAD_CHARACTER; }
