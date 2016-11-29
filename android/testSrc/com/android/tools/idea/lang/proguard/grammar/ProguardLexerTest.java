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
package com.android.tools.idea.lang.proguard.grammar;

import com.intellij.psi.tree.IElementType;
import junit.framework.TestCase;

import static com.android.tools.idea.lang.proguard.psi.ProguardTypes.*;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;

public class ProguardLexerTest extends TestCase {
  public void testSingleFlag() {
    validateLexer("-testFlag",
                  FLAG_NAME);
  }

  public void testMultipleFlags() {
    validateLexer("-testFlag1  testArg1\n" +
                  "-testFlag2 testArg2",
                  FLAG_NAME, WHITE_SPACE, FLAG_ARG, CRLF,
                  FLAG_NAME, WHITE_SPACE, FLAG_ARG);
  }

  public void testComments() {
    validateLexer("# Test Comment\n" +
                  "-testFlag#comment",
                  LINE_CMT, CRLF,
                  FLAG_NAME, LINE_CMT);
  }

  public void testJavaSpec() {
    validateLexer("-keepclassmembers class {\n" +
                  "   public *; #comment\n" +
                  "}",
                  FLAG_NAME, WHITE_SPACE, FLAG_ARG, WHITE_SPACE, OPEN_BRACE, CRLF,
                  JAVA_DECL, WHITE_SPACE, LINE_CMT, CRLF,
                  CLOSE_BRACE);
  }

  public void testSingleLineJavaSpec() {
    validateLexer("-keep class { int a; }",
                  FLAG_NAME, WHITE_SPACE, FLAG_ARG, WHITE_SPACE,
                  OPEN_BRACE, JAVA_DECL, WHITE_SPACE, CLOSE_BRACE);
  }

  public void testBadChar() {
    // A proguard line should be either a comment, whitespace or a flag.
    validateLexer("b", BAD_CHARACTER);
  }

  private static void validateLexer(String input, IElementType... tokens) {
    ProguardLexer lexer = new ProguardLexer();

    int i = 0;
    for (lexer.start(input); lexer.getTokenType() != null; lexer.advance(), i++) {
      assertTrue(String.format("More tokens than expected (%1$d)", tokens.length), i < tokens.length);
      assertEquals("Mismatch at '" + lexer.getTokenText() + "'", tokens[i], lexer.getTokenType());
    }
    assertEquals("Expected more tokens than parsed", tokens.length, i);
  }
}
