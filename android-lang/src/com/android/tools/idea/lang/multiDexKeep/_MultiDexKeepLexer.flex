package com.android.tools.idea.lang.multiDexKeep;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepPsiTypes.*;

%%

%{
  public _MultiDexKeepLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _MultiDexKeepLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\n
STRING=[a-zA-Z0-9/.]+

%%
<YYINITIAL> {
  {EOL}              { return EOL; }
  {STRING}           { return STRING; }

}

[^] { return BAD_CHARACTER; }
