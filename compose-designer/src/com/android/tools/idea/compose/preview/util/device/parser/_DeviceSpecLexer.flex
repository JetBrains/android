package com.android.tools.idea.compose.preview.util.device.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.*;

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
STRING_T=[:letter:]+([a-zA-Z_0-9]|[ \t\n\x0B\f\r])*

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
  "unit"             { return UNIT_KEYWORD; }
  "spec"             { return SPEC_KEYWORD; }
  "id"               { return ID_KEYWORD; }
  "name"             { return NAME_KEYWORD; }
  "landscape"        { return LANDSCAPE_KEYWORD; }
  "portrait"         { return PORTRAIT_KEYWORD; }
  "square"           { return SQUARE_KEYWORD; }
  "width"            { return WIDTH_KEYWORD; }
  "height"           { return HEIGHT_KEYWORD; }
  "parent"           { return PARENT_KEYWORD; }
  "orientation"      { return ORIENTATION_KEYWORD; }
  "isRound"          { return IS_ROUND_KEYWORD; }
  "chinSize"         { return CHIN_SIZE_KEYWORD; }
  "dpi"              { return DPI_KEYWORD; }

  {NUMERIC_T}        { return NUMERIC_T; }
  {STRING_T}         { return STRING_T; }

}

[^] { return BAD_CHARACTER; }
