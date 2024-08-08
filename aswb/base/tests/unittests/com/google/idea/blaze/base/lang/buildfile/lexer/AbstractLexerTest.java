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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

/** Tests of tokenization behavior of {@link BuildLexerBase}. */
@Ignore
public abstract class AbstractLexerTest {

  private final BuildLexerBase.LexerMode mode;
  protected String lastError;

  protected AbstractLexerTest(BuildLexerBase.LexerMode mode) {
    this.mode = mode;
  }

  /**
   * Create a lexer which takes input from the specified string. Resets the error handler
   * beforehand.
   */
  protected BuildLexerBase createLexer(String input) {
    lastError = null;
    return new BuildLexerBase(input, 0, mode) {
      @Override
      protected void error(String message, int start, int end) {
        super.error(message, start, end);
        lastError = message;
      }
    };
  }

  protected Token[] tokens(String input) {
    Token[] tokens = createLexer(input).getTokens().toArray(new Token[0]);
    assertNoCharactersMissing(input.length(), tokens);
    return tokens;
  }

  /**
   * Both the syntax highlighter and the parser require every character be accounted for by a
   * lexical element.
   */
  private static void assertNoCharactersMissing(int totalLength, Token[] tokens) {
    if (tokens.length != 0 && tokens[tokens.length - 1].right != totalLength) {
      throw new AssertionError(
          String.format(
              "Last tokenized character '%s' doesn't match document length '%s'",
              tokens[tokens.length - 1].right, totalLength));
    }
    int start = 0;
    for (int i = 0; i < tokens.length; i++) {
      Token token = tokens[i];
      if (token.left != start) {
        throw new AssertionError("Gap/inconsistency at: " + start);
      }
      start = token.right;
    }
  }

  /**
   * Returns a string containing the names of the tokens and their associated values.
   * (String-literals are printed without escaping.)
   */
  protected String values(Token[] tokens) {
    StringBuilder buffer = new StringBuilder();
    for (Token token : tokens) {
      if (isIgnored(token.kind)) {
        continue;
      }
      if (buffer.length() > 0) {
        buffer.append(' ');
      }
      buffer.append(token.kind.name());
      if (token.kind != TokenKind.WHITESPACE && token.value != null) {
        buffer.append('(').append(token.value).append(')');
      }
    }
    return buffer.toString();
  }

  /** Returns a string containing just the names of the tokens. */
  protected String names(Token[] tokens) {
    StringBuilder buf = new StringBuilder();
    for (Token token : tokens) {
      if (isIgnored(token.kind)) {
        continue;
      }
      if (buf.length() > 0) {
        buf.append(' ');
      }
      buf.append(token.kind.name());
    }
    return buf.toString();
  }

  private boolean isIgnored(TokenKind kind) {
    if (mode == BuildLexerBase.LexerMode.Parsing) {
      return kind == TokenKind.WHITESPACE || kind == TokenKind.COMMENT;
    }
    return false;
  }

  /**
   * Returns a string containing just the half-open position intervals of each token. e.g. "[3,4)
   * [4,9)".
   */
  protected String positions(Token[] tokens) {
    StringBuilder buf = new StringBuilder();
    for (Token token : tokens) {
      if (isIgnored(token.kind)) {
        continue;
      }
      if (buf.length() > 0) {
        buf.append(' ');
      }
      buf.append('[').append(token.left).append(',').append(token.right).append(')');
    }
    return buf.toString();
  }

  @Test
  public void testIntegers() throws Exception {
    // Detection of MINUS immediately following integer constant proves we
    // don't consume too many chars.

    // decimal
    assertEquals("INT(12345) MINUS", values(tokens("12345-")));

    // octal
    assertEquals("INT(5349) MINUS", values(tokens("012345-")));
    assertEquals("INT(5349) MINUS", values(tokens("0o12345-")));
    assertEquals("INT(63)", values(tokens("0O77")));

    // octal (bad)
    assertEquals("INT(0) MINUS", values(tokens("012349-")));
    assertEquals("invalid base-8 integer constant: 012349", lastError);
    assertEquals("INT(0)", values(tokens("0o")));
    assertEquals("invalid base-8 integer constant: 0o", lastError);

    // hexadecimal (uppercase)
    assertEquals("INT(1193055) MINUS", values(tokens("0X12345F-")));

    // hexadecimal (lowercase)
    assertEquals("INT(1193055) MINUS", values(tokens("0x12345f-")));

    // hexadecimal (lowercase) [note: "g" cause termination of token]
    assertEquals("INT(74565) IDENTIFIER(g) MINUS", values(tokens("0x12345g-")));
  }

  @Test
  public void testStringDelimiters() throws Exception {
    assertEquals("STRING(foo)", values(tokens("\"foo\"")));
    assertEquals("STRING(foo)", values(tokens("'foo'")));
  }

  @Test
  public void testQuotesInStrings() throws Exception {
    assertEquals("STRING(foo'bar)", values(tokens("'foo\\'bar'")));
    assertEquals("STRING(foo'bar)", values(tokens("\"foo'bar\"")));
    assertEquals("STRING(foo\"bar)", values(tokens("'foo\"bar'")));
    assertEquals("STRING(foo\"bar)", values(tokens("\"foo\\\"bar\"")));
  }

  @Test
  public void testStringEscapes() throws Exception {
    assertEquals("STRING(a\tb\nc\rd)", values(tokens("'a\\tb\\nc\\rd'"))); // \t \r \n
    assertEquals("STRING(x\\hx)", values(tokens("'x\\hx'"))); // \h is unknown => "\h"
    assertEquals("STRING(\\$$)", values(tokens("'\\$$'")));
    assertEquals("STRING(ab)", values(tokens("'a\\\nb'"))); // escape end of line
    assertEquals("STRING(abcd)", values(tokens("\"ab\\ucd\"")));
    assertEquals("escape sequence not implemented: \\u", lastError);
  }

  @Test
  public void testRawString() throws Exception {
    assertEquals("STRING(abcd)", values(tokens("r'abcd'")));
    assertEquals("STRING(abcd)", values(tokens("r\"abcd\"")));
    assertEquals("STRING(a\\tb\\nc\\rd)", values(tokens("r'a\\tb\\nc\\rd'"))); // r'a\tb\nc\rd'
    assertEquals("STRING(a\\\")", values(tokens("r\"a\\\"\""))); // r"a\""
    assertEquals("STRING(a\\\\b)", values(tokens("r'a\\\\b'"))); // r'a\\b'
    assertEquals("STRING(ab) IDENTIFIER(r)", values(tokens("r'ab'r")));

    // Unterminated raw string
    values(tokens("r'\\'")); // r'\'
    assertEquals("unterminated string literal at eof", lastError);
  }

  @Test
  public void testTripleRawString() throws Exception {
    // r'''a\ncd'''
    assertEquals("STRING(ab\\ncd)", values(tokens("r'''ab\\ncd'''")));
    // r"""ab
    // cd"""
    assertEquals("STRING(ab\ncd)", values(tokens("\"\"\"ab\ncd\"\"\"")));

    // Unterminated raw string
    values(tokens("r'''\\'''")); // r'''\'''
    assertEquals("unterminated string literal at eof", lastError);
  }

  @Test
  public void testOctalEscapes() throws Exception {
    // Regression test for a bug.
    assertEquals(
        "STRING(\0 \1 \t \u003f I I1 \u00ff \u00ff \u00fe)",
        values(tokens("'\\0 \\1 \\11 \\77 \\111 \\1111 \\377 \\777 \\776'")));
    // Test boundaries (non-octal char, EOF).
    assertEquals("STRING(\1b \1)", values(tokens("'\\1b \\1'")));
  }

  @Test
  public void testTripleQuotedStrings() throws Exception {
    assertEquals("STRING(a\"b'c \n d\"\"e)", values(tokens("\"\"\"a\"b'c \n d\"\"e\"\"\"")));
    assertEquals("STRING(a\"b'c \n d\"\"e)", values(tokens("'''a\"b'c \n d\"\"e'''")));
  }

  @Test
  public void testBadChar() throws Exception {
    assertEquals("IDENTIFIER(a) ILLEGAL($) IDENTIFIER(b)", values(tokens("a$b")));
    assertEquals("invalid character: '$'", lastError);
  }

  @Test
  public void testContainsErrors() throws Exception {
    BuildLexerBase lexerSuccess = createLexer("foo");
    assertFalse(lexerSuccess.containsErrors());

    BuildLexerBase lexerFail = createLexer("f$o");
    assertTrue(lexerFail.containsErrors());

    String s = "'unterminated";
    lexerFail = createLexer(s);
    assertTrue(lexerFail.containsErrors());
    assertEquals("STRING(unterminated)", values(tokens(s)));
  }

  @Test
  public void testUnterminatedEscapedQuotedString() throws Exception {
    // regression test --
    assertEquals(
        "STRING(escaped \n string) NEWLINE IDENTIFIER(next_line)",
        values(tokens("\"escaped \\n string\nnext_line")));

    assertEquals("STRING(escaped \n string)", values(tokens("'escaped \\n string")));
  }
}
