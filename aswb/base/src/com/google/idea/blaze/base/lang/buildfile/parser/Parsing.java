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
package com.google.idea.blaze.base.lang.buildfile.parser;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;

/** Base class for BUILD file component parsers */
public abstract class Parsing {

  // Keywords that exist in Python which we don't parse.
  protected static final EnumSet<TokenKind> FORBIDDEN_KEYWORDS =
      EnumSet.of(
          TokenKind.AS,
          TokenKind.ASSERT,
          TokenKind.DEL,
          TokenKind.EXCEPT,
          TokenKind.FINALLY,
          TokenKind.FROM,
          TokenKind.GLOBAL,
          TokenKind.IMPORT,
          TokenKind.IS,
          TokenKind.LAMBDA,
          TokenKind.NONLOCAL,
          TokenKind.RAISE,
          TokenKind.TRY,
          TokenKind.WITH,
          TokenKind.WHILE,
          TokenKind.YIELD);

  protected ParsingContext context;
  protected PsiBuilder builder;

  public Parsing(ParsingContext context) {
    this.context = context;
    this.builder = context.builder;
  }

  protected ExpressionParsing getExpressionParser() {
    return context.expressionParser;
  }

  /** @return true if a string was parsed */
  protected boolean parseStringLiteral(boolean alwaysConsume) {
    if (currentToken() != TokenKind.STRING) {
      expect(TokenKind.STRING, alwaysConsume);
      return false;
    }
    buildTokenElement(BuildElementTypes.STRING_LITERAL);
    if (currentToken() == TokenKind.STRING) {
      builder.error("implicit string concatenation is forbidden; use the '+' operator");
    }
    return true;
  }

  protected void buildTokenElement(BuildElementType type) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(type);
  }

  /**
   * Consume tokens until we reach the first token that has a kind that is in the set of
   * terminatingTokens.
   */
  protected void syncTo(Set<TokenKind> terminatingTokens) {
    // read past the problematic token
    while (!atAnyOfTokens(terminatingTokens)) {
      builder.advanceLexer();
    }
  }

  /**
   * Consume tokens until we consume the first token that has a kind that is in the set of
   * terminatingTokens.
   */
  protected void syncPast(Set<TokenKind> terminatingTokens) {
    // read past the problematic token
    while (!matchesAnyOf(terminatingTokens)) {
      builder.advanceLexer();
    }
  }

  /**
   * Consumes the current token iff it's one of the expected types.<br>
   * Otherwise, returns false and reports an error.
   */
  protected boolean expect(TokenKind kind) {
    return expect(kind, false);
  }

  /**
   * Consumes the current token if 'alwaysConsume' is true or if it's one of the expected types.<br>
   * Otherwise, returns false and reports an error.
   */
  protected boolean expect(TokenKind kind, boolean alwaysConsume) {
    return expect(kind, String.format("'%s' expected", kind), alwaysConsume);
  }

  /**
   * Consumes the current token if 'alwaysConsume' is true or if it's one of the expected types.<br>
   * Otherwise, returns false and reports an error.
   */
  protected boolean expect(TokenKind kind, String message, boolean alwaysConsume) {
    TokenKind current = currentToken();
    if (current == kind || alwaysConsume) {
      builder.advanceLexer();
    }
    if (current != kind) {
      builder.error(message);
      return false;
    }
    return true;
  }

  /** Checks if we're at the current sequence of tokens. If so, consumes them. */
  protected boolean matchesSequence(TokenKind... kinds) {
    PsiBuilder.Marker marker = builder.mark();
    for (TokenKind kind : kinds) {
      if (!matches(kind)) {
        marker.rollbackTo();
        return false;
      }
    }
    marker.drop();
    return true;
  }

  /** Consumes the current token iff it matches the expected type. Otherwise, returns false */
  protected boolean matches(TokenKind kind) {
    if (currentToken() == kind) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /**
   * Consumes the current token iff it matches one of the expected types. Otherwise, returns false
   */
  protected boolean matchesAnyOf(TokenKind... kinds) {
    TokenKind current = currentToken();
    for (TokenKind kind : kinds) {
      if (kind == current) {
        builder.advanceLexer();
        return true;
      }
    }
    return false;
  }

  /** Consumes the current token iff it's one of the expected types. Otherwise, returns false */
  protected boolean matchesAnyOf(Set<TokenKind> kinds) {
    if (kinds.contains(currentToken())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /** Checks if the upcoming sequence of tokens match that expected. Doesn't advance the parser. */
  protected boolean atTokenSequence(TokenKind... kinds) {
    for (int i = 0; i < kinds.length; i++) {
      if (kindFromElement(builder.lookAhead(i)) != kinds[i]) {
        return false;
      }
    }
    return true;
  }

  /** Checks if the current token matches the expected kind. Doesn't advance the parser. */
  protected boolean atToken(TokenKind kind) {
    return currentToken() == kind;
  }

  /**
   * Checks if the current token matches any one of the expected kinds. Doesn't advance the parser.
   */
  protected boolean atAnyOfTokens(TokenKind... kinds) {
    TokenKind current = currentToken();
    for (TokenKind kind : kinds) {
      if (current == kind) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the current token matches any one of the expected kinds. Doesn't advance the parser.
   */
  protected boolean atAnyOfTokens(Set<TokenKind> kinds) {
    return kinds.contains(currentToken());
  }

  @Nullable
  protected TokenKind currentToken() {
    return builder.eof() ? TokenKind.EOF : kindFromElement(builder.getTokenType());
  }

  @Nullable
  protected TokenKind kindFromElement(IElementType type) {
    if (type == null) {
      return null;
    }
    if (!(type instanceof BuildToken)) {
      throw new RuntimeException("Invalid type: " + type + " of class " + type.getClass());
    }
    TokenKind kind = ((BuildToken) type).kind;
    checkForbiddenKeywords(kind);
    return kind;
  }

  private void checkForbiddenKeywords(TokenKind kind) {
    if (!FORBIDDEN_KEYWORDS.contains(kind)) {
      return;
    }
    builder.error(forbiddenKeywordError(kind));
  }

  protected String forbiddenKeywordError(TokenKind kind) {
    assert FORBIDDEN_KEYWORDS.contains(kind);
    switch (kind) {
      case ASSERT:
        return "'assert' not supported, use 'fail' instead";
      case TRY:
        return "'try' not supported, all exceptions are fatal";
      case IMPORT:
        return "'import' not supported, use 'load' instead";
      case IS:
        return "'is' not supported, use '==' instead";
      case LAMBDA:
        return "'lambda' not supported, declare a function instead";
      case RAISE:
        return "'raise' not supported, use 'fail' instead";
      case WHILE:
        return "'while' not supported, use 'for' instead";
      default:
        return "keyword '" + kind + "' not supported";
    }
  }
}
