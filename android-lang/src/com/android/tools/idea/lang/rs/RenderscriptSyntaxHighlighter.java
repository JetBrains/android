/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.lang.rs;

import com.google.common.collect.Maps;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RenderscriptSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> sAttributes =
    Maps.newHashMapWithExpectedSize(20);

  private static final TokenSet ourKeywords = TokenSet.create(RenderscriptTokenType.KEYWORD);
  private static final TokenSet ourBraces = TokenSet.create(RenderscriptTokenType.BRACE);
  private static final TokenSet ourSeparators = TokenSet.create(RenderscriptTokenType.SEPARATOR);
  private static final TokenSet ourOperators = TokenSet.create(RenderscriptTokenType.OPERATOR);
  private static final TokenSet ourComments = TokenSet.create(RenderscriptTokenType.COMMENT);
  private static final TokenSet ourString = TokenSet.create(RenderscriptTokenType.STRING);
  private static final TokenSet ourCharacter = TokenSet.create(RenderscriptTokenType.CHARACTER,
                                                             RenderscriptTokenType.NUMBER);
  private static final TokenSet ourError = TokenSet.create(RenderscriptTokenType.UNKNOWN);

  static {
    fillMap(sAttributes, ourKeywords, JavaHighlightingColors.KEYWORD);
    fillMap(sAttributes, ourBraces, JavaHighlightingColors.BRACES);
    fillMap(sAttributes, ourSeparators, JavaHighlightingColors.JAVA_SEMICOLON);
    fillMap(sAttributes, ourOperators, JavaHighlightingColors.OPERATION_SIGN);
    fillMap(sAttributes, ourComments, JavaHighlightingColors.JAVA_BLOCK_COMMENT);
    fillMap(sAttributes, ourString, JavaHighlightingColors.STRING);
    fillMap(sAttributes, ourCharacter, JavaHighlightingColors.NUMBER);
    fillMap(sAttributes, ourError, CodeInsightColors.ERRORS_ATTRIBUTES);
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new RenderscriptLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(sAttributes.get(tokenType));
  }
}
