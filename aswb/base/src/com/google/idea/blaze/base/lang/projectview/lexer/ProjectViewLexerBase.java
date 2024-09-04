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
package com.google.idea.blaze.base.lang.projectview.lexer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewKeywords;
import java.util.List;

/** Lexer for project view files. */
public class ProjectViewLexerBase {

  @VisibleForTesting
  static class Token {
    final ProjectViewTokenType type;
    final int left;
    final int right;

    private Token(ProjectViewTokenType type, int left, int right) {
      this.type = type;
      this.left = left;
      this.right = right;
    }
  }

  private final List<Token> tokens;

  // Input buffer and position
  private final char[] buffer;
  private int pos;

  private int identifierStart = -1;
  private boolean lineHasPrecedingNonWhitespaceChar = false;

  public ProjectViewLexerBase(CharSequence input) {
    this.buffer = input.toString().toCharArray();
    this.tokens = Lists.newArrayList();
    this.pos = 0;
    tokenize();
  }

  public List<Token> getTokens() {
    return tokens;
  }

  /** Performs tokenization of the character buffer of file contents provided to the constructor. */
  private void tokenize() {
    while (pos < buffer.length) {
      char c = buffer[pos];
      pos++;
      switch (c) {
        case '\n':
          addPrecedingIdentifier(pos - 1);
          tokens.add(new Token(ProjectViewTokenType.NEWLINE, pos - 1, pos));
          lineHasPrecedingNonWhitespaceChar = false;
          break;
        case ' ':
        case '\t':
        case '\r':
          addPrecedingIdentifier(pos - 1);
          handleWhitespace();
          break;
        case ':':
          addPrecedingIdentifier(pos - 1);
          tokens.add(new Token(ProjectViewTokenType.COLON, pos - 1, pos));
          break;
        case '#':
          if (!lineHasPrecedingNonWhitespaceChar) {
            addPrecedingIdentifier(pos - 1);
            addCommentLine(pos - 1);
            break;
          }
          // otherwise '#' treated as part of the identifier; intentional fall-through
        default:
          lineHasPrecedingNonWhitespaceChar = true;
          // all other characters combined into an 'identifier' lexical token
          if (identifierStart == -1) {
            identifierStart = pos - 1;
          }
      }
    }
    addPrecedingIdentifier(pos);
  }

  private void addPrecedingIdentifier(int end) {
    if (identifierStart != -1) {
      tokens.add(new Token(getIdentifierToken(identifierStart, end), identifierStart, end));
      identifierStart = -1;
    }
  }

  private void addCommentLine(int start) {
    while (pos < buffer.length) {
      char c = buffer[pos];
      if (c == '\n') {
        break;
      }
      pos++;
    }
    tokens.add(new Token(ProjectViewTokenType.COMMENT, start, pos));
  }

  /**
   * If the whitespace is followed by an end-of-line comment or a newline, it's combined with those
   * tokens.
   */
  private void handleWhitespace() {
    int oldPos = pos - 1;
    while (pos < buffer.length) {
      char c = buffer[pos];
      switch (c) {
        case ' ':
        case '\t':
        case '\r':
          pos++;
          break;
        default:
          if (lineHasPrecedingNonWhitespaceChar || c == '#' || c == '\n') {
            tokens.add(new Token(ProjectViewTokenType.WHITESPACE, oldPos, pos));
          } else {
            tokens.add(new Token(ProjectViewTokenType.INDENT, oldPos, pos));
          }
          return;
      }
    }
    tokens.add(new Token(ProjectViewTokenType.WHITESPACE, oldPos, pos));
  }

  private ProjectViewTokenType getIdentifierToken(int start, int end) {
    String string = bufferSlice(start, end);
    if (ProjectViewKeywords.LIST_KEYWORD_MAP.keySet().contains(string)) {
      return ProjectViewTokenType.LIST_KEYWORD;
    }
    if (ProjectViewKeywords.SCALAR_KEYWORD_MAP.keySet().contains(string)) {
      return ProjectViewTokenType.SCALAR_KEYWORD;
    }
    return ProjectViewTokenType.IDENTIFIER;
  }

  private String bufferSlice(int start, int end) {
    return new String(this.buffer, start, end - start);
  }
}
