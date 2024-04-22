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
package com.android.tools.idea.gradle.declarative.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.*;
import static com.android.tools.idea.gradle.declarative.parser.DeclarativeParserUtil.*;
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
    create_token_set_(BARE, PROPERTY, QUALIFIED),
  };

  /* ********************************************************** */
  // (rvalue (OP_COMMA rvalue)*)?
  public static boolean argumentsList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList")) return false;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENTS_LIST, "<arguments list>");
    argumentsList_0(b, l + 1);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // rvalue (OP_COMMA rvalue)*
  private static boolean argumentsList_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = rvalue(b, l + 1);
    r = r && argumentsList_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (OP_COMMA rvalue)*
  private static boolean argumentsList_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!argumentsList_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "argumentsList_0_1", c)) break;
    }
    return true;
  }

  // OP_COMMA rvalue
  private static boolean argumentsList_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argumentsList_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OP_COMMA);
    r = r && rvalue(b, l + 1);
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
  // OP_LBRACE block_entry* OP_RBRACE
  public static boolean block_group(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_group")) return false;
    if (!nextTokenIs(b, OP_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BLOCK_GROUP, null);
    r = consumeToken(b, OP_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, block_group_1(b, l + 1));
    r = p && consumeToken(b, OP_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // block_entry*
  private static boolean block_group_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_group_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block_entry(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_group_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // factory | identifier
  static boolean block_head(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_head")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r;
    r = factory(b, l + 1);
    if (!r) r = identifier(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // entry*
  static boolean entries(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entries")) return false;
    while (true) {
      int c = current_position_(b);
      if (!entry(b, l + 1)) break;
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
  // !(token|OP_RBRACE|OP_RPAREN)
  static boolean entry_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !entry_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // token|OP_RBRACE|OP_RPAREN
  private static boolean entry_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entry_recover_0")) return false;
    boolean r;
    r = consumeToken(b, TOKEN);
    if (!r) r = consumeToken(b, OP_RBRACE);
    if (!r) r = consumeToken(b, OP_RPAREN);
    return r;
  }

  /* ********************************************************** */
  // identifier OP_LPAREN argumentsList OP_RPAREN
  public static boolean factory(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "factory")) return false;
    if (!nextTokenIs(b, TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FACTORY, null);
    r = identifier(b, l + 1);
    r = r && consumeToken(b, OP_LPAREN);
    p = r; // pin = 2
    r = r && report_error_(b, argumentsList(b, l + 1));
    r = p && consumeToken(b, OP_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
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
  // string | number | boolean
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL, "<literal>");
    r = consumeToken(b, STRING);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, BOOLEAN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // identifier
  static boolean lvalue(PsiBuilder b, int l) {
    return identifier(b, l + 1);
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

}
