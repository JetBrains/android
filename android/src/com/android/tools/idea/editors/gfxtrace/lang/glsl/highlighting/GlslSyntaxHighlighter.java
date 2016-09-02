/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.lang.glsl.highlighting;

import com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer.GlslLexer;
import com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer.GlslToken;
import com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer.TokenKind;
import com.google.common.collect.Maps;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;

public class GlslSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final Map<IElementType, TextAttributesKey> keys = Maps.newHashMap();

  static {
    addAttribute(TokenKind.COMMENT, LINE_COMMENT);
    addAttribute(TokenKind.NUMERIC, NUMBER);
    addAttribute(TokenKind.STRING, STRING);
    addAttribute(TokenKind.PREPROCESSOR, METADATA);

    addAttribute(TokenKind.LBRACE, BRACES);
    addAttribute(TokenKind.RBRACE, BRACES);
    addAttribute(TokenKind.LBRACKET, BRACKETS);
    addAttribute(TokenKind.RBRACKET, BRACKETS);

    addAttribute(TokenKind.LPAREN, PARENTHESES);
    addAttribute(TokenKind.RPAREN, PARENTHESES);

    addAttribute(TokenKind.DOT, DOT);
    addAttribute(TokenKind.SEMI, SEMICOLON);
    addAttribute(TokenKind.COMMA, COMMA);

    addAttribute(TokenKind.BINARY_OP, OPERATION_SIGN);
    addAttribute(TokenKind.KEYWORD, KEYWORD);
    addAttribute(TokenKind.COMPONENTS, STATIC_FIELD);
  }

  private static void addAttribute(TokenKind kind, TextAttributesKey key) {
    keys.put(GlslToken.fromKind(kind), key);
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new GlslLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys.get(tokenType));
  }
}
