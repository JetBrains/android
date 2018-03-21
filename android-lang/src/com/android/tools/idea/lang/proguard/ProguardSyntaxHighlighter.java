/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.lang.proguard;

import com.android.tools.idea.lang.proguard.grammar.ProguardLexer;
import com.android.tools.idea.lang.proguard.psi.ProguardTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class ProguardSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final TextAttributesKey[] EMPTY_KEY = new TextAttributesKey[0];

  private static final TextAttributesKey COMMENT = createTextAttributesKey("COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  private static final TextAttributesKey[] COMMENTS_KEY = new TextAttributesKey[]{COMMENT};

  private static final TextAttributesKey BAD_CHAR = createTextAttributesKey("BAD_CHAR", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);
  private static final TextAttributesKey[] BAD_CHARS_KEY = new TextAttributesKey[]{BAD_CHAR};

  private static final TextAttributesKey OPERATOR = createTextAttributesKey("OPERATOR", DefaultLanguageHighlighterColors.BRACES);
  private static final TextAttributesKey[] OPERATOR_KEY = new TextAttributesKey[]{OPERATOR};

  private static final TextAttributesKey FLAG_NAME = createTextAttributesKey("FLAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
  private static final TextAttributesKey[] FLAG_NAME_KEY = new TextAttributesKey[]{FLAG_NAME};

  private static final TextAttributesKey FLAG_ARG = createTextAttributesKey("FLAG_ARG", DefaultLanguageHighlighterColors.PARAMETER);
  private static final TextAttributesKey[] FLAG_ARG_KEY = new TextAttributesKey[]{FLAG_ARG};

  private static final TextAttributesKey CLASS_SPEC = createTextAttributesKey("CLASS_SPEC", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
  private static final TextAttributesKey[] CLASS_SPEC_KEY = new TextAttributesKey[]{CLASS_SPEC};

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new ProguardLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    // Return the appropriate text attributes depending on the type of token.
    if (tokenType.equals(ProguardTypes.LINE_CMT)) {
      return COMMENTS_KEY;
    }
    if (tokenType.equals(TokenType.BAD_CHARACTER)) {
      return BAD_CHARS_KEY;
    }
    if (tokenType.equals(ProguardTypes.JAVA_DECL)) {
      return CLASS_SPEC_KEY;
    }
    if (tokenType.equals(ProguardTypes.CLOSE_BRACE) || tokenType.equals(ProguardTypes.OPEN_BRACE)) {
      return OPERATOR_KEY;
    }
    if (tokenType.equals(ProguardTypes.FLAG_NAME)) {
      return FLAG_NAME_KEY;
    }
    if (tokenType.equals(ProguardTypes.FLAG_ARG)) {
      return FLAG_ARG_KEY;
    }

    return EMPTY_KEY;
  }
}
