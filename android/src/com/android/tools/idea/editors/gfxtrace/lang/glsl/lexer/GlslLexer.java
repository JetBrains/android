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
package com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public class GlslLexer extends LexerBase {

  private int offsetStart;
  private CharSequence buffer;
  private Iterator<Token> tokens;
  private Token currentToken;
  private int currentState;

  @Override
  public void start(@NotNull CharSequence charSequence, int startOffset, int endOffset, int initialState) {
    buffer = charSequence;
    this.offsetStart = startOffset;

    GlslLexerBase lexer = new GlslLexerBase(charSequence.subSequence(startOffset, endOffset), initialState);
    checkNoCharactersMissing(charSequence.subSequence(startOffset, endOffset).length(), lexer.getTokens());
    tokens = lexer.getTokens().iterator();
    currentToken = null;
    if (tokens.hasNext()) {
      currentToken = tokens.next();
    }
    currentState = lexer.getOpenParenStackDepth();
  }

  /**
   * Temporary debugging code. We need to tokenize every character in the input string.
   */
  private void checkNoCharactersMissing(int totalLength, List<Token> tokens) {
    if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).right != totalLength) {
      String error = String.format("Lengths don't match: %s instead of %s",
                                   tokens.get(tokens.size() - 1).right,
                                   totalLength);
      throw new RuntimeException(error);
    }
    int start = 0;
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.left != start) {
        throw new RuntimeException("Gap/inconsistency at: " + start);
      }
      start = token.right;
    }
  }

  @Override
  public int getState() {
    return currentState;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    if (currentToken != null) {
      return GlslToken.fromKind(currentToken.kind);
    }
    return null;
  }

  @Override
  public int getTokenStart() {
    if (currentToken == null) {
      return 0;
    }
    return currentToken.left + offsetStart;
  }

  @Override
  public int getTokenEnd() {
    if (currentToken == null) {
      return 0;
    }
    return currentToken.right + offsetStart;
  }

  @Override
  public void advance() {
    if (tokens.hasNext()) {
      currentToken = tokens.next();
    } else {
      currentToken = null;
    }
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return 0;
  }
}
