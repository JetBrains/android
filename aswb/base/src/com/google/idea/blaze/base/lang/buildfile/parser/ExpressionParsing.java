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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.intellij.lang.PsiBuilder;
import java.util.EnumSet;

/** For parsing expressions in BUILD files. */
public class ExpressionParsing extends Parsing {

  private static final ImmutableSet<TokenKind> LIST_TERMINATOR_SET =
      ImmutableSet.of(TokenKind.EOF, TokenKind.RBRACKET, TokenKind.SEMI);

  private static final ImmutableSet<TokenKind> DICT_TERMINATOR_SET =
      ImmutableSet.of(TokenKind.EOF, TokenKind.RBRACE, TokenKind.SEMI);

  private static final ImmutableSet<TokenKind> EXPR_LIST_TERMINATOR_SET =
      ImmutableSet.of(
          TokenKind.EOF,
          TokenKind.NEWLINE,
          TokenKind.EQUALS,
          TokenKind.RBRACE,
          TokenKind.RBRACKET,
          TokenKind.RPAREN,
          TokenKind.SEMI);

  protected static final ImmutableSet<TokenKind> EXPR_TERMINATOR_SET =
      ImmutableSet.of(
          TokenKind.EOF,
          TokenKind.COLON,
          TokenKind.COMMA,
          TokenKind.FOR,
          TokenKind.MINUS,
          TokenKind.PERCENT,
          TokenKind.PLUS,
          TokenKind.RBRACKET,
          TokenKind.RPAREN,
          TokenKind.SLASH);

  private static final ImmutableSet<TokenKind> FUNCALL_TERMINATOR_SET =
      ImmutableSet.of(TokenKind.EOF, TokenKind.RPAREN, TokenKind.SEMI, TokenKind.NEWLINE);

  /**
   * Highest precedence goes last. Based on:
   * http://docs.python.org/2/reference/expressions.html#operator-precedence
   */
  private static final ImmutableList<EnumSet<TokenKind>> OPERATOR_PRECEDENCE =
      ImmutableList.of(
          EnumSet.of(TokenKind.OR),
          EnumSet.of(TokenKind.AND),
          EnumSet.of(TokenKind.NOT),
          EnumSet.of(
              TokenKind.EQUALS_EQUALS,
              TokenKind.NOT_EQUALS,
              TokenKind.LESS,
              TokenKind.LESS_EQUALS,
              TokenKind.GREATER,
              TokenKind.GREATER_EQUALS,
              TokenKind.IN,
              TokenKind.NOT_IN),
          EnumSet.of(TokenKind.PIPE),
          EnumSet.of(TokenKind.MINUS, TokenKind.PLUS),
          EnumSet.of(TokenKind.SLASH, TokenKind.SLASH_SLASH, TokenKind.STAR, TokenKind.PERCENT));

  public ExpressionParsing(ParsingContext context) {
    super(context);
  }

  public void parseExpression(boolean insideParens) {
    PsiBuilder.Marker tupleMarker = builder.mark();
    parseNonTupleExpression();
    if (currentToken() == TokenKind.COMMA) {
      parseExpressionList(insideParens);
      tupleMarker.done(BuildElementTypes.TUPLE_EXPRESSION);
    } else {
      tupleMarker.drop();
    }
  }

  /**
   * Parses a comma-separated list of expressions. It assumes that the first expression was already
   * parsed, so it starts with a comma. It is used to parse tuples and list elements.<br>
   * expr_list ::= ( ',' expr )* ','?
   */
  private void parseExpressionList(boolean trailingColonAllowed) {
    while (matches(TokenKind.COMMA)) {
      if (atAnyOfTokens(EXPR_LIST_TERMINATOR_SET)) {
        if (!trailingColonAllowed) {
          builder.error("Trailing commas are allowed only in parenthesized tuples.");
        }
        break;
      }
      parseNonTupleExpression();
    }
  }

  protected void parseNonTupleExpression() {
    parseNonTupleExpression(0);
    // don't bother including conditional expressions for now,
    //just include their components serially
    if (matches(TokenKind.IF)) {
      parseNonTupleExpression(0);
      if (matches(TokenKind.ELSE)) {
        parseNonTupleExpression();
      }
    }
  }

  private void parseNonTupleExpression(int prec) {
    if (prec >= OPERATOR_PRECEDENCE.size()) {
      parsePrimaryWithSuffix();
      return;
    }
    if (currentToken() == TokenKind.NOT && OPERATOR_PRECEDENCE.get(prec).contains(TokenKind.NOT)) {
      // special case handling of multi-token 'NOT IN' binary operator
      if (kindFromElement(builder.lookAhead(1)) != TokenKind.IN) {
        // skip the 'not' -- no need for a specific 'not' expression
        builder.advanceLexer();
        parseNonTupleExpression(prec + 1);
        return;
      }
    }
    parseBinOpExpression(prec);
  }

  /**
   * binop_expression ::= binop_expression OP binop_expression | parsePrimaryWithSuffix This
   * function takes care of precedence between operators (see OPERATOR_PRECEDENCE for the order),
   * and it assumes left-to-right associativity.
   */
  private void parseBinOpExpression(int prec) {
    PsiBuilder.Marker marker = builder.mark();
    parseNonTupleExpression(prec + 1);

    while (true) {
      if (!atBinaryOperator(prec)) {
        marker.drop();
        return;
      }
      parseNonTupleExpression(prec + 1);
      marker.done(BuildElementTypes.BINARY_OP_EXPRESSION);
      marker = marker.precede();
    }
  }

  /**
   * Consumes current token iff it's a binary operator at the given precedence level (with
   * special-case handling of 'NOT' 'IN' double token binary operator)
   */
  private boolean atBinaryOperator(int prec) {
    if (matchesAnyOf(OPERATOR_PRECEDENCE.get(prec))) {
      return true;
    }
    if (matchesSequence(TokenKind.NOT, TokenKind.IN)) {
      return true;
    }
    return false;
  }

  // primary_with_suffix ::= primary (selector_suffix | substring_suffix)*
  private void parsePrimaryWithSuffix() {
    PsiBuilder.Marker marker = builder.mark();
    parsePrimary();
    while (true) {
      if (matches(TokenKind.DOT)) {
        marker = parseSelectorSuffix(marker);
      } else if (matches(TokenKind.LBRACKET)) {
        marker = parseSubstringSuffix(marker);
      } else {
        break;
      }
    }
    marker.drop();
  }

  // selector_suffix ::= '.' IDENTIFIER [funcall_suffix]
  private PsiBuilder.Marker parseSelectorSuffix(PsiBuilder.Marker marker) {
    if (!atToken(TokenKind.IDENTIFIER)) {
      builder.error("expected identifier after dot");
      syncPast(EXPR_TERMINATOR_SET);
      return marker;
    }
    parseTargetOrReferenceIdentifier();
    if (atToken(TokenKind.LPAREN)) {
      parseFuncallSuffix();
      marker.done(BuildElementTypes.FUNCALL_EXPRESSION);
    } else {
      marker.done(BuildElementTypes.DOT_EXPRESSION);
    }
    return marker.precede();
  }

  // substring_suffix ::= '[' expression? ':' expression? ':' expression? ']'
  private PsiBuilder.Marker parseSubstringSuffix(PsiBuilder.Marker marker) {
    if (!atToken(TokenKind.COLON)) {
      PsiBuilder.Marker pos = builder.mark();
      parseExpression(false);
      pos.done(BuildElementTypes.POSITIONAL);
    }
    while (!matches(TokenKind.RBRACKET)) {
      if (expect(TokenKind.COLON)) {
        if (!atAnyOfTokens(TokenKind.COLON, TokenKind.RBRACKET)) {
          parseNonTupleExpression();
        }
      } else {
        syncPast(EXPR_LIST_TERMINATOR_SET);
        break;
      }
    }
    marker.done(BuildElementTypes.FUNCALL_EXPRESSION);
    return marker.precede();
  }

  private void parseTargetOrReferenceIdentifier() {
    if (!atToken(TokenKind.IDENTIFIER)) {
      builder.error("expected an identifier");
      return;
    }
    // TODO: handle assigning to a list of targets (e.g. "a,b = 1")
    TokenKind next = kindFromElement(builder.lookAhead(1));
    if (next == TokenKind.EQUALS || next == TokenKind.IN) {
      buildTokenElement(BuildElementTypes.TARGET_EXPRESSION);
    } else {
      buildTokenElement(BuildElementTypes.REFERENCE_EXPRESSION);
    }
  }

  private void parsePrimary() {
    TokenKind current = currentToken();
    switch (current) {
      case INT:
        buildTokenElement(BuildElementTypes.INTEGER_LITERAL);
        return;
      case STRING:
        parseStringLiteral(true);
        return;
      case IDENTIFIER:
        PsiBuilder.Marker marker = builder.mark();
        String tokenText = builder.getTokenText();
        parseTargetOrReferenceIdentifier();
        if (atToken(TokenKind.LPAREN)) {
          parseFuncallSuffix();
          marker.done(getFuncallExpressionType(tokenText));
        } else {
          marker.drop();
        }
        return;
      case LBRACKET:
        parseListMaker();
        return;
      case LBRACE:
        parseDictExpression();
        return;
      case LPAREN:
        marker = builder.mark();
        builder.advanceLexer();
        if (matches(TokenKind.RPAREN)) {
          marker.done(BuildElementTypes.TUPLE_EXPRESSION);
          return;
        }
        parseExpression(true);
        expect(TokenKind.RPAREN, true);
        marker.done(BuildElementTypes.PARENTHESIZED_EXPRESSION);
        return;
      case MINUS:
        marker = builder.mark();
        builder.advanceLexer();
        parsePrimaryWithSuffix();
        marker.done(BuildElementTypes.POSITIONAL);
        return;
      default:
        builder.error("expected an expression");
        syncPast(EXPR_TERMINATOR_SET);
    }
  }

  /** funcall_suffix ::= '(' arg_list? ')' arg_list ::= ((arg ',')* arg ','? )? */
  private void parseFuncallSuffix() {
    PsiBuilder.Marker mark = builder.mark();
    expect(TokenKind.LPAREN, true);
    if (matches(TokenKind.RPAREN)) {
      mark.done(BuildElementTypes.ARGUMENT_LIST);
      return;
    }
    parseFuncallArgument();
    while (!atAnyOfTokens(FUNCALL_TERMINATOR_SET)) {
      expect(TokenKind.COMMA);
      if (atAnyOfTokens(FUNCALL_TERMINATOR_SET)) {
        break;
      }
      parseFuncallArgument();
    }
    expect(TokenKind.RPAREN, true);
    mark.done(BuildElementTypes.ARGUMENT_LIST);
  }

  private BuildElementType getFuncallExpressionType(String functionName) {
    if ("glob".equals(functionName)) {
      return BuildElementTypes.GLOB_EXPRESSION;
    }
    return BuildElementTypes.FUNCALL_EXPRESSION;
  }

  protected void parseFunctionParameters() {
    if (atToken(TokenKind.RPAREN)) {
      return;
    }
    parseFunctionParameter();
    while (!atAnyOfTokens(FUNCALL_TERMINATOR_SET)) {
      expect(TokenKind.COMMA);
      if (atAnyOfTokens(FUNCALL_TERMINATOR_SET)) {
        break;
      }
      parseFunctionParameter();
    }
  }

  // arg ::= IDENTIFIER '=' nontupleexpr
  //       | expr
  //       | *args
  //       | **kwargs
  private void parseFuncallArgument() {
    PsiBuilder.Marker marker = builder.mark();
    if (matches(TokenKind.STAR_STAR)) {
      parseNonTupleExpression();
      marker.done(BuildElementTypes.STAR_STAR);
      return;
    }
    if (matches(TokenKind.STAR)) {
      parseNonTupleExpression();
      marker.done(BuildElementTypes.STAR);
      return;
    }
    if (matchesSequence(TokenKind.IDENTIFIER, TokenKind.EQUALS)) {
      parseNonTupleExpression();
      marker.done(BuildElementTypes.KEYWORD);
      return;
    }
    parseNonTupleExpression();
    marker.done(BuildElementTypes.POSITIONAL);
  }

  /** arg ::= IDENTIFIER ['=' nontupleexpr] */
  private void parseFunctionParameter() {
    PsiBuilder.Marker marker = builder.mark();
    if (matches(TokenKind.STAR_STAR)) {
      expectIdentifier("invalid parameter name");
      marker.done(BuildElementTypes.PARAM_STAR_STAR);
      return;
    }
    if (matches(TokenKind.STAR)) {
      if (atToken(TokenKind.IDENTIFIER)) {
        builder.advanceLexer();
      }
      marker.done(BuildElementTypes.PARAM_STAR);
      return;
    }
    expectIdentifier("invalid parameter name");
    if (matches(TokenKind.EQUALS)) {
      parseNonTupleExpression();
      marker.done(BuildElementTypes.PARAM_OPTIONAL);
      return;
    }
    marker.done(BuildElementTypes.PARAM_MANDATORY);
  }

  protected void expectIdentifier(String error) {
    expect(TokenKind.IDENTIFIER, error, true);
  }

  // list_maker ::= '[' ']'
  //               |'[' expr ']'
  //               |'[' expr expr_list ']'
  //               |'[' expr ('FOR' loop_variables 'IN' expr)+ ']'
  private void parseListMaker() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.LBRACKET);
    if (matches(TokenKind.RBRACKET)) {
      marker.done(BuildElementTypes.LIST_LITERAL);
      return;
    }
    parseNonTupleExpression();
    switch (currentToken()) {
      case RBRACKET:
        builder.advanceLexer();
        marker.done(BuildElementTypes.LIST_LITERAL);
        return;
      case FOR:
        parseComprehensionSuffix(TokenKind.RBRACKET);
        marker.done(BuildElementTypes.LIST_COMPREHENSION_EXPR);
        return;
      case COMMA:
        parseExpressionList(true);
        if (!matches(TokenKind.RBRACKET)) {
          builder.error("expected 'for' or ']'");
          syncPast(LIST_TERMINATOR_SET);
        }
        marker.done(BuildElementTypes.LIST_LITERAL);
        return;
      default:
        builder.error("expected ',', 'for' or ']'");
        syncPast(LIST_TERMINATOR_SET);
        marker.done(BuildElementTypes.LIST_LITERAL);
    }
  }

  // dict_expression ::= '{' '}'
  //                    |'{' dict_entry_list '}'
  //                    |'{' dict_entry 'FOR' loop_variables 'IN' expr '}'
  private void parseDictExpression() {
    PsiBuilder.Marker marker = builder.mark();
    expect(TokenKind.LBRACE, true);
    if (matches(TokenKind.RBRACE)) {
      marker.done(BuildElementTypes.DICTIONARY_LITERAL);
      return;
    }
    parseDictEntry();
    if (currentToken() == TokenKind.FOR) {
      parseComprehensionSuffix(TokenKind.RBRACE);
      marker.done(BuildElementTypes.LIST_COMPREHENSION_EXPR);
      return;
    }
    if (matches(TokenKind.COMMA)) {
      parseDictEntryList();
    }
    expect(TokenKind.RBRACE, true);
    marker.done(BuildElementTypes.DICTIONARY_LITERAL);
  }

  // dict_entry_list ::= ( (dict_entry ',')* dict_entry ','? )?
  private void parseDictEntryList() {
    if (atAnyOfTokens(DICT_TERMINATOR_SET)) {
      return;
    }
    parseDictEntry();
    while (matches(TokenKind.COMMA)) {
      if (atAnyOfTokens(DICT_TERMINATOR_SET)) {
        return;
      }
      parseDictEntry();
    }
  }

  // dict_entry ::= nontupleexpr ':' nontupleexpr
  private void parseDictEntry() {
    PsiBuilder.Marker marker = builder.mark();
    parseNonTupleExpression();
    expect(TokenKind.COLON);
    parseNonTupleExpression();
    marker.done(BuildElementTypes.DICTIONARY_ENTRY_LITERAL);
  }

  // comprehension_suffix ::= 'FOR' loop_variables 'IN' expr comprehension_suffix
  //                        | 'IF' expr comprehension_suffix
  //                        | ']'
  private void parseComprehensionSuffix(TokenKind closingBracket) {
    while (true) {
      if (matches(TokenKind.FOR)) {
        parseForLoopVariables();
        expect(TokenKind.IN);
        parseNonTupleExpression(0);
      } else if (matches(TokenKind.IF)) {
        parseExpression(false);
      } else if (matches(closingBracket)) {
        return;
      } else {
        builder.error("expected " + closingBracket + ", 'for' or 'if'");
        syncPast(EXPR_LIST_TERMINATOR_SET);
        return;
      }
    }
  }

  // Equivalent to 'exprlist' rule in Python grammar.
  // loop_variables ::= primary_with_suffix ( ',' primary_with_suffix )* ','?
  protected void parseForLoopVariables() {
    PsiBuilder.Marker marker = builder.mark();
    parsePrimaryWithSuffix();
    if (currentToken() != TokenKind.COMMA) {
      marker.drop();
      return;
    }
    while (matches(TokenKind.COMMA)) {
      if (atAnyOfTokens(EXPR_LIST_TERMINATOR_SET)) {
        break;
      }
      parsePrimaryWithSuffix();
    }
    marker.done(BuildElementTypes.LIST_LITERAL);
  }
}
