// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.*;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DeviceSpecParser implements PsiParser, LightPsiParser {

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
    create_token_set_(CHIN_SIZE_PARAM, DPI_PARAM, HEIGHT_PARAM, ID_PARAM,
      IS_ROUND_PARAM, NAME_PARAM, ORIENTATION_PARAM, PARAM,
      PARENT_PARAM, SHAPE_PARAM, UNIT_PARAM, WIDTH_PARAM),
  };

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean boolean_t(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "boolean_t")) return false;
    if (!nextTokenIs(b, "<boolean t>", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOOLEAN_T, "<boolean t>");
    r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // CHIN_SIZE_KEYWORD EQUALS size_t
  public static boolean chin_size_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "chin_size_param")) return false;
    if (!nextTokenIs(b, CHIN_SIZE_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CHIN_SIZE_KEYWORD, EQUALS);
    r = r && size_t(b, l + 1);
    exit_section_(b, m, CHIN_SIZE_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // DPI_KEYWORD EQUALS NUMERIC_T
  public static boolean dpi_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dpi_param")) return false;
    if (!nextTokenIs(b, DPI_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DPI_KEYWORD, EQUALS, NUMERIC_T);
    exit_section_(b, m, DPI_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // HEIGHT_KEYWORD EQUALS size_t
  public static boolean height_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "height_param")) return false;
    if (!nextTokenIs(b, HEIGHT_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, HEIGHT_KEYWORD, EQUALS);
    r = r && size_t(b, l + 1);
    exit_section_(b, m, HEIGHT_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // ID_KEYWORD EQUALS STRING_T
  public static boolean id_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "id_param")) return false;
    if (!nextTokenIs(b, ID_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID_KEYWORD, EQUALS, STRING_T);
    exit_section_(b, m, ID_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // IS_ROUND_KEYWORD EQUALS boolean_t
  public static boolean is_round_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_round_param")) return false;
    if (!nextTokenIs(b, IS_ROUND_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IS_ROUND_KEYWORD, EQUALS);
    r = r && boolean_t(b, l + 1);
    exit_section_(b, m, IS_ROUND_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // NAME_KEYWORD EQUALS STRING_T
  public static boolean name_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_param")) return false;
    if (!nextTokenIs(b, NAME_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, NAME_KEYWORD, EQUALS, STRING_T);
    exit_section_(b, m, NAME_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // ORIENTATION_KEYWORD EQUALS orientation_t
  public static boolean orientation_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "orientation_param")) return false;
    if (!nextTokenIs(b, ORIENTATION_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ORIENTATION_KEYWORD, EQUALS);
    r = r && orientation_t(b, l + 1);
    exit_section_(b, m, ORIENTATION_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // LANDSCAPE_KEYWORD | PORTRAIT_KEYWORD | SQUARE_KEYWORD
  public static boolean orientation_t(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "orientation_t")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ORIENTATION_T, "<orientation t>");
    r = consumeToken(b, LANDSCAPE_KEYWORD);
    if (!r) r = consumeToken(b, PORTRAIT_KEYWORD);
    if (!r) r = consumeToken(b, SQUARE_KEYWORD);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // parent_param
  //    | id_param
  //    | name_param
  //    | width_param
  //    | height_param
  //    | shape_param // Deprecated, just here to be compatible with the old parser
  //    | unit_param // Deprecated, just here to be compatible with the old parser
  //    | orientation_param
  //    | is_round_param
  //    | chin_size_param
  //    | dpi_param
  public static boolean param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "param")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PARAM, "<param>");
    r = parent_param(b, l + 1);
    if (!r) r = id_param(b, l + 1);
    if (!r) r = name_param(b, l + 1);
    if (!r) r = width_param(b, l + 1);
    if (!r) r = height_param(b, l + 1);
    if (!r) r = shape_param(b, l + 1);
    if (!r) r = unit_param(b, l + 1);
    if (!r) r = orientation_param(b, l + 1);
    if (!r) r = is_round_param(b, l + 1);
    if (!r) r = chin_size_param(b, l + 1);
    if (!r) r = dpi_param(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // PARENT_KEYWORD EQUALS STRING_T
  public static boolean parent_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parent_param")) return false;
    if (!nextTokenIs(b, PARENT_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, PARENT_KEYWORD, EQUALS, STRING_T);
    exit_section_(b, m, PARENT_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // SPEC_KEYWORD COLON spec | ID_KEYWORD COLON STRING_T | NAME_KEYWORD COLON STRING_T
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = root_0(b, l + 1);
    if (!r) r = parseTokens(b, 0, ID_KEYWORD, COLON, STRING_T);
    if (!r) r = parseTokens(b, 0, NAME_KEYWORD, COLON, STRING_T);
    exit_section_(b, m, null, r);
    return r;
  }

  // SPEC_KEYWORD COLON spec
  private static boolean root_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SPEC_KEYWORD, COLON);
    r = r && spec(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // "Shape" EQUALS ("Normal" | "Square" | "Round" | "Chin")
  public static boolean shape_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shape_param")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SHAPE_PARAM, "<shape param>");
    r = consumeToken(b, "Shape");
    r = r && consumeToken(b, EQUALS);
    r = r && shape_param_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // "Normal" | "Square" | "Round" | "Chin"
  private static boolean shape_param_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shape_param_2")) return false;
    boolean r;
    r = consumeToken(b, "Normal");
    if (!r) r = consumeToken(b, "Square");
    if (!r) r = consumeToken(b, "Round");
    if (!r) r = consumeToken(b, "Chin");
    return r;
  }

  /* ********************************************************** */
  // NUMERIC_T (unit)?
  public static boolean size_t(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "size_t")) return false;
    if (!nextTokenIs(b, NUMERIC_T)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NUMERIC_T);
    r = r && size_t_1(b, l + 1);
    exit_section_(b, m, SIZE_T, r);
    return r;
  }

  // (unit)?
  private static boolean size_t_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "size_t_1")) return false;
    size_t_1_0(b, l + 1);
    return true;
  }

  // (unit)
  private static boolean size_t_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "size_t_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unit(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // param (COMMA param)*
  public static boolean spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spec")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SPEC, "<spec>");
    r = param(b, l + 1);
    r = r && spec_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA param)*
  private static boolean spec_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spec_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!spec_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "spec_1", c)) break;
    }
    return true;
  }

  // COMMA param
  private static boolean spec_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spec_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && param(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // PX | DP
  public static boolean unit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unit")) return false;
    if (!nextTokenIs(b, "<unit>", DP, PX)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNIT, "<unit>");
    r = consumeToken(b, PX);
    if (!r) r = consumeToken(b, DP);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // UNIT_KEYWORD EQUALS unit
  public static boolean unit_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unit_param")) return false;
    if (!nextTokenIs(b, UNIT_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, UNIT_KEYWORD, EQUALS);
    r = r && unit(b, l + 1);
    exit_section_(b, m, UNIT_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // WIDTH_KEYWORD EQUALS size_t
  public static boolean width_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "width_param")) return false;
    if (!nextTokenIs(b, WIDTH_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, WIDTH_KEYWORD, EQUALS);
    r = r && size_t(b, l + 1);
    exit_section_(b, m, WIDTH_PARAM, r);
    return r;
  }

}
