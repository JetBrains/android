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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.tree.IElementType;

/** For parsing statements in BUILD files. */
public class StatementParsing extends Parsing {

  private static final ImmutableSet<TokenKind> STATEMENT_TERMINATOR_SET =
      ImmutableSet.of(TokenKind.EOF, TokenKind.NEWLINE, TokenKind.SEMI);

  public StatementParsing(ParsingContext context) {
    super(context);
  }

  /** Called at the start of parsing. Parses an entire file */
  public void parseFileInput() {
    builder.setDebugMode(ApplicationManager.getApplication().isUnitTestMode());
    while (!builder.eof()) {
      if (matches(TokenKind.NEWLINE)) {
        continue;
      }
      parseTopLevelStatement();
    }
  }

  // Unlike in Python grammar, 'load' and 'def' are only allowed as a top-level statement
  public void parseTopLevelStatement() {
    if (currentToken() == TokenKind.IDENTIFIER && "load".equals(builder.getTokenText())) {
      parseLoadStatement();
    } else if (currentToken() == TokenKind.DEF) {
      parseFunctionDefStatement();
    } else {
      parseStatement();
    }
  }

  // simple_stmt | compound_stmt
  public void parseStatement() {
    TokenKind current = currentToken();
    if (current == TokenKind.IF) {
      parseIfStatement();
    } else if (current == TokenKind.FOR) {
      parseForStatement();
    } else if (FORBIDDEN_KEYWORDS.contains(current)) {
      PsiBuilder.Marker mark = builder.mark();
      syncTo(STATEMENT_TERMINATOR_SET);
      mark.error(forbiddenKeywordError(current));
      builder.advanceLexer();
    } else {
      parseSimpleStatement();
    }
  }

  // func_def_stmt ::= DEF IDENTIFIER funcall_suffix ':' suite
  private void parseFunctionDefStatement() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.DEF);
    getExpressionParser().expectIdentifier("expected a function name");
    PsiBuilder.Marker listMarker = builder.mark();
    expect(TokenKind.LPAREN);
    getExpressionParser().parseFunctionParameters();
    expect(TokenKind.RPAREN, true);
    listMarker.done(BuildElementTypes.PARAMETER_LIST);
    expect(TokenKind.COLON);
    parseSuite();
    marker.done(BuildElementTypes.FUNCTION_STATEMENT);
  }

  // load '(' STRING (',' [IDENTIFIER '='] STRING)* [','] ')'
  private void parseLoadStatement() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.IDENTIFIER);
    expect(TokenKind.LPAREN);
    parseStringLiteral(false);
    // Not implementing [IDENTIFIER EQUALS] option -- not a documented feature,
    // so wait for users to complain
    boolean hasSymbols = false;
    while (!matches(TokenKind.RPAREN) && !matchesAnyOf(STATEMENT_TERMINATOR_SET)) {
      expect(TokenKind.COMMA);
      if (matches(TokenKind.RPAREN) || matchesAnyOf(STATEMENT_TERMINATOR_SET)) {
        break;
      }
      hasSymbols |= parseLoadedSymbol();
    }
    if (!hasSymbols) {
      builder.error("'load' statements must include at least one loaded function");
    }
    marker.done(BuildElementTypes.LOAD_STATEMENT);
  }

  /** [IDENTIFIER '='] STRING */
  private boolean parseLoadedSymbol() {
    PsiBuilder.Marker marker = builder.mark();
    if (currentToken() == TokenKind.STRING) {
      parseStringLiteral(true);
      marker.done(BuildElementTypes.LOADED_SYMBOL);
      return true;
    }
    if (parseAlias()) {
      marker.done(BuildElementTypes.LOADED_SYMBOL);
      return true;
    }
    marker.drop();
    builder.error("Expected a loaded symbol or alias");
    syncPast(ExpressionParsing.EXPR_TERMINATOR_SET);
    return false;
  }

  private boolean parseAlias() {
    if (!atTokenSequence(TokenKind.IDENTIFIER, TokenKind.EQUALS, TokenKind.STRING)) {
      return false;
    }
    PsiBuilder.Marker assignment = builder.mark();
    buildTokenElement(BuildElementTypes.TARGET_EXPRESSION);
    expect(TokenKind.EQUALS);
    parseStringLiteral(true);
    assignment.done(BuildElementTypes.ASSIGNMENT_STATEMENT);
    return true;
  }

  /** if_stmt ::= IF expr ':' suite (ELIF expr ':' suite)* [ELSE ':' suite] */
  private void parseIfStatement() {
    PsiBuilder.Marker marker = builder.mark();
    parseIfStatementPart(TokenKind.IF, BuildElementTypes.IF_PART, true);
    while (currentToken() == TokenKind.ELIF) {
      parseIfStatementPart(TokenKind.ELIF, BuildElementTypes.ELSE_IF_PART, true);
    }
    if (currentToken() == TokenKind.ELSE) {
      parseIfStatementPart(TokenKind.ELSE, BuildElementTypes.ELSE_PART, false);
    }
    marker.done(BuildElementTypes.IF_STATEMENT);
  }

  // cond_stmts ::= [EL]IF expr ':' suite
  private void parseIfStatementPart(TokenKind tokenKind, IElementType type, boolean conditional) {
    PsiBuilder.Marker marker = builder.mark();
    expect(tokenKind);
    if (conditional) {
      getExpressionParser().parseNonTupleExpression();
    }
    expect(TokenKind.COLON);
    parseSuite();
    marker.done(type);
  }

  // for_stmt ::= FOR IDENTIFIER IN expr ':' suite
  private void parseForStatement() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.FOR);
    getExpressionParser().parseForLoopVariables();
    expect(TokenKind.IN);
    getExpressionParser().parseExpression(false);
    expect(TokenKind.COLON);
    parseSuite();
    marker.done(BuildElementTypes.FOR_STATEMENT);
  }

  // simple_stmt ::= small_stmt (';' small_stmt)* ';'? NEWLINE
  private void parseSimpleStatement() {
    parseSmallStatementOrPass();
    while (matches(TokenKind.SEMI)) {
      if (matches(TokenKind.NEWLINE)) {
        return;
      }
      parseSmallStatementOrPass();
    }
    if (!builder.eof()) {
      expect(TokenKind.NEWLINE);
    }
  }

  // small_stmt | 'pass'
  private void parseSmallStatementOrPass() {
    if (currentToken() == TokenKind.PASS) {
      buildTokenElement(BuildElementTypes.PASS_STATMENT);
      return;
    }
    parseSmallStatement();
  }

  private void parseSmallStatement() {
    if (currentToken() == TokenKind.RETURN) {
      parseReturnStatement();
      return;
    }
    if (atAnyOfTokens(TokenKind.BREAK, TokenKind.CONTINUE)) {
      buildTokenElement(BuildElementTypes.FLOW_STATEMENT);
      return;
    }
    PsiBuilder.Marker refMarker = builder.mark();
    getExpressionParser().parseExpression(false);
    if (matches(TokenKind.EQUALS)) {
      getExpressionParser().parseExpression(false);
      refMarker.done(BuildElementTypes.ASSIGNMENT_STATEMENT);
    } else if (matchesAnyOf(TokenKind.AUGMENTED_ASSIGNMENT_OPS)) {
      getExpressionParser().parseExpression(false);
      refMarker.done(BuildElementTypes.AUGMENTED_ASSIGNMENT);
    } else {
      refMarker.drop();
    }
  }

  private void parseReturnStatement() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.RETURN);
    if (!STATEMENT_TERMINATOR_SET.contains(currentToken())) {
      getExpressionParser().parseExpression(false);
    }
    marker.done(BuildElementTypes.RETURN_STATEMENT);
  }

  // suite ::= simple_stmt | (NEWLINE INDENT stmt+ DEDENT)
  private void parseSuite() {
    if (!matches(TokenKind.NEWLINE)) {
      parseSimpleStatement();
      return;
    }
    PsiBuilder.Marker marker = builder.mark();
    if (expect(TokenKind.INDENT)) {
      while (!builder.eof() && !matches(TokenKind.DEDENT)) {
        parseStatement();
      }
    }
    marker.done(BuildElementTypes.STATEMENT_LIST);
  }
}
