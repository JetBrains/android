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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic syntax highlighter that highlights the keywords and comments.
 */
public class AidlSyntaxHighlighter extends JavaFileHighlighter {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

  static {
    fillMap(ATTRIBUTES, AidlTokenTypeSets.KEY_WORDS, DefaultLanguageHighlighterColors.KEYWORD);
    ATTRIBUTES.put(AidlTokenTypes.COMMENT, DefaultLanguageHighlighterColors.LINE_COMMENT);
    ATTRIBUTES.put(AidlTokenTypes.BLOCK_COMMENT, DefaultLanguageHighlighterColors.BLOCK_COMMENT);
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
