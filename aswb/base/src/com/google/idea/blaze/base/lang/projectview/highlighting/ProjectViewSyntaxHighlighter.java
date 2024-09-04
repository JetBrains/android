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
package com.google.idea.blaze.base.lang.projectview.highlighting;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.IDENTIFIER;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.LINE_COMMENT;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.SEMICOLON;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewLexer;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import java.util.Map;

/**
 * This class maps tokens to highlighting attributes. Each attribute contains the font properties.
 */
public class ProjectViewSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final Map<IElementType, TextAttributesKey> keys =
      ImmutableMap.of(
          ProjectViewTokenType.COMMENT, LINE_COMMENT,
          ProjectViewTokenType.COLON, SEMICOLON,
          ProjectViewTokenType.IDENTIFIER, IDENTIFIER,
          ProjectViewTokenType.LIST_KEYWORD, KEYWORD,
          ProjectViewTokenType.SCALAR_KEYWORD, KEYWORD);

  @Override
  public Lexer getHighlightingLexer() {
    return new ProjectViewLexer();
  }

  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType iElementType) {
    return pack(keys.get(iElementType));
  }
}
