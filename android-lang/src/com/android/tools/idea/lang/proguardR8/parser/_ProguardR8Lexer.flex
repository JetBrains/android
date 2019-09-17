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

FLAG=-[a-z]+
FILE_NAME=[\w\-./<>*?]+
FILE_NAME_SINGLE_QUOTED='([\w\-./<>*?\s()])*'
FILE_NAME_DOUBLE_QUOTED=\"([\w\-./<>*?\s()])*\"

UNTERMINATED_FILE_NAME_SINGLE_QUOTED='([\w\-./<>*?\s()])*
UNTERMINATED_FILE_NAME_DOUBLE_QUOTED=\"([\w\-./<>*?\s()])*
LINE_CMT=#[^\n\r]*

// jletter includes all characters for which the Java function Character.isJavaIdentifierStart returns true and
// jletterdigit all characters for that Character.isJavaIdentifierPart returns true.
// We exclude the $ symbol beacause we are using it to separate inner classes
JAVA_LETTER = [[:jletter:]&&[^$]]
JAVA_DIGIT = [[:jletterdigit:]&&[^$]]
JAVA_IDENTIFIER={JAVA_LETTER}{JAVA_DIGIT}*
WILDCARD=(\?|\*{1,2})
WILDCARD_FOLLOWED_BY_DIGITS_OR_LETTERS= {WILDCARD}{JAVA_DIGIT}+
// Like JAVA_IDENTIFIER but contain the "?" symbol (no more than one in row) and the "*" (no more than two in row).
JAVA_IDENTIFIER_WITH_WILDCARDS = {JAVA_IDENTIFIER}? (({WILDCARD_FOLLOWED_BY_DIGITS_OR_LETTERS}+{WILDCARD}?)|{WILDCARD})

%state STATE_JAVA_SECTION_HEADER
%state STATE_JAVA_SECTION_BODY

%%
<YYINITIAL> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "!"                                    { return EM; }
  "{"                                    { yybegin(STATE_JAVA_SECTION_BODY); return OPEN_BRACE; }
  "}"                                    { return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  "$"                                    { return DOLLAR; }
  ";"                                    { return SEMICOLON; }
  ":"                                    { return COLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
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

  {FLAG}                                 { return FLAG; }
  {FILE_NAME}                            { return FILE_NAME; }
  {FILE_NAME_SINGLE_QUOTED}              { return FILE_NAME_SINGLE_QUOTED; }
  {FILE_NAME_DOUBLE_QUOTED}              { return FILE_NAME_DOUBLE_QUOTED; }
  {UNTERMINATED_FILE_NAME_SINGLE_QUOTED} { return FILE_NAME_SINGLE_QUOTED; }
  {UNTERMINATED_FILE_NAME_DOUBLE_QUOTED} { return FILE_NAME_DOUBLE_QUOTED; }
  {LINE_CMT}                             { return LINE_CMT; }
}

<STATE_JAVA_SECTION_HEADER> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "{"                                    { yybegin(STATE_JAVA_SECTION_BODY); return OPEN_BRACE; }
  "!"                                    { return EM; }
  "}"                                    { yybegin(YYINITIAL); return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  "$"                                    { return DOLLAR; }
  ";"                                    { return SEMICOLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
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


  {JAVA_IDENTIFIER}                      { return JAVA_IDENTIFIER; }
  {JAVA_IDENTIFIER_WITH_WILDCARDS}       { return JAVA_IDENTIFIER_WITH_WILDCARDS; }
  {FLAG}                                 { yybegin(YYINITIAL); return FLAG; }
  {LINE_CMT}                             { return LINE_CMT; }
}

<STATE_JAVA_SECTION_BODY> {
  {WHITE_SPACE}                          { return WHITE_SPACE; }

  "{"                                    { return OPEN_BRACE; }
  "!"                                    { return EM; }
  "}"                                    { yybegin(YYINITIAL); return CLOSE_BRACE; }
  "("                                    { return LPAREN; }
  ")"                                    { return RPAREN; }
  "$"                                    { return DOLLAR; }
  ";"                                    { return SEMICOLON; }
  ","                                    { return COMMA; }
  "."                                    { return DOT; }
  "*"                                    { return ASTERISK; }
  "@"                                    { return AT; }

  "***"                                  { return ANY_TYPE_; }
  "..."                                  { return ANY_TYPE_AND_NUM_OF_ARGS; }
  "%"                                    { return ANY_PRIMITIVE_TYPE_; }
  "[]"                                   { return ARRAY; }
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
  "values"                               { return VALUES; }
  "synchronized"                         { return SYNCHRONIZED; }
  "native"                               { return NATIVE; }
  "strictfp"                             { return STRICTFP; }
  "<methods>"                            { return _METHODS_; }
  "public"                               { return PUBLIC; }
  "final"                                { return FINAL; }
  "abstract"                             { return ABSTRACT; }

  {JAVA_IDENTIFIER}                      { return JAVA_IDENTIFIER; }
  {JAVA_IDENTIFIER_WITH_WILDCARDS}       { return JAVA_IDENTIFIER_WITH_WILDCARDS; }
  {LINE_CMT}                             { return LINE_CMT; }
}


[^]  { return BAD_CHARACTER; }
