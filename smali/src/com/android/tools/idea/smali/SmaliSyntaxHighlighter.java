/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.smali;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.smali.SmaliHighlighterColors.*;
import static com.android.tools.idea.smali.SmaliTokenSets.*;
import static com.android.tools.idea.smali.psi.SmaliTypes.JAVA_IDENTIFIER;

public class SmaliSyntaxHighlighter extends SyntaxHighlighterBase {
  static final TextAttributesKey[] COMMENT_ATTR_KEYS = new TextAttributesKey[]{COMMENT_ATTR_KEY};
  static final TextAttributesKey[] JAVA_IDENTIFIER_ATTR_KEYS = new TextAttributesKey[]{JAVA_IDENTIFIER_ATTR_KEY};
  static final TextAttributesKey[] KEYWORD_ATTR_KEYS = new TextAttributesKey[]{KEYWORD_ATTR_KEY};
  static final TextAttributesKey[] STRING_ATTR_KEYS = new TextAttributesKey[]{STRING_ATTR_KEY};
  static final TextAttributesKey[] NUMBER_ATTR_KEYS = new TextAttributesKey[]{NUMBER_ATTR_KEY};
  static final TextAttributesKey[] BRACES_ATTR_KEYS = new TextAttributesKey[]{BRACES_ATTR_KEY};
  static final TextAttributesKey[] PARENTHESES_ATTR_KEYS = new TextAttributesKey[]{PARENTHESES_ATTR_KEY};
  static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new SmaliLexerAdapter();
  }

  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    if (KEYWORD_TOKENS.contains(tokenType) || ACCESS_MODIFIER_TOKENS.contains(tokenType)) {
      return KEYWORD_ATTR_KEYS;
    }
    if (COMMENT_TOKENS.contains(tokenType)) {
      return COMMENT_ATTR_KEYS;
    }
    if (tokenType.equals(JAVA_IDENTIFIER)) {
      return JAVA_IDENTIFIER_ATTR_KEYS;
    }
    if (STRING_TOKENS.contains(tokenType)) {
      return STRING_ATTR_KEYS;
    }
    if (NUMBER_TOKENS.contains(tokenType)) {
      return NUMBER_ATTR_KEYS;
    }
    if (BRACES_TOKENS.contains(tokenType)) {
      return BRACES_ATTR_KEYS;
    }
    if (PARENTHESES_TOKENS.contains(tokenType)) {
      return PARENTHESES_ATTR_KEYS;
    }
    return EMPTY_KEYS;
  }
}
