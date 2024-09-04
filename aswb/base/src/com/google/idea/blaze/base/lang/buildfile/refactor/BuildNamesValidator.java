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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexer;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexerBase;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import java.util.stream.Collectors;

/** Used for rename validation of elements which aren't string literals. */
public class BuildNamesValidator implements NamesValidator {

  private static final ImmutableSet<String> KEYWORDS =
      ImmutableSet.copyOf(
          TokenKind.KEYWORDS.stream().map(TokenKind::toString).collect(Collectors.toSet()));

  @Override
  public boolean isKeyword(String s, Project project) {
    return KEYWORDS.contains(s);
  }

  @Override
  public boolean isIdentifier(String s, Project project) {
    BuildLexer lexer = new BuildLexer(BuildLexerBase.LexerMode.Parsing);
    lexer.start(s);
    return lexer.getTokenEnd() == s.length() && lexer.getTokenKind() == TokenKind.IDENTIFIER;
  }
}
