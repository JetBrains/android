// generatedFilesHeader.txt
package com.android.tools.idea.lang.multiDexKeep.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepPsiTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class MultiDexKeepParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType type, PsiBuilder builder) {
    parseLight(type, builder);
    return builder.getTreeBuilt();
  }

  public void parseLight(IElementType type, PsiBuilder builder) {
    boolean result;
    builder = adapt_builder_(type, builder, this, null);
    Marker marker = enter_section_(builder, 0, _COLLAPSE_, null);
    result = parse_root_(type, builder);
    exit_section_(builder, 0, marker, type, result, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType type, PsiBuilder builder) {
    return parse_root_(type, builder, 0);
  }

  static boolean parse_root_(IElementType type, PsiBuilder builder, int level) {
    return multiDexKeepFile(builder, level + 1);
  }

  /* ********************************************************** */
  // STRING
  public static boolean className(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "className")) return false;
    if (!nextTokenIs(builder, STRING)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, STRING);
    exit_section_(builder, marker, CLASS_NAME, result);
    return result;
  }

  /* ********************************************************** */
  // (className | EOL)*
  public static boolean classNames(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "classNames")) return false;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_NAMES, "<class names>");
    while (true) {
      int pos = current_position_(builder);
      if (!classNames_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "classNames", pos)) break;
    }
    exit_section_(builder, level, marker, true, false, null);
    return true;
  }

  // className | EOL
  private static boolean classNames_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "classNames_0")) return false;
    boolean result;
    result = className(builder, level + 1);
    if (!result) result = consumeToken(builder, EOL);
    return result;
  }

  /* ********************************************************** */
  // classNames
  static boolean multiDexKeepFile(PsiBuilder builder, int level) {
    return classNames(builder, level + 1);
  }

}
