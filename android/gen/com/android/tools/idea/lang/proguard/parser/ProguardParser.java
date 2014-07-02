// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.proguard.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.diagnostic.Logger;
import static com.android.tools.idea.lang.proguard.psi.ProguardTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ProguardParser implements PsiParser {

  public static final Logger LOG_ = Logger.getInstance("com.android.tools.idea.lang.proguard.parser.ProguardParser");

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    if (root_ == COMMENT) {
      result_ = comment(builder_, 0);
    }
    else if (root_ == FLAG) {
      result_ = flag(builder_, 0);
    }
    else if (root_ == JAVA_SECTION) {
      result_ = javaSection(builder_, 0);
    }
    else if (root_ == MULTI_LINE_FLAG) {
      result_ = multiLineFlag(builder_, 0);
    }
    else if (root_ == SINGLE_LINE_FLAG) {
      result_ = singleLineFlag(builder_, 0);
    }
    else {
      result_ = parse_root_(root_, builder_, 0);
    }
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
    return builder_.getTreeBuilt();
  }

  protected boolean parse_root_(final IElementType root_, final PsiBuilder builder_, final int level_) {
    return proguardFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // LINE_CMT
  public static boolean comment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment")) return false;
    if (!nextTokenIs(builder_, LINE_CMT)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINE_CMT);
    exit_section_(builder_, marker_, COMMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // multiLineFlag | singleLineFlag comment?
  public static boolean flag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flag")) return false;
    if (!nextTokenIs(builder_, FLAG_NAME)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = multiLineFlag(builder_, level_ + 1);
    if (!result_) result_ = flag_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, FLAG, result_);
    return result_;
  }

  // singleLineFlag comment?
  private static boolean flag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flag_1")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = singleLineFlag(builder_, level_ + 1);
    result_ = result_ && flag_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // comment?
  private static boolean flag_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flag_1_1")) return false;
    comment(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // OPEN_BRACE CRLF? (JAVA_DECL CRLF?)* CLOSE_BRACE
  public static boolean javaSection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "javaSection")) return false;
    if (!nextTokenIs(builder_, OPEN_BRACE)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_BRACE);
    result_ = result_ && javaSection_1(builder_, level_ + 1);
    result_ = result_ && javaSection_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_BRACE);
    exit_section_(builder_, marker_, JAVA_SECTION, result_);
    return result_;
  }

  // CRLF?
  private static boolean javaSection_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "javaSection_1")) return false;
    consumeToken(builder_, CRLF);
    return true;
  }

  // (JAVA_DECL CRLF?)*
  private static boolean javaSection_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "javaSection_2")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!javaSection_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "javaSection_2", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  // JAVA_DECL CRLF?
  private static boolean javaSection_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "javaSection_2_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, JAVA_DECL);
    result_ = result_ && javaSection_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CRLF?
  private static boolean javaSection_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "javaSection_2_0_1")) return false;
    consumeToken(builder_, CRLF);
    return true;
  }

  /* ********************************************************** */
  // FLAG_NAME FLAG_ARG* javaSection
  public static boolean multiLineFlag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiLineFlag")) return false;
    if (!nextTokenIs(builder_, FLAG_NAME)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, FLAG_NAME);
    result_ = result_ && multiLineFlag_1(builder_, level_ + 1);
    result_ = result_ && javaSection(builder_, level_ + 1);
    exit_section_(builder_, marker_, MULTI_LINE_FLAG, result_);
    return result_;
  }

  // FLAG_ARG*
  private static boolean multiLineFlag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiLineFlag_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!consumeToken(builder_, FLAG_ARG)) break;
      if (!empty_element_parsed_guard_(builder_, "multiLineFlag_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  /* ********************************************************** */
  // (comment CRLF | flag CRLF | WS? CRLF)*
  //                  (comment      | flag      | WS?     )?
  static boolean proguardFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = proguardFile_0(builder_, level_ + 1);
    result_ = result_ && proguardFile_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (comment CRLF | flag CRLF | WS? CRLF)*
  private static boolean proguardFile_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!proguardFile_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "proguardFile_0", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  // comment CRLF | flag CRLF | WS? CRLF
  private static boolean proguardFile_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = proguardFile_0_0_0(builder_, level_ + 1);
    if (!result_) result_ = proguardFile_0_0_1(builder_, level_ + 1);
    if (!result_) result_ = proguardFile_0_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // comment CRLF
  private static boolean proguardFile_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0_0_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = comment(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CRLF);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // flag CRLF
  private static boolean proguardFile_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0_0_1")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = flag(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CRLF);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // WS? CRLF
  private static boolean proguardFile_0_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0_0_2")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = proguardFile_0_0_2_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CRLF);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // WS?
  private static boolean proguardFile_0_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_0_0_2_0")) return false;
    consumeToken(builder_, WS);
    return true;
  }

  // (comment      | flag      | WS?     )?
  private static boolean proguardFile_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_1")) return false;
    proguardFile_1_0(builder_, level_ + 1);
    return true;
  }

  // comment      | flag      | WS?
  private static boolean proguardFile_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = comment(builder_, level_ + 1);
    if (!result_) result_ = flag(builder_, level_ + 1);
    if (!result_) result_ = proguardFile_1_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // WS?
  private static boolean proguardFile_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proguardFile_1_0_2")) return false;
    consumeToken(builder_, WS);
    return true;
  }

  /* ********************************************************** */
  // FLAG_NAME FLAG_ARG*
  public static boolean singleLineFlag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "singleLineFlag")) return false;
    if (!nextTokenIs(builder_, FLAG_NAME)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, FLAG_NAME);
    result_ = result_ && singleLineFlag_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, SINGLE_LINE_FLAG, result_);
    return result_;
  }

  // FLAG_ARG*
  private static boolean singleLineFlag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "singleLineFlag_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!consumeToken(builder_, FLAG_ARG)) break;
      if (!empty_element_parsed_guard_(builder_, "singleLineFlag_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

}
