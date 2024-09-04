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
package com.google.idea.blaze.base.lang.buildfile.lexer;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexerBase.LexerMode;
import com.intellij.lexer.LexerBase;
import java.util.Iterator;
import java.util.List;

/** Implementation of LexerBase using BuildLexerBase to tokenize the input. */
public class BuildLexer extends LexerBase {

  private final LexerMode mode;

  private int offsetEnd;
  private int offsetStart;
  private CharSequence buffer;
  private Iterator<Token> tokens;
  private Token currentToken;
  private int currentState;

  public BuildLexer(LexerMode mode) {
    this.mode = mode;
  }

  @Override
  public void start(CharSequence charSequence, int startOffset, int endOffset, int initialState) {
    buffer = charSequence;
    this.offsetEnd = endOffset;
    this.offsetStart = startOffset;

    BuildLexerBase lexer =
        new BuildLexerBase(charSequence.subSequence(startOffset, endOffset), initialState, mode);
    checkNoCharactersMissing(
        charSequence.subSequence(startOffset, endOffset).length(), lexer.getTokens());
    tokens = lexer.getTokens().iterator();
    currentToken = null;
    if (tokens.hasNext()) {
      currentToken = tokens.next();
    }
    currentState = lexer.getOpenParenStackDepth();
  }

  /** Temporary debugging code. We need to tokenize every character in the input string. */
  private void checkNoCharactersMissing(int totalLength, List<Token> tokens) {
    if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).right != totalLength) {
      String error =
          String.format(
              "Lengths don't match: %s instead of %s",
              tokens.get(tokens.size() - 1).right, totalLength);
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

  @Override
  public BuildToken getTokenType() {
    if (currentToken != null) {
      return BuildToken.fromKind(currentToken.kind);
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

  public TokenKind getTokenKind() {
    return currentToken.kind;
  }

  @Override
  public CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return offsetEnd;
  }
}
