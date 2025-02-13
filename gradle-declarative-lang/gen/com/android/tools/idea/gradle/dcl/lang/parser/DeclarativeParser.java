/*
 * Copyright (C) 2024 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from declarative.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.dcl.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.*;
import static com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DeclarativeParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return entries(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ASSIGNABLE_BARE, ASSIGNABLE_PROPERTY, ASSIGNABLE_QUALIFIED),
    create_token_set_(BARE, PROPERTY, QUALIFIED),
    create_token_set_(BARE_RECEIVER, PROPERTY_RECEIVER, QUALIFIED_RECEIVER),
    create_token_set_(FACTORY_RECEIVER, RECEIVER_PREFIXED_FACTORY, SIMPLE_FACTORY),
  };

  /* ********************************************************** */
  // (identifier OP_EQ)? rvalue
  public static boolean argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENT, "<argument>");
    r = argument_0(b, l + 1);
    r = r && rvalue(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (identifier OP_EQ)?
  private static boolean argument_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_0")) return false;
    argument_0_0(b, l + 1);
    return true;
  }

  // identifier OP_EQ
  private static boolean argument_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = identifier(b, l + 1);
    r = r && consumeToken(b, OP_EQ);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (argument (OP_COMMA argument)*)?
  public static boolean argumentsList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList")) return false;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENTS_LIST, "<arguments list>");
    argumentsList_0(b, l + 1);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // argument (OP_COMMA argument)*
  private static boolean argumentsList_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = argument(b, l + 1);
    r = r && argumentsList_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (OP_COMMA argument)*
  private static boolean argumentsList_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!argumentsList_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "argumentsList_0_1", c)) break;
    }
    return true;
  }

  // OP_COMMA argument
  private static boolean argumentsList_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OP_COMMA);
    r = r && argument(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // lvalue OP_EQ rvalue
  public static boolean assignment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ASSIGNMENT, null);
    r = lvalue(b, l + 1);
    r = r && consumeToken(b, OP_EQ);
    p = r; // pin = 2
    r = r && rvalue(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // block_head block_group
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_head(b, l + 1);
    r = r && block_group(b, l + 1);
    exit_section_(b, m, BLOCK, r);
    return r;
  }

  /* ********************************************************** */
  // block_entry <<atSameLine (SEMI <<atSameLine block_entry>>)>>* SEMI?
  static boolean block_entries(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entries")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_entry(b, l + 1);
    r = r && block_entries_1(b, l + 1);
    r = r && block_entries_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<atSameLine (SEMI <<atSameLine block_entry>>)>>*
  private static boolean block_entries_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entries_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!atSameLine(b, l + 1, DeclarativeParser::block_entries_1_0_0)) break;
      if (!empty_element_parsed_guard_(b, "block_entries_1", c)) break;
    }
    return true;
  }

  // SEMI <<atSameLine block_entry>>
  private static boolean block_entries_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entries_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMI);
    r = r && atSameLine(b, l + 1, DeclarativeParser::block_entry);
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMI?
  private static boolean block_entries_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entries_2")) return false;
    consumeToken(b, SEMI);
    return true;
  }

  /* ********************************************************** */
  // !'}' entry
  static boolean block_entry(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entry")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_entry_0(b, l + 1);
    r = r && entry(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'}'
  private static boolean block_entry_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_entry_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, OP_RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // OP_LBRACE block_entries? newline_block_entries* OP_RBRACE
  public static boolean block_group(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_group")) return false;
    if (!nextTokenIs(b, OP_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BLOCK_GROUP, null);
    r = consumeToken(b, OP_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, block_group_1(b, l + 1));
    r = p && report_error_(b, block_group_2(b, l + 1)) && r;
    r = p && consumeToken(b, OP_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // block_entries?
  private static boolean block_group_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_group_1")) return false;
    block_entries(b, l + 1);
    return true;
  }

  // newline_block_entries*
  private static boolean block_group_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_group_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!newline_block_entries(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_group_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // embedded_factory | identifier
  static boolean block_head(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_head")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    r = embedded_factory(b, l + 1);
    if (!r) r = identifier(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // private_factory
  public static boolean embedded_factory(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "embedded_factory")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = private_factory(b, l + 1);
    exit_section_(b, m, EMBEDDED_FACTORY, r);
    return r;
  }

  /* ********************************************************** */
  // one_line_entries*
  static boolean entries(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entries")) return false;
    while (true) {
      int c = current_position_(b);
      if (!one_line_entries(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "entries", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // !<<eof>> !(OP_RBRACE|OP_RPAREN) (assignment | block | factory)
  static boolean entry(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = entry_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, entry_1(b, l + 1));
    r = p && entry_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, DeclarativeParser::entry_recover);
    return r || p;
  }

  // !<<eof>>
  private static boolean entry_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !eof(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // !(OP_RBRACE|OP_RPAREN)
  private static boolean entry_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !entry_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // OP_RBRACE|OP_RPAREN
  private static boolean entry_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_1_0")) return false;
    boolean r;
    r = consumeToken(b, OP_RBRACE);
    if (!r) r = consumeToken(b, OP_RPAREN);
    return r;
  }

  // assignment | block | factory
  private static boolean entry_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_2")) return false;
    boolean r;
    r = assignment(b, l + 1);
    if (!r) r = block(b, l + 1);
    if (!r) r = factory(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // !(token|OP_RBRACE|OP_RPAREN|SEMI)
  static boolean entry_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !entry_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // token|OP_RBRACE|OP_RPAREN|SEMI
  private static boolean entry_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_recover_0")) return false;
    boolean r;
    r = consumeToken(b, TOKEN);
    if (!r) r = consumeToken(b, OP_RBRACE);
    if (!r) r = consumeToken(b, OP_RPAREN);
    if (!r) r = consumeToken(b, SEMI);
    return r;
  }

  /* ********************************************************** */
  // factory_receiver | factory_property_receiver
  static boolean factory(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "factory")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    r = factory_receiver(b, l + 1, -1);
    if (!r) r = factory_property_receiver(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // property_receiver OP_DOT private_factory
  public static boolean factory_property_receiver(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "factory_property_receiver")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_receiver(b, l + 1, -1);
    r = r && consumeToken(b, OP_DOT);
    r = r && private_factory(b, l + 1);
    exit_section_(b, m, FACTORY_PROPERTY_RECEIVER, r);
    return r;
  }

  /* ********************************************************** */
  // token
  public static boolean identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "identifier")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, TOKEN);
    exit_section_(b, m, IDENTIFIER, r);
    return r;
  }

  /* ********************************************************** */
  // multiline_string_literal | one_line_string_literal | double_literal | integer_literal | long_literal | unsigned_long | unsigned_integer | boolean | null
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL, "<literal>");
    r = consumeToken(b, MULTILINE_STRING_LITERAL);
    if (!r) r = consumeToken(b, ONE_LINE_STRING_LITERAL);
    if (!r) r = consumeToken(b, DOUBLE_LITERAL);
    if (!r) r = consumeToken(b, INTEGER_LITERAL);
    if (!r) r = consumeToken(b, LONG_LITERAL);
    if (!r) r = consumeToken(b, UNSIGNED_LONG);
    if (!r) r = consumeToken(b, UNSIGNED_INTEGER);
    if (!r) r = consumeToken(b, BOOLEAN);
    if (!r) r = consumeToken(b, NULL);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // assignable_property
  static boolean lvalue(PsiBuilder b, int l) {
    return assignable_property(b, l + 1, -1);
  }

  /* ********************************************************** */
  // <<atNewLine block_entries+>>
  static boolean newline_block_entries(PsiBuilder b, int l) {
    return atNewLine(b, l + 1, DeclarativeParser::newline_block_entries_0_0);
  }

  // block_entries+
  private static boolean newline_block_entries_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newline_block_entries_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_entries(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!block_entries(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "newline_block_entries_0_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<atNewLine (entry <<atSameLine (SEMI <<atSameLine entry>>)>>* SEMI?)>>
  static boolean one_line_entries(PsiBuilder b, int l) {
    return atNewLine(b, l + 1, DeclarativeParser::one_line_entries_0_0);
  }

  // entry <<atSameLine (SEMI <<atSameLine entry>>)>>* SEMI?
  private static boolean one_line_entries_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "one_line_entries_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = entry(b, l + 1);
    r = r && one_line_entries_0_0_1(b, l + 1);
    r = r && one_line_entries_0_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<atSameLine (SEMI <<atSameLine entry>>)>>*
  private static boolean one_line_entries_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "one_line_entries_0_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!atSameLine(b, l + 1, DeclarativeParser::one_line_entries_0_0_1_0_0)) break;
      if (!empty_element_parsed_guard_(b, "one_line_entries_0_0_1", c)) break;
    }
    return true;
  }

  // SEMI <<atSameLine entry>>
  private static boolean one_line_entries_0_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "one_line_entries_0_0_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMI);
    r = r && atSameLine(b, l + 1, DeclarativeParser::entry);
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMI?
  private static boolean one_line_entries_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "one_line_entries_0_0_2")) return false;
    consumeToken(b, SEMI);
    return true;
  }

  /* ********************************************************** */
  // identifier OP_LPAREN argumentsList OP_RPAREN
  static boolean private_factory(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "private_factory")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = identifier(b, l + 1);
    r = r && consumeToken(b, OP_LPAREN);
    p = r; // pin = 2
    r = r && report_error_(b, argumentsList(b, l + 1));
    r = p && consumeToken(b, OP_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // factory | property | literal
  static boolean rvalue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rvalue")) return false;
    boolean r;
    r = factory(b, l + 1);
    if (!r) r = property(b, l + 1, -1);
    if (!r) r = literal(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // Expression root: assignable_property
  // Operator priority table:
  // 0: POSTFIX(assignable_qualified)
  // 1: ATOM(assignable_bare)
  public static boolean assignable_property(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "assignable_property")) return false;
    addVariant(b, "<assignable property>");
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<assignable property>");
    r = assignable_bare(b, l + 1);
    p = r;
    r = r && assignable_property_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean assignable_property_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "assignable_property_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && assignable_qualified_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, ASSIGNABLE_QUALIFIED, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // OP_DOT identifier
  private static boolean assignable_qualified_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignable_qualified_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, OP_DOT);
    r = r && identifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // identifier
  public static boolean assignable_bare(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignable_bare")) return false;
    if (!nextTokenIsSmart(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = identifier(b, l + 1);
    exit_section_(b, m, ASSIGNABLE_BARE, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: factory_receiver
  // Operator priority table:
  // 0: POSTFIX(receiver_prefixed_factory)
  // 1: ATOM(simple_factory)
  public static boolean factory_receiver(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "factory_receiver")) return false;
    addVariant(b, "<factory receiver>");
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<factory receiver>");
    r = simple_factory(b, l + 1);
    p = r;
    r = r && factory_receiver_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean factory_receiver_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "factory_receiver_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && receiver_prefixed_factory_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, RECEIVER_PREFIXED_FACTORY, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // OP_DOT private_factory
  private static boolean receiver_prefixed_factory_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "receiver_prefixed_factory_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, OP_DOT);
    r = r && private_factory(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // private_factory
  public static boolean simple_factory(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_factory")) return false;
    if (!nextTokenIsSmart(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = private_factory(b, l + 1);
    exit_section_(b, m, SIMPLE_FACTORY, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: property
  // Operator priority table:
  // 0: POSTFIX(qualified)
  // 1: ATOM(bare)
  public static boolean property(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "property")) return false;
    addVariant(b, "<property>");
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<property>");
    r = bare(b, l + 1);
    p = r;
    r = r && property_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean property_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "property_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && qualified_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, QUALIFIED, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // OP_DOT identifier
  private static boolean qualified_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, OP_DOT);
    r = r && identifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // identifier
  public static boolean bare(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bare")) return false;
    if (!nextTokenIsSmart(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = identifier(b, l + 1);
    exit_section_(b, m, BARE, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: property_receiver
  // Operator priority table:
  // 0: POSTFIX(qualified_receiver)
  // 1: ATOM(bare_receiver)
  public static boolean property_receiver(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "property_receiver")) return false;
    addVariant(b, "<property receiver>");
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<property receiver>");
    r = bare_receiver(b, l + 1);
    p = r;
    r = r && property_receiver_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean property_receiver_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "property_receiver_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && notBeforeLParen(b, l + 1, DeclarativeParser::qualified_receiver_0_0)) {
        r = true;
        exit_section_(b, l, m, QUALIFIED_RECEIVER, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // OP_DOT identifier
  private static boolean qualified_receiver_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_receiver_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, OP_DOT);
    r = r && identifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // identifier
  public static boolean bare_receiver(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bare_receiver")) return false;
    if (!nextTokenIsSmart(b, TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = identifier(b, l + 1);
    exit_section_(b, m, BARE_RECEIVER, r);
    return r;
  }

}
