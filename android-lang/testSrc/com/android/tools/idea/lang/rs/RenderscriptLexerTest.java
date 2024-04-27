/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.lang.rs;

import com.android.tools.idea.lang.LangTestDataKt;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

public class RenderscriptLexerTest extends TestCase {
  private void validateLexer(String input, IElementType... tokens) {
    int i = 0;

    RenderscriptLexer lexer = new RenderscriptLexer();

    for (lexer.start(input); lexer.getTokenType() != null; lexer.advance(), i++) {
      assertTrue(String.format("More tokens than expected (%1$d)", tokens.length),
                 i < tokens.length);
      assertEquals("Mismatch at '" + lexer.getTokenText() + "'", tokens[i], lexer.getTokenType());
    }
  }

  public void test1() {
    validateLexer("const int i;",
                  RenderscriptTokenType.KEYWORD,
                  TokenType.WHITE_SPACE,
                  RenderscriptTokenType.KEYWORD,
                  TokenType.WHITE_SPACE,
                  RenderscriptTokenType.IDENTIFIER,
                  RenderscriptTokenType.SEPARATOR);
  }

  public void testCStyleComment() {
    validateLexer("foo(var/* comment */);",
                  RenderscriptTokenType.IDENTIFIER,
                  RenderscriptTokenType.BRACE,
                  RenderscriptTokenType.IDENTIFIER,
                  RenderscriptTokenType.COMMENT,
                  RenderscriptTokenType.BRACE,
                  RenderscriptTokenType.SEPARATOR);
  }

  public void testCppComment() {
    validateLexer("float// foo\n\tdouble",
                  RenderscriptTokenType.KEYWORD,
                  RenderscriptTokenType.COMMENT,
                  TokenType.WHITE_SPACE,
                  RenderscriptTokenType.KEYWORD);
  }

  public void testString() {
    validateLexer("\"Simple string\\ntest\\rwith escape\\chars.\";",
                  RenderscriptTokenType.STRING,
                  RenderscriptTokenType.SEPARATOR);

    // current lexer recognizes a string even if it doesn't terminate with a quote
    validateLexer("\"No end quote", RenderscriptTokenType.STRING);

    // a newline should terminate a string as far as the lexer is concerned
    validateLexer("\"Line separator\n\"",
                  RenderscriptTokenType.STRING,   // string before \n
                  TokenType.WHITE_SPACE,          // \n
                  RenderscriptTokenType.STRING);  // the quote  after \n
  }

  public void testCharacterLiterals() {
    validateLexer("'a'", RenderscriptTokenType.CHARACTER);
    validateLexer("'\\n'", RenderscriptTokenType.CHARACTER);
    validateLexer("'\\\\'", RenderscriptTokenType.CHARACTER);

    // newline should terminate a character literal (as far as the lexer is concerned)
    validateLexer("'\n'",
                  RenderscriptTokenType.CHARACTER,  // first quote
                  TokenType.WHITE_SPACE,            // newline
                  RenderscriptTokenType.CHARACTER); // second quote
  }

  public void testNumbers() {
    validateLexer("3.14f", RenderscriptTokenType.NUMBER);
    validateLexer("2.1e-123", RenderscriptTokenType.NUMBER);
  }

  public void testFile() throws IOException {
    String path = LangTestDataKt.getTestDataPath();
    String rsPath = "lang" + File.separator + "rs" + File.separator + "ball_physics.rs";
    String input = Files.toString(new File(path, rsPath), Charsets.UTF_8);

    RenderscriptLexer lexer = new RenderscriptLexer();

    int i = 0;
    for (lexer.start(input); lexer.getTokenType() != null; lexer.advance(), i++) {
      assertTrue(lexer.getTokenType() != RenderscriptTokenType.UNKNOWN);
      assertTrue(i < 10000); // check we are not stuck in an infinite loop
    }
  }
  public void testVectorTypes() {
    List<String> vectorTypes = Arrays.asList("char2", "double2", "float3", "half4",
                                             "int2", "long3", "short4", "uchar2",
                                             "ulong3", "uint4", "ushort2");
    for (String type: vectorTypes) {
      validateLexer(type + " i",
                    RenderscriptTokenType.KEYWORD,
                    TokenType.WHITE_SPACE,
                    RenderscriptTokenType.IDENTIFIER);
    }
  }

  public void testRsDataTypes() {
    List<String> rsDataTypes = Arrays.asList("rs_matrix2x2", "rs_quaternion",
                                             "rs_element", "rs_allocation",
                                             "rs_sampler", "rs_script",
                                             "rs_type", "rs_kernel_context");
    for (String type: rsDataTypes) {
      validateLexer(type + " i",
                    RenderscriptTokenType.KEYWORD,
                    TokenType.WHITE_SPACE,
                    RenderscriptTokenType.IDENTIFIER);
    }
  }

  public void testRsAPIs() {
    List<String> rsAPIs = Arrays.asList("abs", "half_sqrt", "log10",
                                             "native_sqrt", "native_tanh",
                                             "sqrt", "convert_uchar2",
                                             "rsIsObject", "rsGetDimX");
    for (String api: rsAPIs) {
      validateLexer(api + "(i)",
                    RenderscriptTokenType.KEYWORD,
                    RenderscriptTokenType.BRACE,
                    RenderscriptTokenType.IDENTIFIER,
                    RenderscriptTokenType.BRACE);
    }

    rsAPIs = Arrays.asList("rsGetElementAt", "rsSetElementAt",
                                             "rsAllocationVStoreX_uint3");
    for (String api: rsAPIs) {
      validateLexer(api + "(a, val, x)",
                    RenderscriptTokenType.KEYWORD,
                    RenderscriptTokenType.BRACE,
                    RenderscriptTokenType.IDENTIFIER,
                    RenderscriptTokenType.SEPARATOR,
                    TokenType.WHITE_SPACE,
                    RenderscriptTokenType.IDENTIFIER,
                    RenderscriptTokenType.SEPARATOR,
                    TokenType.WHITE_SPACE,
                    RenderscriptTokenType.IDENTIFIER,
                    RenderscriptTokenType.BRACE);
    }
  }
}
