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

WS=[ \t\n\x0B\f\r]+
INT_T=[0-9]+
DEVICE_ID_T=[:letter:]+[a-zA-Z_0-9]*

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
  "id"               { return ID_KEYWORD; }
  "landscape"        { return LANDSCAPE_KEYWORD; }
  "portrait"         { return PORTRAIT_KEYWORD; }
  "square"           { return SQUARE_KEYWORD; }
  "name"             { return NAME_PARAM_KEYWORD; }
  "width"            { return WIDTH_PARAM_KEYWORD; }
  "height"           { return HEIGHT_PARAM_KEYWORD; }
  "parent"           { return PARENT_PARAM_KEYWORD; }
  "orientation"      { return ORIENTATION_PARAM_KEYWORD; }
  "isRound"          { return IS_ROUND_PARAM_KEYWORD; }
  "chinSize"         { return CHIN_SIZE_PARAM_KEYWORD; }
  "dpi"              { return DPI_PARAM_KEYWORD; }
  "boolean"          { return BOOLEAN; }

  {WS}               { return WS; }
  {INT_T}            { return INT_T; }
  {DEVICE_ID_T}      { return DEVICE_ID_T; }

}

[^] { return BAD_CHARACTER; }
