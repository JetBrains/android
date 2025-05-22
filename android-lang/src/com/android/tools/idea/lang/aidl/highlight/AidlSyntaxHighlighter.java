/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.aidl.highlight;

import com.android.tools.idea.lang.aidl.lexer.AidlLexer;
import com.android.tools.idea.lang.aidl.lexer.AidlTokenTypeSets;
import com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Basic syntax highlighter that highlights the keywords and comments.
 */
public class AidlSyntaxHighlighter extends JavaFileHighlighter {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

  static {
    fillMap(ATTRIBUTES, AidlTokenTypeSets.KEY_WORDS, DefaultLanguageHighlighterColors.KEYWORD);
    fillMap(ATTRIBUTES, AidlTokenTypeSets.NUMBERS, DefaultLanguageHighlighterColors.NUMBER);
    fillMap(ATTRIBUTES, AidlTokenTypeSets.OPERATORS, DefaultLanguageHighlighterColors.OPERATION_SIGN);
    ATTRIBUTES.put(AidlTokenTypes.COMMENT, DefaultLanguageHighlighterColors.LINE_COMMENT);
    ATTRIBUTES.put(AidlTokenTypes.BLOCK_COMMENT, DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    ATTRIBUTES.put(AidlTokenTypes.C_STR, DefaultLanguageHighlighterColors.STRING);
    ATTRIBUTES.put(AidlTokenTypes.CHARVALUE, DefaultLanguageHighlighterColors.STRING);
    ATTRIBUTES.put(AidlTokenTypes.LBRACKET, DefaultLanguageHighlighterColors.BRACKETS);
    ATTRIBUTES.put(AidlTokenTypes.RBRACKET, DefaultLanguageHighlighterColors.BRACKETS);
    ATTRIBUTES.put(AidlTokenTypes.LBRACE, DefaultLanguageHighlighterColors.BRACES);
    ATTRIBUTES.put(AidlTokenTypes.RBRACE, DefaultLanguageHighlighterColors.BRACES);
    ATTRIBUTES.put(AidlTokenTypes.LPAREN, DefaultLanguageHighlighterColors.PARENTHESES);
    ATTRIBUTES.put(AidlTokenTypes.RPAREN, DefaultLanguageHighlighterColors.PARENTHESES);
    ATTRIBUTES.put(AidlTokenTypes.DOT, DefaultLanguageHighlighterColors.DOT);
    ATTRIBUTES.put(AidlTokenTypes.COMMA, DefaultLanguageHighlighterColors.COMMA);
    ATTRIBUTES.put(AidlTokenTypes.SEMICOLON, DefaultLanguageHighlighterColors.SEMICOLON);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new AidlLexer();
  }

  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
