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

public enum TokenKind {
  BINARY_OP("binary operator"),
  COMPONENTS("vector or scalar components"),
  COMMA(","),
  COMMENT("comment"),
  DOT("."),
  IDENTIFIER("identifier"),
  ILLEGAL("illegal character"),
  KEYWORD("keyword"),
  LBRACE("{"),
  LBRACKET("["),
  LPAREN("("),
  NUMERIC("numeric"),
  NEWLINE("newline"),
  PREPROCESSOR("preprocessor directive"),
  RBRACE("}"),
  RBRACKET("]"),
  RPAREN(")"),
  SEMI(";"),
  SPECIAL("special"),
  STRING("string"),
  // Used for all tokens which should be ignored by the highlighter.
  WHITESPACE("whitespace");

  private final String name;

  TokenKind(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
