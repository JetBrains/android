// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.logcat.filters.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class LogcatFilterParser implements PsiParser, LightPsiParser {

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
    return root(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(AND_EXPRESSION, EXPRESSION, LITERAL_EXPRESSION, OR_EXPRESSION,
      PAREN_EXPRESSION),
  };

  /* ********************************************************** */
  // expression *
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    while (true) {
      int c = current_position_(b);
      if (!expression(b, l + 1, -1)) break;
      if (!empty_element_parsed_guard_(b, "root", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // Expression root: expression
  // Operator priority table:
  // 0: BINARY(orExpression)
  // 1: BINARY(andExpression)
  // 2: ATOM(literalExpression)
  // 3: PREFIX(parenExpression)
  public static boolean expression(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression")) return false;
    addVariant(b, "<expression>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expression>");
    r = literalExpression(b, l + 1);
    if (!r) r = parenExpression(b, l + 1);
    p = r;
    r = r && expression_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expression_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && consumeTokenSmart(b, OR)) {
        r = expression(b, l, 0);
        exit_section_(b, l, m, OR_EXPRESSION, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, AND)) {
        r = expression(b, l, 1);
        exit_section_(b, l, m, AND_EXPRESSION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // KEY KVALUE
  //   |   STRING_KEY STRING_KVALUE
  //   |   REGEX_KEY REGEX_KVALUE
  //   |   VALUE
  public static boolean literalExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literalExpression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_EXPRESSION, "<literal expression>");
    r = parseTokensSmart(b, 0, KEY, KVALUE);
    if (!r) r = parseTokensSmart(b, 0, STRING_KEY, STRING_KVALUE);
    if (!r) r = parseTokensSmart(b, 0, REGEX_KEY, REGEX_KVALUE);
    if (!r) r = consumeTokenSmart(b, VALUE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  public static boolean parenExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parenExpression")) return false;
    if (!nextTokenIsSmart(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LPAREN);
    p = r;
    r = p && expression(b, l, -1);
    r = p && report_error_(b, consumeToken(b, RPAREN)) && r;
    exit_section_(b, l, m, PAREN_EXPRESSION, r, p, null);
    return r || p;
  }

}
