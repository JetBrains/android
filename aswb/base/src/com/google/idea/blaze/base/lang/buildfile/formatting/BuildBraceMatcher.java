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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * This adds a close brace automatically once an opening brace is typed by the user in the editor.
 */
public class BuildBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS =
      new BracePair[] {
        new BracePair(
            BuildToken.fromKind(TokenKind.LPAREN), BuildToken.fromKind(TokenKind.RPAREN), true),
        new BracePair(
            BuildToken.fromKind(TokenKind.LBRACKET), BuildToken.fromKind(TokenKind.RBRACKET), true),
        new BracePair(
            BuildToken.fromKind(TokenKind.LBRACE), BuildToken.fromKind(TokenKind.RBRACE), true)
      };

  private static final TokenSet BRACES_ALLOWED_BEFORE =
      tokenSet(
          TokenKind.NEWLINE,
          TokenKind.WHITESPACE,
          TokenKind.COMMENT,
          TokenKind.COLON,
          TokenKind.COMMA,
          TokenKind.RPAREN,
          TokenKind.RBRACKET,
          TokenKind.RBRACE,
          TokenKind.LBRACE);

  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(
      IElementType lbraceType, @Nullable IElementType contextType) {
    return contextType == null || BRACES_ALLOWED_BEFORE.contains(contextType);
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }

  private static TokenSet tokenSet(TokenKind... kind) {
    return TokenSet.create(
        Arrays.stream(kind).map(BuildToken::fromKind).toArray(IElementType[]::new));
  }
}
