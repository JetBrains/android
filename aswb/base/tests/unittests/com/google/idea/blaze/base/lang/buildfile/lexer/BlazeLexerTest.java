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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests of tokenization behavior of {@link BuildLexerBase} in 'parsing mode' (see {@link
 * BuildLexerBase.LexerMode})
 */
@RunWith(JUnit4.class)
public class BlazeLexerTest extends AbstractLexerTest {

  public BlazeLexerTest() {
    super(BuildLexerBase.LexerMode.Parsing);
  }

  @Test
  public void testBasics1() throws Exception {
    assertEquals("IDENTIFIER RPAREN", names(tokens("wiz) ")));
    assertEquals("IDENTIFIER RPAREN", names(tokens("wiz )")));
    assertEquals("IDENTIFIER RPAREN", names(tokens(" wiz)")));
    assertEquals("IDENTIFIER RPAREN", names(tokens(" wiz ) ")));
    assertEquals("IDENTIFIER RPAREN", names(tokens("wiz\t)")));
  }

  @Test
  public void testBasics2() throws Exception {
    assertEquals("RPAREN", names(tokens(")")));
    assertEquals("RPAREN", names(tokens(" )")));
    assertEquals("RPAREN", names(tokens(" ) ")));
    assertEquals("RPAREN", names(tokens(") ")));
  }

  @Test
  public void testBasics3() throws Exception {
    assertEquals("INT NEWLINE INT", names(tokens("123#456\n789")));
    assertEquals("INT NEWLINE INT", names(tokens("123 #456\n789")));
    assertEquals("INT NEWLINE INT", names(tokens("123#456 \n789")));
    assertEquals("INT NEWLINE INDENT INT", names(tokens("123#456\n 789")));
    assertEquals("INT NEWLINE INT", names(tokens("123#456\n789 ")));
  }

  @Test
  public void testBasics4() throws Exception {
    assertEquals("", names(tokens("")));
    assertEquals("", names(tokens("# foo")));
    assertEquals("INT INT INT INT", names(tokens("1 2 3 4")));
    assertEquals("INT DOT INT", names(tokens("1.234")));
    assertEquals(
        "IDENTIFIER LPAREN IDENTIFIER COMMA IDENTIFIER RPAREN", names(tokens("foo(bar, wiz)")));
  }

  @Test
  public void testIntegersAndDot() throws Exception {
    assertEquals("INT(1) DOT INT(2345)", values(tokens("1.2345")));

    assertEquals("INT(1) DOT INT(2) DOT INT(345)", values(tokens("1.2.345")));

    assertEquals("INT(1) DOT INT(0)", values(tokens("1.23E10")));
    assertEquals("invalid base-10 integer constant: 23E10", lastError);

    assertEquals("INT(1) DOT INT(0) MINUS INT(10)", values(tokens("1.23E-10")));
    assertEquals("invalid base-10 integer constant: 23E", lastError);

    assertEquals("DOT INT(123)", values(tokens(". 123")));
    assertEquals("DOT INT(123)", values(tokens(".123")));
    assertEquals("DOT IDENTIFIER(abc)", values(tokens(".abc")));

    assertEquals("IDENTIFIER(foo) DOT INT(123)", values(tokens("foo.123")));
    assertEquals(
        "IDENTIFIER(foo) DOT IDENTIFIER(bcd)", values(tokens("foo.bcd"))); // 'b' are hex chars
    assertEquals("IDENTIFIER(foo) DOT IDENTIFIER(xyz)", values(tokens("foo.xyz")));
  }

  @Test
  public void testIndentation() throws Exception {
    assertEquals("INT(1) NEWLINE INT(2) NEWLINE INT(3)", values(tokens("1\n2\n3")));
    assertEquals(
        "INT(1) NEWLINE INDENT INT(2) NEWLINE INT(3) NEWLINE DEDENT INT(4)",
        values(tokens("1\n  2\n  3\n4 ")));
    assertEquals("INT(1) NEWLINE INDENT INT(2) NEWLINE INT(3)", values(tokens("1\n  2\n  3")));
    assertEquals(
        "INT(1) NEWLINE INDENT INT(2) NEWLINE INDENT INT(3)", values(tokens("1\n  2\n    3")));
    assertEquals(
        "INT(1) NEWLINE INDENT INT(2) NEWLINE INDENT INT(3) NEWLINE "
            + "DEDENT INT(4) NEWLINE DEDENT INT(5)",
        values(tokens("1\n  2\n    3\n  4\n5")));

    assertEquals(
        "INT(1) NEWLINE INDENT INT(2) NEWLINE INDENT INT(3) NEWLINE "
            + "DEDENT INT(4) NEWLINE DEDENT INT(5)",
        values(tokens("1\n  2\n    3\n   4\n5")));
    assertEquals("indentation error", lastError);
  }

  @Test
  public void testIndentationInsideParens() throws Exception {
    // Indentation is ignored inside parens:
    assertEquals(
        "INT(1) LPAREN INT(2) INT(3) INT(4) INT(5)", values(tokens("1 (\n  2\n    3\n  4\n5")));
    assertEquals(
        "INT(1) LBRACE INT(2) INT(3) INT(4) INT(5)", values(tokens("1 {\n  2\n    3\n  4\n5")));
    assertEquals(
        "INT(1) LBRACKET INT(2) INT(3) INT(4) INT(5)", values(tokens("1 [\n  2\n    3\n  4\n5")));
    assertEquals(
        "INT(1) LBRACKET INT(2) RBRACKET NEWLINE INDENT INT(3) "
            + "NEWLINE INT(4) NEWLINE DEDENT INT(5)",
        values(tokens("1 [\n  2]\n    3\n    4\n5")));
  }

  @Test
  public void testNoIndentationAtEOF() throws Exception {
    assertEquals("INDENT INT(1)", values(tokens("\n  1")));
  }

  @Test
  public void testBlankLineIndentation() throws Exception {
    // Blank lines and comment lines should not generate any indents
    // (but note that every input ends with).
    assertEquals("", names(tokens("\n      #\n")));
    assertEquals("", names(tokens("      #")));
    assertEquals("NEWLINE", names(tokens("      #\n")));
    assertEquals("NEWLINE", names(tokens("      #comment\n")));
    assertEquals(
        "DEF IDENTIFIER LPAREN IDENTIFIER RPAREN COLON NEWLINE "
            + "INDENT RETURN IDENTIFIER NEWLINE DEDENT",
        names(tokens("def f(x):\n" + "  # comment\n" + "\n" + "  \n" + "  return x\n")));
  }

  @Test
  public void testMultipleCommentLines() throws Exception {
    assertEquals(
        "NEWLINE "
            + "DEF IDENTIFIER LPAREN IDENTIFIER RPAREN COLON NEWLINE "
            + "INDENT RETURN IDENTIFIER NEWLINE DEDENT",
        names(
            tokens(
                "# Copyright\n"
                    + "#\n"
                    + "# A comment line\n"
                    + "# An adjoining line\n"
                    + "def f(x):\n"
                    + "  return x\n")));
  }

  @Test
  public void testBackslash() throws Exception {
    // backslash followed by newline marked as whitespace (skipped by parser)
    assertEquals("IDENTIFIER IDENTIFIER", names(tokens("a\\\nb")));
    assertEquals("IDENTIFIER ILLEGAL IDENTIFIER", names(tokens("a\\ b")));
    assertEquals("IDENTIFIER LPAREN INT RPAREN", names(tokens("a(\\\n2)")));
  }

  @Test
  public void testTokenPositions() throws Exception {
    //            foo   (     bar   ,     {      1       :
    assertEquals(
        "[0,3) [3,4) [4,7) [7,8) [9,10) [10,11) [11,12)"
            //      'quux'  }       )
            + " [13,19) [19,20) [20,21)",
        positions(tokens("foo(bar, {1: 'quux'})")));
  }
}
