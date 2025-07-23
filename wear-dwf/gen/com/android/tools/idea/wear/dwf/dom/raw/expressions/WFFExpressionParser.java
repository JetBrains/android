/*
 * Copyright (C) 2025 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from
// wear-dwf/src/com/android/tools/idea/wear/dwf/dom/raw/expressions/wff_expressions.bnf
// Do not edit it manually.
package com.android.tools.idea.wear.dwf.dom.raw.expressions;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.*;
import static com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class WFFExpressionParser implements PsiParser, LightPsiParser {

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
    boolean r;
    if (t == EXPR) {
      r = expr(b, l + 1, -1);
    }
    else {
      r = root(b, l + 1);
    }
    return r;
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(AND_EXPR, BIT_COMPL_EXPR, CALL_EXPR, CONDITIONAL_EXPR,
      DIV_EXPR, ELVIS_EXPR, EXPR, LITERAL_EXPR,
      MINUS_EXPR, MOD_EXPR, MUL_EXPR, OR_EXPR,
      PAREN_EXPR, PLUS_EXPR, UNARY_MIN_EXPR, UNARY_NOT_EXPR,
      UNARY_PLUS_EXPR),
  };

  /* ********************************************************** */
  // OPEN_PAREN [ !CLOSE_PAREN expr  (',' expr) * ] CLOSE_PAREN
  public static boolean arg_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list")) return false;
    if (!nextTokenIs(b, OPEN_PAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPEN_PAREN);
    r = r && arg_list_1(b, l + 1);
    r = r && consumeToken(b, CLOSE_PAREN);
    exit_section_(b, m, ARG_LIST, r);
    return r;
  }

  // [ !CLOSE_PAREN expr  (',' expr) * ]
  private static boolean arg_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list_1")) return false;
    arg_list_1_0(b, l + 1);
    return true;
  }

  // !CLOSE_PAREN expr  (',' expr) *
  private static boolean arg_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = arg_list_1_0_0(b, l + 1);
    r = r && expr(b, l + 1, -1);
    r = r && arg_list_1_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !CLOSE_PAREN
  private static boolean arg_list_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, CLOSE_PAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (',' expr) *
  private static boolean arg_list_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list_1_0_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!arg_list_1_0_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "arg_list_1_0_2", c)) break;
    }
    return true;
  }

  // ',' expr
  private static boolean arg_list_1_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list_1_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '<=' | '>=' | '==' | '!=' | '&&' | '||' | '<' | '>'
  public static boolean conditional_op(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_op")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONDITIONAL_OP, "<conditional op>");
    r = consumeToken(b, "<=");
    if (!r) r = consumeToken(b, ">=");
    if (!r) r = consumeToken(b, "==");
    if (!r) r = consumeToken(b, "!=");
    if (!r) r = consumeToken(b, "&&");
    if (!r) r = consumeToken(b, "||");
    if (!r) r = consumeToken(b, "<");
    if (!r) r = consumeToken(b, ">");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // OPEN_BRACKET ID CLOSE_BRACKET
  public static boolean data_source_or_configuration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "data_source_or_configuration")) return false;
    if (!nextTokenIs(b, OPEN_BRACKET)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DATA_SOURCE_OR_CONFIGURATION, null);
    r = consumeTokens(b, 2, OPEN_BRACKET, ID, CLOSE_BRACKET);
    p = r; // pin = 2
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // expr
  static boolean element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, WFFExpressionParser::element_recover);
    return r;
  }

  /* ********************************************************** */
  // !(OPEN_PAREN | OPEN_BRACKET | '+' | '-' | '!' | '~' | ID | NUMBER | QUOTED_STRING)
  static boolean element_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "element_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !element_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // OPEN_PAREN | OPEN_BRACKET | '+' | '-' | '!' | '~' | ID | NUMBER | QUOTED_STRING
  private static boolean element_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "element_recover_0")) return false;
    boolean r;
    r = consumeToken(b, OPEN_PAREN);
    if (!r) r = consumeToken(b, OPEN_BRACKET);
    if (!r) r = consumeToken(b, "+");
    if (!r) r = consumeToken(b, "-");
    if (!r) r = consumeToken(b, "!");
    if (!r) r = consumeToken(b, "~");
    if (!r) r = consumeToken(b, ID);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, QUOTED_STRING);
    return r;
  }

  /* ********************************************************** */
  // ID &OPEN_PAREN
  public static boolean function_id(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_id")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    r = r && function_id_1(b, l + 1);
    exit_section_(b, m, FUNCTION_ID, r);
    return r;
  }

  // &OPEN_PAREN
  private static boolean function_id_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_id_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, OPEN_PAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // element
  static boolean root(PsiBuilder b, int l) {
    return element(b, l + 1);
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: BINARY(plus_expr) BINARY(minus_expr)
  // 1: BINARY(conditional_expr) BINARY(elvis_expr)
  // 2: BINARY(or_expr) BINARY(and_expr)
  // 3: BINARY(mul_expr) BINARY(div_expr) BINARY(mod_expr)
  // 4: PREFIX(unary_plus_expr) PREFIX(unary_min_expr) PREFIX(unary_not_expr) PREFIX(bit_compl_expr)
  // 5: ATOM(call_expr)
  // 6: ATOM(literal_expr) PREFIX(paren_expr)
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = unary_plus_expr(b, l + 1);
    if (!r) r = unary_min_expr(b, l + 1);
    if (!r) r = unary_not_expr(b, l + 1);
    if (!r) r = bit_compl_expr(b, l + 1);
    if (!r) r = call_expr(b, l + 1);
    if (!r) r = literal_expr(b, l + 1);
    if (!r) r = paren_expr(b, l + 1);
    p = r;
    r = r && expr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && consumeTokenSmart(b, "+")) {
        r = expr(b, l, 0);
        exit_section_(b, l, m, PLUS_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, "-")) {
        r = expr(b, l, 0);
        exit_section_(b, l, m, MINUS_EXPR, r, true, null);
      }
      else if (g < 1 && conditional_op(b, l + 1)) {
        r = expr(b, l, 1);
        exit_section_(b, l, m, CONDITIONAL_EXPR, r, true, null);
      }
      else if (g < 1 && leftMarkerIs(b, CONDITIONAL_EXPR) && consumeTokenSmart(b, "?")) {
        r = report_error_(b, expr(b, l, 1));
        r = elvis_expr_1(b, l + 1) && r;
        exit_section_(b, l, m, ELVIS_EXPR, r, true, null);
      }
      else if (g < 2 && or_expr_0(b, l + 1)) {
        r = expr(b, l, 2);
        exit_section_(b, l, m, OR_EXPR, r, true, null);
      }
      else if (g < 2 && and_expr_0(b, l + 1)) {
        r = expr(b, l, 2);
        exit_section_(b, l, m, AND_EXPR, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, "*")) {
        r = expr(b, l, 3);
        exit_section_(b, l, m, MUL_EXPR, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, "/")) {
        r = expr(b, l, 3);
        exit_section_(b, l, m, DIV_EXPR, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, "%")) {
        r = expr(b, l, 3);
        exit_section_(b, l, m, MOD_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  public static boolean unary_plus_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_plus_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, "+");
    p = r;
    r = p && expr(b, l, 4);
    exit_section_(b, l, m, UNARY_PLUS_EXPR, r, p, null);
    return r || p;
  }

  public static boolean unary_min_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_min_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, "-");
    p = r;
    r = p && expr(b, l, 4);
    exit_section_(b, l, m, UNARY_MIN_EXPR, r, p, null);
    return r || p;
  }

  // ':' expr
  private static boolean elvis_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elvis_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '||' | '|'
  private static boolean or_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "or_expr_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, "||");
    if (!r) r = consumeTokenSmart(b, "|");
    return r;
  }

  // '&&' | '&'
  private static boolean and_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "and_expr_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, "&&");
    if (!r) r = consumeTokenSmart(b, "&");
    return r;
  }

  public static boolean unary_not_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_not_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, "!");
    p = r;
    r = p && expr(b, l, 4);
    exit_section_(b, l, m, UNARY_NOT_EXPR, r, p, null);
    return r || p;
  }

  public static boolean bit_compl_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bit_compl_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, "~");
    p = r;
    r = p && expr(b, l, 4);
    exit_section_(b, l, m, BIT_COMPL_EXPR, r, p, null);
    return r || p;
  }

  // function_id arg_list
  public static boolean call_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_expr")) return false;
    if (!nextTokenIsSmart(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = function_id(b, l + 1);
    r = r && arg_list(b, l + 1);
    exit_section_(b, m, CALL_EXPR, r);
    return r;
  }

  // NUMBER | QUOTED_STRING | ID | data_source_or_configuration | NULL
  public static boolean literal_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_EXPR, "<literal expr>");
    r = consumeTokenSmart(b, NUMBER);
    if (!r) r = consumeTokenSmart(b, QUOTED_STRING);
    if (!r) r = consumeTokenSmart(b, ID);
    if (!r) r = data_source_or_configuration(b, l + 1);
    if (!r) r = consumeTokenSmart(b, NULL);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  public static boolean paren_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_expr")) return false;
    if (!nextTokenIsSmart(b, OPEN_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, OPEN_PAREN);
    p = r;
    r = p && expr(b, l, -1);
    r = p && report_error_(b, consumeToken(b, CLOSE_PAREN)) && r;
    exit_section_(b, l, m, PAREN_EXPR, r, p, null);
    return r || p;
  }

}
