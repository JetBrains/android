// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.lang.databinding;

import com.android.tools.idea.lang.databinding.psi.DbTokenTypes;
import com.google.common.collect.Maps;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.Map;

import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.*;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;

public class DbSyntaxHighlighter extends SyntaxHighlighterBase {

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new FlexAdapter(new _DbLexer((Reader)null));
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(sAttributes.get(tokenType));
  }

  private static final Map<IElementType, TextAttributesKey> sAttributes = Maps.newHashMapWithExpectedSize(50);

  static {
    final TokenSet keywords = TokenSet
      .create(BOOLEAN_KEYWORD, BYTE_KEYWORD, CHAR_KEYWORD, SHORT_KEYWORD, INT_KEYWORD, LONG_KEYWORD, FLOAT_KEYWORD, DOUBLE_KEYWORD,
              VOID_KEYWORD, CLASS_KEYWORD, INSTANCEOF_KEYWORD, DEFAULT_KEYWORD, TRUE, FALSE, NULL);
    final TokenSet brackets = TokenSet.create(LBRACKET, RBRACKET);
    final TokenSet parentheses = TokenSet.create(LPARENTH, RPARENTH);
    final TokenSet numbers = TokenSet.create(INTEGER_LITERAL, FLOAT_LITERAL, LONG_LITERAL, DOUBLE_LITERAL);
    final TokenSet operators = TokenSet
      .create(EQEQ, NE, LE, LTLT, LT, GTGTGT, GTGT, GTEQ, GT, EQ, EXCL, TILDE, QUESTQUEST, QUEST, COLON, PLUS, MINUS, ASTERISK, DIV,
              ANDAND, AND, OROR, OR, XOR);
    final TokenSet strings = TokenSet.create(STRING_LITERAL, CHARACTER_LITERAL);

    fillMap(sAttributes, keywords, KEYWORD);
    fillMap(sAttributes, brackets, BRACKETS);
    fillMap(sAttributes, parentheses, PARENTHESES);
    fillMap(sAttributes, numbers, NUMBER);
    fillMap(sAttributes, operators, OPERATION_SIGN);
    fillMap(sAttributes, strings, STRING);
    sAttributes.put(RESOURCE_REFERENCE, MARKUP_ATTRIBUTE);
    sAttributes.put(DbTokenTypes.IDENTIFIER, DefaultLanguageHighlighterColors.IDENTIFIER);
    sAttributes.put(DbTokenTypes.DOT, DefaultLanguageHighlighterColors.DOT);
    sAttributes.put(DbTokenTypes.COMMA, DefaultLanguageHighlighterColors.COMMA);
  }
}
