/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.highlighting;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.BRACES;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.BRACKETS;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.COMMA;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.DOT;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.FUNCTION_DECLARATION;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.LINE_COMMENT;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.NUMBER;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.OPERATION_SIGN;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.PARAMETER;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.PARENTHESES;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.SEMICOLON;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.STRING;

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexer;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexerBase;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import java.util.Map;

/**
 * This class maps tokens to highlighting attributes. Each attribute contains the font properties.
 */
public class BuildSyntaxHighlighter extends SyntaxHighlighterBase {

  public static final TextAttributesKey BUILD_KEYWORD = key("BUILD.MODIFIER", KEYWORD);
  public static final TextAttributesKey BUILD_STRING = key("BUILD.STRING", STRING);
  public static final TextAttributesKey BUILD_NUMBER = key("BUILD.NUMBER", NUMBER);
  public static final TextAttributesKey BUILD_LINE_COMMENT =
      key("BUILD.LINE_COMMENT", LINE_COMMENT);
  public static final TextAttributesKey BUILD_BRACES = key("BUILD.BRACES", BRACES);
  public static final TextAttributesKey BUILD_PARENS = key("BUILD.PARENS", PARENTHESES);
  public static final TextAttributesKey BUILD_BRACKETS = key("BUILD.BRACKETS", BRACKETS);
  public static final TextAttributesKey BUILD_OPERATION_SIGN =
      key("BUILD.OPERATION_SIGN", OPERATION_SIGN);
  public static final TextAttributesKey BUILD_DOT = key("BUILD.DOT", DOT);
  public static final TextAttributesKey BUILD_SEMICOLON = key("BUILD.SEMICOLON", SEMICOLON);
  public static final TextAttributesKey BUILD_COMMA = key("BUILD.COMMA", COMMA);
  public static final TextAttributesKey BUILD_PARAMETER = key("BUILD.PARAMETER", PARAMETER);
  public static final TextAttributesKey BUILD_KEYWORD_ARG = key("BUILD.KEYWORD.ARG", PARAMETER);
  public static final TextAttributesKey BUILD_FN_DEFINITION =
      key("BUILD.FN.DEFINITION", FUNCTION_DECLARATION);
  public static final TextAttributesKey BUILD_BUILTIN_NAME =
      TextAttributesKey.createTextAttributesKey("BUILD.BUILTIN_NAME", PREDEFINED_SYMBOL);

  private static TextAttributesKey key(String name, TextAttributesKey fallbackKey) {
    return TextAttributesKey.createTextAttributesKey(name, fallbackKey);
  }

  private static final Map<IElementType, TextAttributesKey> keys = Maps.newHashMap();

  static {
    addAttribute(TokenKind.COMMENT, BUILD_LINE_COMMENT);
    addAttribute(TokenKind.INT, BUILD_NUMBER);
    addAttribute(TokenKind.STRING, BUILD_STRING);

    addAttribute(TokenKind.LBRACE, BUILD_BRACES);
    addAttribute(TokenKind.RBRACE, BUILD_BRACES);
    addAttribute(TokenKind.LBRACKET, BUILD_BRACKETS);
    addAttribute(TokenKind.RBRACKET, BUILD_BRACKETS);

    addAttribute(TokenKind.LPAREN, BUILD_PARENS);
    addAttribute(TokenKind.RPAREN, BUILD_PARENS);

    addAttribute(TokenKind.DOT, BUILD_DOT);
    addAttribute(TokenKind.SEMI, BUILD_SEMICOLON);
    addAttribute(TokenKind.COMMA, BUILD_COMMA);

    for (TokenKind kind : TokenKind.OPERATIONS) {
      addAttribute(kind, BUILD_OPERATION_SIGN);
    }

    for (TokenKind kind : TokenKind.KEYWORDS) {
      addAttribute(kind, BUILD_KEYWORD);
    }
  }

  private static void addAttribute(TokenKind kind, TextAttributesKey key) {
    keys.put(BuildToken.fromKind(kind), key);
  }

  @Override
  public Lexer getHighlightingLexer() {
    return new BuildLexer(BuildLexerBase.LexerMode.SyntaxHighlighting);
  }

  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType iElementType) {
    return pack(keys.get(iElementType));
  }
}
