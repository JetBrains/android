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

import com.google.common.collect.ImmutableSet;

/** A TokenKind is an enumeration of each different kind of lexical symbol. */
public enum TokenKind {
  ASSERT("assert"),
  AND("and"),
  AS("as"),
  BREAK("break"),
  CLASS("class"),
  COLON(":"),
  COMMA(","),
  COMMENT("comment"),
  CONTINUE("continue"),
  DEF("def"),
  DEL("del"),
  DOT("."),
  ELIF("elif"),
  ELSE("else"),
  EOF("EOF"),
  EQUALS("="),
  EQUALS_EQUALS("=="),
  EXCEPT("except"),
  FINALLY("finally"),
  FOR("for"),
  FROM("from"),
  GLOBAL("global"),
  GREATER(">"),
  GREATER_EQUALS(">="),
  IDENTIFIER("identifier"),
  IF("if"),
  ILLEGAL("illegal character"),
  IMPORT("import"),
  IN("in"),
  INDENT("indent"),
  INT("integer"),
  IS("is"),
  LAMBDA("lambda"),
  LBRACE("{"),
  LBRACKET("["),
  LESS("<"),
  LESS_EQUALS("<="),
  LOAD("load"),
  LPAREN("("),
  MINUS("-"),
  NEWLINE("newline"),
  NONLOCAL("nonlocal"),
  NOT("not"),
  NOT_EQUALS("!="),
  NOT_IN("not in"), // used internally by the parser; not directly created by the lexer
  OR("or"),
  DEDENT("dedent"),
  PASS("pass"),
  PERCENT("%"),
  PIPE("|"),
  PLUS("+"),
  PLUS_EQUALS("+="),
  MINUS_EQUALS("-="),
  STAR_EQUALS("*="),
  SLASH_EQUALS("/="),
  SLASH_SLASH_EQUALS("//="),
  PERCENT_EQUALS("%="),
  RAISE("raise"),
  RBRACE("}"),
  RBRACKET("]"),
  RETURN("return"),
  RPAREN(")"),
  SEMI(";"),
  SLASH("/"),
  SLASH_SLASH("//"),
  STAR("*"),
  STAR_STAR("**"),
  STRING("string"),
  TRY("try"),
  WHILE("while"),
  WITH("with"),
  YIELD("yield"),
  // We need to tokenize all characters.
  // Whitespace will be used for all tokens which should be ignored by the parser.
  WHITESPACE("whitespace");

  private final String name;

  TokenKind(String name) {
    this.name = name;
  }

  /**
   * This is a user-friendly name. For keywords (if, yield, True, etc.), it's also the exact
   * character sequence used by the lexer.
   */
  @Override
  public String toString() {
    return name;
  }

  public static final ImmutableSet<TokenKind> KEYWORDS =
      ImmutableSet.of(
          AND, AS, ASSERT, BREAK, CLASS, CONTINUE, DEF, DEL, ELIF, ELSE, EXCEPT, FINALLY, FOR, FROM,
          GLOBAL, IF, IMPORT, IN, IS, LAMBDA, NONLOCAL, NOT, OR, PASS, RAISE, RETURN, TRY, WHILE,
          WITH, YIELD);

  public static final ImmutableSet<TokenKind> OPERATIONS =
      ImmutableSet.of(
          AND,
          EQUALS_EQUALS,
          GREATER,
          GREATER_EQUALS,
          IN,
          LESS,
          LESS_EQUALS,
          MINUS,
          NOT_EQUALS,
          NOT_IN,
          OR,
          PERCENT,
          SLASH,
          SLASH_SLASH,
          PLUS,
          PIPE,
          STAR);

  public static final ImmutableSet<TokenKind> AUGMENTED_ASSIGNMENT_OPS =
      ImmutableSet.of(
          PLUS_EQUALS, MINUS_EQUALS, STAR_EQUALS, SLASH_EQUALS, SLASH_SLASH_EQUALS, PERCENT_EQUALS);
}
