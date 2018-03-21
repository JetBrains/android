/*
 * Copyright (C) 2017 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from Proguard.bnf. Do not edit it manually.

package com.android.tools.idea.lang.proguard.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.proguard.psi.ProguardTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ProguardParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == COMMENT) {
      r = comment(b, 0);
    }
    else if (t == FLAG) {
      r = flag(b, 0);
    }
    else if (t == JAVA_SECTION) {
      r = javaSection(b, 0);
    }
    else if (t == MULTI_LINE_FLAG) {
      r = multiLineFlag(b, 0);
    }
    else if (t == SINGLE_LINE_FLAG) {
      r = singleLineFlag(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return proguardFile(b, l + 1);
  }

  /* ********************************************************** */
  // LINE_CMT
  public static boolean comment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comment")) return false;
    if (!nextTokenIs(b, LINE_CMT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINE_CMT);
    exit_section_(b, m, COMMENT, r);
    return r;
  }

  /* ********************************************************** */
  // multiLineFlag | singleLineFlag comment?
  public static boolean flag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flag")) return false;
    if (!nextTokenIs(b, FLAG_NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = multiLineFlag(b, l + 1);
    if (!r) r = flag_1(b, l + 1);
    exit_section_(b, m, FLAG, r);
    return r;
  }

  // singleLineFlag comment?
  private static boolean flag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flag_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = singleLineFlag(b, l + 1);
    r = r && flag_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // comment?
  private static boolean flag_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flag_1_1")) return false;
    comment(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // OPEN_BRACE CRLF? (JAVA_DECL CRLF?)* CLOSE_BRACE
  public static boolean javaSection(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "javaSection")) return false;
    if (!nextTokenIs(b, OPEN_BRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPEN_BRACE);
    r = r && javaSection_1(b, l + 1);
    r = r && javaSection_2(b, l + 1);
    r = r && consumeToken(b, CLOSE_BRACE);
    exit_section_(b, m, JAVA_SECTION, r);
    return r;
  }

  // CRLF?
  private static boolean javaSection_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "javaSection_1")) return false;
    consumeToken(b, CRLF);
    return true;
  }

  // (JAVA_DECL CRLF?)*
  private static boolean javaSection_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "javaSection_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!javaSection_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "javaSection_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // JAVA_DECL CRLF?
  private static boolean javaSection_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "javaSection_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, JAVA_DECL);
    r = r && javaSection_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // CRLF?
  private static boolean javaSection_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "javaSection_2_0_1")) return false;
    consumeToken(b, CRLF);
    return true;
  }

  /* ********************************************************** */
  // FLAG_NAME FLAG_ARG* javaSection
  public static boolean multiLineFlag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiLineFlag")) return false;
    if (!nextTokenIs(b, FLAG_NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FLAG_NAME);
    r = r && multiLineFlag_1(b, l + 1);
    r = r && javaSection(b, l + 1);
    exit_section_(b, m, MULTI_LINE_FLAG, r);
    return r;
  }

  // FLAG_ARG*
  private static boolean multiLineFlag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiLineFlag_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, FLAG_ARG)) break;
      if (!empty_element_parsed_guard_(b, "multiLineFlag_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // (comment CRLF | flag CRLF | WS? CRLF)*
  //                  (comment      | flag      | WS?     )?
  static boolean proguardFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = proguardFile_0(b, l + 1);
    r = r && proguardFile_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (comment CRLF | flag CRLF | WS? CRLF)*
  private static boolean proguardFile_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0")) return false;
    int c = current_position_(b);
    while (true) {
      if (!proguardFile_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "proguardFile_0", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // comment CRLF | flag CRLF | WS? CRLF
  private static boolean proguardFile_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = proguardFile_0_0_0(b, l + 1);
    if (!r) r = proguardFile_0_0_1(b, l + 1);
    if (!r) r = proguardFile_0_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // comment CRLF
  private static boolean proguardFile_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = comment(b, l + 1);
    r = r && consumeToken(b, CRLF);
    exit_section_(b, m, null, r);
    return r;
  }

  // flag CRLF
  private static boolean proguardFile_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = flag(b, l + 1);
    r = r && consumeToken(b, CRLF);
    exit_section_(b, m, null, r);
    return r;
  }

  // WS? CRLF
  private static boolean proguardFile_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0_0_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = proguardFile_0_0_2_0(b, l + 1);
    r = r && consumeToken(b, CRLF);
    exit_section_(b, m, null, r);
    return r;
  }

  // WS?
  private static boolean proguardFile_0_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_0_0_2_0")) return false;
    consumeToken(b, WS);
    return true;
  }

  // (comment      | flag      | WS?     )?
  private static boolean proguardFile_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_1")) return false;
    proguardFile_1_0(b, l + 1);
    return true;
  }

  // comment      | flag      | WS?
  private static boolean proguardFile_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = comment(b, l + 1);
    if (!r) r = flag(b, l + 1);
    if (!r) r = proguardFile_1_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // WS?
  private static boolean proguardFile_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "proguardFile_1_0_2")) return false;
    consumeToken(b, WS);
    return true;
  }

  /* ********************************************************** */
  // FLAG_NAME FLAG_ARG*
  public static boolean singleLineFlag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "singleLineFlag")) return false;
    if (!nextTokenIs(b, FLAG_NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FLAG_NAME);
    r = r && singleLineFlag_1(b, l + 1);
    exit_section_(b, m, SINGLE_LINE_FLAG, r);
    return r;
  }

  // FLAG_ARG*
  private static boolean singleLineFlag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "singleLineFlag_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, FLAG_ARG)) break;
      if (!empty_element_parsed_guard_(b, "singleLineFlag_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

}
