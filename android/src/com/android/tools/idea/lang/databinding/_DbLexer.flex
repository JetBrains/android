package com.android.tools.idea.lang.databinding;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.*;
@SuppressWarnings("ALL")
%%

%{
  public _DbLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _DbLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL="\r"|"\n"|"\r\n"
LINE_WS=[\ \t\f]
WHITE_SPACE=({LINE_WS}|{EOL})+

// These tokens have been copied from intelliJ's java lexer.
IDENTIFIER=[:jletter:] [:jletterdigit:]*

DIGIT = [0-9]
DIGIT_OR_UNDERSCORE = [_0-9]
DIGITS = {DIGIT} | {DIGIT} {DIGIT_OR_UNDERSCORE}*
HEX_DIGIT_OR_UNDERSCORE = [_0-9A-Fa-f]

INTEGER_LITERAL = {DIGITS} | {HEX_INTEGER_LITERAL} | {BIN_INTEGER_LITERAL}
LONG_LITERAL = {INTEGER_LITERAL} [Ll]
HEX_INTEGER_LITERAL = 0 [Xx] {HEX_DIGIT_OR_UNDERSCORE}*
BIN_INTEGER_LITERAL = 0 [Bb] {DIGIT_OR_UNDERSCORE}*

FLOAT_LITERAL = ({DEC_FP_LITERAL} | {HEX_FP_LITERAL}) [Ff] | {DIGITS} [Ff]
DOUBLE_LITERAL = ({DEC_FP_LITERAL} | {HEX_FP_LITERAL}) [Dd]? | {DIGITS} [Dd]
DEC_FP_LITERAL = {DIGITS} {DEC_EXPONENT} | {DEC_SIGNIFICAND} {DEC_EXPONENT}?
DEC_SIGNIFICAND = "." {DIGITS} | {DIGITS} "." {DIGIT_OR_UNDERSCORE}*
DEC_EXPONENT = [Ee] [+-]? {DIGIT_OR_UNDERSCORE}*
HEX_FP_LITERAL = {HEX_SIGNIFICAND} {HEX_EXPONENT}
HEX_SIGNIFICAND = 0 [Xx] ({HEX_DIGIT_OR_UNDERSCORE}+ "."? | {HEX_DIGIT_OR_UNDERSCORE}* "." {HEX_DIGIT_OR_UNDERSCORE}+)
HEX_EXPONENT = [Pp] [+-]? {DIGIT_OR_UNDERSCORE}*

STRING_LITERAL=(\"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*(\"|\\)?) | (`([^\\\"\r\n`]|{ESCAPE_SEQUENCE})*(`|\\)?)
// copy ends.
CHARACTER_LITERAL="'"([^\r\n\'\\]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE})"'"

ESCAPE_SEQUENCE=\\([btnfr\"\'\\]|{OCTAL_ESCAPE})
OCTAL_ESCAPE=[0-3][0-7][0-7] | [0-7][0-7] | [0-7]
UNICODE_ESCAPE=\\u[0-9A-Fa-f]{4}

RESOURCE_REFERENCE="@" (({IDENTIFIER} | "android") ":")? {RESOURCE_TYPE} "/" {IDENTIFIER}
RESOURCE_TYPE=anim|animator|bool|color|colorStateList|dimen|dimenOffset|dimenSize|drawable|fraction|id|integer|intArray|interpolator|layout|plurals|stateListAnimator|string|stringArray|transition|typedArray

%%
<YYINITIAL> {

  {STRING_LITERAL}        { return STRING_LITERAL; }

  {WHITE_SPACE}           { return com.intellij.psi.TokenType.WHITE_SPACE; }

  {FLOAT_LITERAL}         { return FLOAT_LITERAL; }
  {LONG_LITERAL}          { return LONG_LITERAL; }
  {DOUBLE_LITERAL}        { return DOUBLE_LITERAL; }
  {INTEGER_LITERAL}       { return INTEGER_LITERAL; }
  {CHARACTER_LITERAL}     { return CHARACTER_LITERAL; }
  {RESOURCE_REFERENCE}    { return RESOURCE_REFERENCE; }

  "true"                  { return TRUE; }
  "false"                 { return FALSE; }
  "null"                  { return NULL; }
  "boolean"               { return BOOLEAN_KEYWORD; }
  "byte"                  { return BYTE_KEYWORD; }
  "char"                  { return CHAR_KEYWORD; }
  "short"                 { return SHORT_KEYWORD; }
  "int"                   { return INT_KEYWORD; }
  "long"                  { return LONG_KEYWORD; }
  "float"                 { return FLOAT_KEYWORD; }
  "double"                { return DOUBLE_KEYWORD; }
  "void"                  { return VOID_KEYWORD; }
  "class"                 { return CLASS_KEYWORD; }
  "instanceof"            { return INSTANCEOF_KEYWORD; }
  "default"               { return DEFAULT_KEYWORD; }
  "=="                    { return EQEQ; }
  "!="                    { return NE; }
  "<="                    { return LE; }
  "<<"                    { return LTLT; }
  "<"                     { return LT; }
  ">>>"                   { return GTGTGT; }
  ">>"                    { return GTGT; }
  ">="                    { return GTEQ; }
  ">"                     { return GT; }
  "="                     { return EQ; }
  "!"                     { return EXCL; }
  "~"                     { return TILDE; }
  "??"                    { return QUESTQUEST; }
  "?"                     { return QUEST; }
  ":"                     { return COLON; }
  "+"                     { return PLUS; }
  "-"                     { return MINUS; }
  "*"                     { return ASTERISK; }
  "/"                     { return DIV; }
  "&&"                    { return ANDAND; }
  "&"                     { return AND; }
  "||"                    { return OROR; }
  "|"                     { return OR; }
  "^"                     { return XOR; }
  "%"                     { return PERC; }
  "("                     { return LPARENTH; }
  ")"                     { return RPARENTH; }
  "["                     { return LBRACKET; }
  "]"                     { return RBRACKET; }
  ","                     { return COMMA; }
  "."                     { return DOT; }

  {IDENTIFIER}            { return IDENTIFIER; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
