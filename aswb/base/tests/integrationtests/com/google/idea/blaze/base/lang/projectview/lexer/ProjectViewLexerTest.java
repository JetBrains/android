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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.projectview.ProjectViewIntegrationTestCase;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewLexerBase.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the project view file lexer */
@RunWith(JUnit4.class)
public class ProjectViewLexerTest extends ProjectViewIntegrationTestCase {

  @Test
  public void testStandardCase() {
    String result =
        tokenize(
            "directories:",
            "  java/com/google/work",
            "  java/com/google/other",
            "",
            "targets:",
            "  //java/com/google/work/...:all",
            "  //java/com/google/other/...:all");

    assertThat(result)
        .isEqualTo(
            Joiner.on(" ")
                .join(
                    "list_keyword :",
                    "indent identifier",
                    "indent identifier",
                    "list_keyword :",
                    "indent identifier : identifier",
                    "indent identifier : identifier"));
  }

  @Test
  public void testIncludeScalarSections() {
    String result =
        tokenize(
            "import java/com/google/work/.blazeproject",
            "",
            "workspace_type: intellij_plugin",
            "",
            "import_target_output:",
            "  //java/com/google/work:target",
            "",
            "test_sources:",
            "  java/com/google/common/*");

    assertThat(result)
        .isEqualTo(
            Joiner.on(" ")
                .join(
                    "scalar_keyword identifier",
                    "scalar_keyword : identifier",
                    "list_keyword :",
                    "indent identifier : identifier",
                    "list_keyword :",
                    "indent identifier"));
  }

  @Test
  public void testUnrecognizedKeyword() {
    String result =
        tokenize(
            "impart java/com/google/work/.blazeproject", "", "workspace_trype: intellij_plugin");

    assertThat(result)
        .isEqualTo(Joiner.on(" ").join("identifier identifier", "identifier : identifier"));
  }

  private static String tokenize(String... lines) {
    return names(tokens(Joiner.on("\n").join(lines)));
  }

  private static Token[] tokens(String input) {
    Token[] tokens = new ProjectViewLexerBase(input).getTokens().toArray(new Token[0]);
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

  /** Returns a string containing the names of the tokens. */
  private static String names(Token[] tokens) {
    StringBuilder buf = new StringBuilder();
    for (Token token : tokens) {
      if (isIgnored(token.type)) {
        continue;
      }
      if (buf.length() > 0) {
        buf.append(' ');
      }
      buf.append(token.type);
    }
    return buf.toString();
  }

  private static boolean isIgnored(ProjectViewTokenType kind) {
    return kind == ProjectViewTokenType.WHITESPACE || kind == ProjectViewTokenType.NEWLINE;
  }
}
