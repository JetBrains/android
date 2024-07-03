package com.android.tools.idea.preview.util.device.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.preview.util.device.parser.DeviceSpecTypes.*;

%%

%{
  public _DeviceSpecLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _DeviceSpecLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

NUMERIC_T=[0-9]+(\.[0-9])?
STRING_T=([:letter:]|[:digit:]){1}([^:,=\"\\]|\\.)*

%state DIMENSION_PARAM_VALUE
%state STRING_PARAM
%state STRING_VALUE

// region CUSTOM STATE LOGIC
%%
<YYINITIAL> {
  {WHITE_SPACE}      { return WHITE_SPACE; }

  "true"             { return TRUE; }
  "false"            { return FALSE; }
  "px"               { return PX; }
  "dp"               { return DP; }
  ","                { return COMMA; }
  "="                { return EQUALS; }
  ":"                { return COLON; }

  "spec"             { return SPEC_KEYWORD; }

  "landscape"        { return LANDSCAPE_KEYWORD; }
  "portrait"         { return PORTRAIT_KEYWORD; }
  "square"           { return SQUARE_KEYWORD; }

  "unit"             { return UNIT_KEYWORD; }
  "orientation"      { return ORIENTATION_KEYWORD; }
  "isRound"          { return IS_ROUND_KEYWORD; }
  "dpi"              { return DPI_KEYWORD; }
  "cutout"           { return CUTOUT_KEYWORD; }
  "navigation"       { return NAVIGATION_KEYWORD; }
  "buttons"          { return NAV_BUTTONS_KEYWORD; }
  "gesture"          { return NAV_GESTURE_KEYWORD; }
  "none"             { return CUTOUT_NONE_KEYWORD; }
  "corner"           { return CUTOUT_CORNER_KEYWORD; }
  "double"           { return CUTOUT_DOUBLE_KEYWORD; }
  "punch_hole"       { return CUTOUT_HOLE_KEYWORD; }
  "tall"             { return CUTOUT_TALL_KEYWORD; }

  "id"               { yypushback(yylength()); yybegin(STRING_PARAM); }
  "name"             { yypushback(yylength()); yybegin(STRING_PARAM); }
  "parent"           { yypushback(yylength()); yybegin(STRING_PARAM); }

  "width"            { yypushback(yylength()); yybegin(DIMENSION_PARAM_VALUE); }
  "height"           { yypushback(yylength()); yybegin(DIMENSION_PARAM_VALUE); }
  "chinSize"         { yypushback(yylength()); yybegin(DIMENSION_PARAM_VALUE); }

  {NUMERIC_T}        { return NUMERIC_T; }
  {STRING_T}         { return STRING_T; }
}

<DIMENSION_PARAM_VALUE> {
  ","                { yypushback(yylength()); yybegin(YYINITIAL); }
  "px"               { return PX; }
  "dp"               { return DP; }
  "="                { return EQUALS; }
  "width"            { return WIDTH_KEYWORD; }
  "height"           { return HEIGHT_KEYWORD; }
  "chinSize"         { return CHIN_SIZE_KEYWORD; }

  {WHITE_SPACE}      { return WHITE_SPACE; }
  {NUMERIC_T}        { return NUMERIC_T; }
}

/**
 * Parse the parameter, there may be Whitespace
 */
<STRING_PARAM> {
  "="                { yypushback(yylength()); yybegin(STRING_VALUE); }
  ":"                { yypushback(yylength()); yybegin(STRING_VALUE); }

  "id"               { return ID_KEYWORD; }
  "name"             { return NAME_KEYWORD; }
  "parent"           { return PARENT_KEYWORD; }

  {WHITE_SPACE}      { return WHITE_SPACE; }
}

/**
 * Parse string value without considering Whitespace token
 */
<STRING_VALUE> {
  "="                { return EQUALS; }
  ":"                { return COLON; }

  ","                { yypushback(yylength()); yybegin(YYINITIAL); }

  {STRING_T}         { return STRING_T; }
}
// endregion

[^] { return BAD_CHARACTER; }
