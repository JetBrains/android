/*
 * Copyright (C) 2019 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from proguardR8.bnf. Do not edit it manually.

package com.android.tools.idea.lang.proguardR8.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ProguardR8Parser implements PsiParser, LightPsiParser {

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
    boolean result;
    if (type == QUALIFIED_NAME) {
      result = qualifiedName(builder, level + 1);
    }
    else {
      result = root(builder, level + 1);
    }
    return result;
  }

  /* ********************************************************** */
  // AT qualifiedName
  public static boolean annotation_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "annotation_name")) return false;
    if (!nextTokenIs(builder, AT)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, AT);
    result = result && qualifiedName(builder, level + 1);
    exit_section_(builder, marker, ANNOTATION_NAME, result);
    return result;
  }

  /* ********************************************************** */
  // ASTERISK
  public static boolean any_field_or_method(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "any_field_or_method")) return false;
    if (!nextTokenIs(builder, ASTERISK)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ASTERISK);
    exit_section_(builder, marker, ANY_FIELD_OR_METHOD, result);
    return result;
  }

  /* ********************************************************** */
  // ANY_PRIMITIVE_TYPE_
  public static boolean any_primitive_type(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "any_primitive_type")) return false;
    if (!nextTokenIs(builder, ANY_PRIMITIVE_TYPE_)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ANY_PRIMITIVE_TYPE_);
    exit_section_(builder, marker, ANY_PRIMITIVE_TYPE, result);
    return result;
  }

  /* ********************************************************** */
  // ANY_TYPE_
  public static boolean any_type(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "any_type")) return false;
    if (!nextTokenIs(builder, ANY_TYPE_)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ANY_TYPE_);
    exit_section_(builder, marker, ANY_TYPE, result);
    return result;
  }

  /* ********************************************************** */
  // class_name parameters
  static boolean class_description(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_description")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = class_name(builder, level + 1);
    pinned = result; // pin = 1
    result = result && parameters(builder, level + 1);
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  /* ********************************************************** */
  // ((type class_member_name) | class_member_name) !('.'|'$')
  static boolean class_member_core(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_core")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = class_member_core_0(builder, level + 1);
    result = result && class_member_core_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // (type class_member_name) | class_member_name
  private static boolean class_member_core_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_core_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = class_member_core_0_0(builder, level + 1);
    if (!result) result = class_member_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // type class_member_name
  private static boolean class_member_core_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_core_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = type(builder, level + 1);
    result = result && class_member_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // !('.'|'$')
  private static boolean class_member_core_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_core_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !class_member_core_1_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // '.'|'$'
  private static boolean class_member_core_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_core_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DOT);
    if (!result) result = consumeToken(builder, DOLLAR);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // java_identifier_
  public static boolean class_member_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_member_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_MEMBER_NAME, "<class member name>");
    result = java_identifier_(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // "!"?(public|final|abstract)
  public static boolean class_modifier(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_modifier")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_MODIFIER, "<class modifier>");
    result = class_modifier_0(builder, level + 1);
    result = result && class_modifier_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // "!"?
  private static boolean class_modifier_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_modifier_0")) return false;
    consumeToken(builder, EM);
    return true;
  }

  // public|final|abstract
  private static boolean class_modifier_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_modifier_1")) return false;
    boolean result;
    result = consumeToken(builder, PUBLIC);
    if (!result) result = consumeToken(builder, FINAL);
    if (!result) result = consumeToken(builder, ABSTRACT);
    return result;
  }

  /* ********************************************************** */
  // qualifiedName
  public static boolean class_name(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_name")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_NAME, "<class name>");
    result = qualifiedName(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // OPEN_BRACE java CLOSE_BRACE
  public static boolean class_specification_body(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_body")) return false;
    if (!nextTokenIs(builder, OPEN_BRACE)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_SPECIFICATION_BODY, null);
    result = consumeToken(builder, OPEN_BRACE);
    pinned = result; // pin = 1
    result = result && report_error_(builder, java(builder, level + 1));
    result = pinned && consumeToken(builder, CLOSE_BRACE) && result;
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  /* ********************************************************** */
  // annotation_name? class_modifier* class_type class_name ((extends|implements) annotation_name? class_name)?
  public static boolean class_specification_header(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_SPECIFICATION_HEADER, "<class specification header>");
    result = class_specification_header_0(builder, level + 1);
    result = result && class_specification_header_1(builder, level + 1);
    result = result && class_type(builder, level + 1);
    pinned = result; // pin = class_type
    result = result && report_error_(builder, class_name(builder, level + 1));
    result = pinned && class_specification_header_4(builder, level + 1) && result;
    exit_section_(builder, level, marker, result, pinned, ProguardR8Parser::not_open_brace_or_new_flag);
    return result || pinned;
  }

  // annotation_name?
  private static boolean class_specification_header_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_0")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  // class_modifier*
  private static boolean class_specification_header_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!class_modifier(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "class_specification_header_1", pos)) break;
    }
    return true;
  }

  // ((extends|implements) annotation_name? class_name)?
  private static boolean class_specification_header_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_4")) return false;
    class_specification_header_4_0(builder, level + 1);
    return true;
  }

  // (extends|implements) annotation_name? class_name
  private static boolean class_specification_header_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = class_specification_header_4_0_0(builder, level + 1);
    result = result && class_specification_header_4_0_1(builder, level + 1);
    result = result && class_name(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // extends|implements
  private static boolean class_specification_header_4_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_4_0_0")) return false;
    boolean result;
    result = consumeToken(builder, EXTENDS);
    if (!result) result = consumeToken(builder, IMPLEMENTS);
    return result;
  }

  // annotation_name?
  private static boolean class_specification_header_4_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_specification_header_4_0_1")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // "!"?(interface|class|enum)
  public static boolean class_type(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_type")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, CLASS_TYPE, "<class type>");
    result = class_type_0(builder, level + 1);
    result = result && class_type_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // "!"?
  private static boolean class_type_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_type_0")) return false;
    consumeToken(builder, EM);
    return true;
  }

  // interface|class|enum
  private static boolean class_type_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "class_type_1")) return false;
    boolean result;
    result = consumeToken(builder, INTERFACE);
    if (!result) result = consumeToken(builder, CLASS);
    if (!result) result = consumeToken(builder, ENUM);
    return result;
  }

  /* ********************************************************** */
  // annotation_name? fields_modifier* class_member_core
  public static boolean field(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "field")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FIELD, "<field>");
    result = field_0(builder, level + 1);
    result = result && field_1(builder, level + 1);
    result = result && class_member_core(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // annotation_name?
  private static boolean field_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "field_0")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  // fields_modifier*
  private static boolean field_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "field_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!fields_modifier(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "field_1", pos)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // "!"?(public|private|protected|static|volatile|transient|final)
  public static boolean fields_modifier(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_modifier")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FIELDS_MODIFIER, "<fields modifier>");
    result = fields_modifier_0(builder, level + 1);
    result = result && fields_modifier_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // "!"?
  private static boolean fields_modifier_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_modifier_0")) return false;
    consumeToken(builder, EM);
    return true;
  }

  // public|private|protected|static|volatile|transient|final
  private static boolean fields_modifier_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_modifier_1")) return false;
    boolean result;
    result = consumeToken(builder, PUBLIC);
    if (!result) result = consumeToken(builder, PRIVATE);
    if (!result) result = consumeToken(builder, PROTECTED);
    if (!result) result = consumeToken(builder, STATIC);
    if (!result) result = consumeToken(builder, VOLATILE);
    if (!result) result = consumeToken(builder, TRANSIENT);
    if (!result) result = consumeToken(builder, FINAL);
    return result;
  }

  /* ********************************************************** */
  // (field | (annotation_name? fields_modifier* (<fields>|any_field_or_method))) !parameters
  public static boolean fields_specification(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FIELDS_SPECIFICATION, "<fields specification>");
    result = fields_specification_0(builder, level + 1);
    result = result && fields_specification_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // field | (annotation_name? fields_modifier* (<fields>|any_field_or_method))
  private static boolean fields_specification_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = field(builder, level + 1);
    if (!result) result = fields_specification_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // annotation_name? fields_modifier* (<fields>|any_field_or_method)
  private static boolean fields_specification_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_0_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = fields_specification_0_1_0(builder, level + 1);
    result = result && fields_specification_0_1_1(builder, level + 1);
    result = result && fields_specification_0_1_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // annotation_name?
  private static boolean fields_specification_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_0_1_0")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  // fields_modifier*
  private static boolean fields_specification_0_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_0_1_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!fields_modifier(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "fields_specification_0_1_1", pos)) break;
    }
    return true;
  }

  // <fields>|any_field_or_method
  private static boolean fields_specification_0_1_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_0_1_2")) return false;
    boolean result;
    result = consumeToken(builder, _FIELDS_);
    if (!result) result = any_field_or_method(builder, level + 1);
    return result;
  }

  // !parameters
  private static boolean fields_specification_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "fields_specification_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !parameters(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // "!"?(FILE_NAME|FILE_NAME_SINGLE_QUOTED|FILE_NAME_DOUBLE_QUOTED|UNTERMINATED_FILE_NAME_SINGLE_QUOTED|UNTERMINATED_FILE_NAME_DOUBLE_QUOTED)
  static boolean file_(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = file__0(builder, level + 1);
    result = result && file__1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // "!"?
  private static boolean file__0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file__0")) return false;
    consumeToken(builder, EM);
    return true;
  }

  // FILE_NAME|FILE_NAME_SINGLE_QUOTED|FILE_NAME_DOUBLE_QUOTED|UNTERMINATED_FILE_NAME_SINGLE_QUOTED|UNTERMINATED_FILE_NAME_DOUBLE_QUOTED
  private static boolean file__1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file__1")) return false;
    boolean result;
    result = consumeToken(builder, FILE_NAME);
    if (!result) result = consumeToken(builder, FILE_NAME_SINGLE_QUOTED);
    if (!result) result = consumeToken(builder, FILE_NAME_DOUBLE_QUOTED);
    if (!result) result = consumeToken(builder, UNTERMINATED_FILE_NAME_SINGLE_QUOTED);
    if (!result) result = consumeToken(builder, UNTERMINATED_FILE_NAME_DOUBLE_QUOTED);
    return result;
  }

  /* ********************************************************** */
  // file_list (',' file_list)*
  public static boolean file_filter(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_filter")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FILE_FILTER, "<file filter>");
    result = file_list(builder, level + 1);
    result = result && file_filter_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // (',' file_list)*
  private static boolean file_filter_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_filter_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!file_filter_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "file_filter_1", pos)) break;
    }
    return true;
  }

  // ',' file_list
  private static boolean file_filter_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_filter_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && file_list(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // file_ ((':'|';') file_)*
  static boolean file_list(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_list")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = file_(builder, level + 1);
    result = result && file_list_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ((':'|';') file_)*
  private static boolean file_list_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_list_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!file_list_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "file_list_1", pos)) break;
    }
    return true;
  }

  // (':'|';') file_
  private static boolean file_list_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_list_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = file_list_1_0_0(builder, level + 1);
    result = result && file_(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ':'|';'
  private static boolean file_list_1_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "file_list_1_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COLON);
    if (!result) result = consumeToken(builder, SEMICOLON);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // file_list ('(' file_filter ')')?
  public static boolean flag_argument(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "flag_argument")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, FLAG_ARGUMENT, "<flag argument>");
    result = file_list(builder, level + 1);
    result = result && flag_argument_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ('(' file_filter ')')?
  private static boolean flag_argument_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "flag_argument_1")) return false;
    flag_argument_1_0(builder, level + 1);
    return true;
  }

  // '(' file_filter ')'
  private static boolean flag_argument_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "flag_argument_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, LPAREN);
    result = result && file_filter(builder, level + 1);
    result = result && consumeToken(builder, RPAREN);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // (<init>|<clinit>) parameters
  static boolean init_description(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "init_description")) return false;
    if (!nextTokenIs(builder, "", _CLINIT_, _INIT_)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = init_description_0(builder, level + 1);
    result = result && parameters(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // <init>|<clinit>
  private static boolean init_description_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "init_description_0")) return false;
    boolean result;
    result = consumeToken(builder, _INIT_);
    if (!result) result = consumeToken(builder, _CLINIT_);
    return result;
  }

  /* ********************************************************** */
  // (java_rule SEMICOLON)*
  static boolean java(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java")) return false;
    Marker marker = enter_section_(builder, level, _NONE_);
    while (true) {
      int pos = current_position_(builder);
      if (!java_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "java", pos)) break;
    }
    exit_section_(builder, level, marker, true, false, ProguardR8Parser::not_close_brace);
    return true;
  }

  // java_rule SEMICOLON
  private static boolean java_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_0")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_);
    result = java_rule(builder, level + 1);
    pinned = result; // pin = 1
    result = result && consumeToken(builder, SEMICOLON);
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  /* ********************************************************** */
  // JAVA_IDENTIFIER|JAVA_IDENTIFIER_WITH_WILDCARDS|ASTERISK
  static boolean java_identifier_(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_identifier_")) return false;
    boolean result;
    result = consumeToken(builder, JAVA_IDENTIFIER);
    if (!result) result = consumeToken(builder, JAVA_IDENTIFIER_WITH_WILDCARDS);
    if (!result) result = consumeToken(builder, ASTERISK);
    return result;
  }

  /* ********************************************************** */
  // boolean|byte|char|short|int|long|float|double|void
  public static boolean java_primitive(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_primitive")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, JAVA_PRIMITIVE, "<java primitive>");
    result = consumeToken(builder, BOOLEAN);
    if (!result) result = consumeToken(builder, BYTE);
    if (!result) result = consumeToken(builder, CHAR);
    if (!result) result = consumeToken(builder, SHORT);
    if (!result) result = consumeToken(builder, INT);
    if (!result) result = consumeToken(builder, LONG);
    if (!result) result = consumeToken(builder, FLOAT);
    if (!result) result = consumeToken(builder, DOUBLE);
    if (!result) result = consumeToken(builder, VOID);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // !(<<eof>>|CLOSE_BRACE|WHITE_SPACE) (fields_specification|method_specification)
  public static boolean java_rule(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_rule")) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, JAVA_RULE, "<java rule>");
    result = java_rule_0(builder, level + 1);
    pinned = result; // pin = 1
    result = result && java_rule_1(builder, level + 1);
    exit_section_(builder, level, marker, result, pinned, ProguardR8Parser::not_semicolon);
    return result || pinned;
  }

  // !(<<eof>>|CLOSE_BRACE|WHITE_SPACE)
  private static boolean java_rule_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_rule_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !java_rule_0_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // <<eof>>|CLOSE_BRACE|WHITE_SPACE
  private static boolean java_rule_0_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_rule_0_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = eof(builder, level + 1);
    if (!result) result = consumeToken(builder, CLOSE_BRACE);
    if (!result) result = consumeToken(builder, WHITE_SPACE);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // fields_specification|method_specification
  private static boolean java_rule_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "java_rule_1")) return false;
    boolean result;
    result = fields_specification(builder, level + 1);
    if (!result) result = method_specification(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // includedescriptorclasses|includecode|allowshrinking|allowoptimization|allowobfuscation
  public static boolean keep_option_modifier(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "keep_option_modifier")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, KEEP_OPTION_MODIFIER, "<keep option modifier>");
    result = consumeToken(builder, INCLUDEDESCRIPTORCLASSES);
    if (!result) result = consumeToken(builder, INCLUDECODE);
    if (!result) result = consumeToken(builder, ALLOWSHRINKING);
    if (!result) result = consumeToken(builder, ALLOWOPTIMIZATION);
    if (!result) result = consumeToken(builder, ALLOWOBFUSCATION);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // annotation_name? method_modifier* class_member_core parameters (return values)?
  public static boolean method(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, METHOD, "<method>");
    result = method_0(builder, level + 1);
    result = result && method_1(builder, level + 1);
    result = result && class_member_core(builder, level + 1);
    result = result && parameters(builder, level + 1);
    result = result && method_4(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // annotation_name?
  private static boolean method_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_0")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  // method_modifier*
  private static boolean method_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!method_modifier(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "method_1", pos)) break;
    }
    return true;
  }

  // (return values)?
  private static boolean method_4(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_4")) return false;
    method_4_0(builder, level + 1);
    return true;
  }

  // return values
  private static boolean method_4_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_4_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, RETURN, VALUES);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // "!"?(public|private|protected|static|synchronized|native|abstract|strictfp|final)
  public static boolean method_modifier(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_modifier")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, METHOD_MODIFIER, "<method modifier>");
    result = method_modifier_0(builder, level + 1);
    result = result && method_modifier_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // "!"?
  private static boolean method_modifier_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_modifier_0")) return false;
    consumeToken(builder, EM);
    return true;
  }

  // public|private|protected|static|synchronized|native|abstract|strictfp|final
  private static boolean method_modifier_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_modifier_1")) return false;
    boolean result;
    result = consumeToken(builder, PUBLIC);
    if (!result) result = consumeToken(builder, PRIVATE);
    if (!result) result = consumeToken(builder, PROTECTED);
    if (!result) result = consumeToken(builder, STATIC);
    if (!result) result = consumeToken(builder, SYNCHRONIZED);
    if (!result) result = consumeToken(builder, NATIVE);
    if (!result) result = consumeToken(builder, ABSTRACT);
    if (!result) result = consumeToken(builder, STRICTFP);
    if (!result) result = consumeToken(builder, FINAL);
    return result;
  }

  /* ********************************************************** */
  // method | (annotation_name? method_modifier* (<methods> | init_description | class_description |any_field_or_method))
  public static boolean method_specification(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_specification")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, METHOD_SPECIFICATION, "<method specification>");
    result = method(builder, level + 1);
    if (!result) result = method_specification_1(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // annotation_name? method_modifier* (<methods> | init_description | class_description |any_field_or_method)
  private static boolean method_specification_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_specification_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = method_specification_1_0(builder, level + 1);
    result = result && method_specification_1_1(builder, level + 1);
    result = result && method_specification_1_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // annotation_name?
  private static boolean method_specification_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_specification_1_0")) return false;
    annotation_name(builder, level + 1);
    return true;
  }

  // method_modifier*
  private static boolean method_specification_1_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_specification_1_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!method_modifier(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "method_specification_1_1", pos)) break;
    }
    return true;
  }

  // <methods> | init_description | class_description |any_field_or_method
  private static boolean method_specification_1_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "method_specification_1_2")) return false;
    boolean result;
    result = consumeToken(builder, _METHODS_);
    if (!result) result = init_description(builder, level + 1);
    if (!result) result = class_description(builder, level + 1);
    if (!result) result = any_field_or_method(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // !CLOSE_BRACE
  static boolean not_close_brace(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_close_brace")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !consumeToken(builder, CLOSE_BRACE);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // !FLAG
  static boolean not_flag(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_flag")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !consumeToken(builder, FLAG);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // !(OPEN_BRACE|FLAG)
  static boolean not_open_brace_or_new_flag(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_open_brace_or_new_flag")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !not_open_brace_or_new_flag_0(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // OPEN_BRACE|FLAG
  private static boolean not_open_brace_or_new_flag_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_open_brace_or_new_flag_0")) return false;
    boolean result;
    result = consumeToken(builder, OPEN_BRACE);
    if (!result) result = consumeToken(builder, FLAG);
    return result;
  }

  /* ********************************************************** */
  // !RPAREN
  static boolean not_right_paren(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_right_paren")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !consumeToken(builder, RPAREN);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // !SEMICOLON
  static boolean not_semicolon(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "not_semicolon")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NOT_);
    result = !consumeToken(builder, SEMICOLON);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  /* ********************************************************** */
  // LPAREN (ANY_TYPE_AND_NUM_OF_ARGS|type_list) RPAREN
  public static boolean parameters(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "parameters")) return false;
    if (!nextTokenIs(builder, LPAREN)) return false;
    boolean result, pinned;
    Marker marker = enter_section_(builder, level, _NONE_, PARAMETERS, null);
    result = consumeToken(builder, LPAREN);
    pinned = result; // pin = 1
    result = result && report_error_(builder, parameters_1(builder, level + 1));
    result = pinned && consumeToken(builder, RPAREN) && result;
    exit_section_(builder, level, marker, result, pinned, null);
    return result || pinned;
  }

  // ANY_TYPE_AND_NUM_OF_ARGS|type_list
  private static boolean parameters_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "parameters_1")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, ANY_TYPE_AND_NUM_OF_ARGS);
    if (!result) result = type_list(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // java_identifier_ ("." java_identifier_)* ("$" java_identifier_)?
  public static boolean qualifiedName(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "qualifiedName")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, QUALIFIED_NAME, "<qualified name>");
    result = java_identifier_(builder, level + 1);
    result = result && qualifiedName_1(builder, level + 1);
    result = result && qualifiedName_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // ("." java_identifier_)*
  private static boolean qualifiedName_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "qualifiedName_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!qualifiedName_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "qualifiedName_1", pos)) break;
    }
    return true;
  }

  // "." java_identifier_
  private static boolean qualifiedName_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "qualifiedName_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DOT);
    result = result && java_identifier_(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ("$" java_identifier_)?
  private static boolean qualifiedName_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "qualifiedName_2")) return false;
    qualifiedName_2_0(builder, level + 1);
    return true;
  }

  // "$" java_identifier_
  private static boolean qualifiedName_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "qualifiedName_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, DOLLAR);
    result = result && java_identifier_(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // (rule_with_class_specification | rule )*
  static boolean root(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "root")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!root_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "root", pos)) break;
    }
    return true;
  }

  // rule_with_class_specification | rule
  private static boolean root_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "root_0")) return false;
    boolean result;
    result = rule_with_class_specification(builder, level + 1);
    if (!result) result = rule(builder, level + 1);
    return result;
  }

  /* ********************************************************** */
  // FLAG (flag_argument ("," flag_argument)*)?
  public static boolean rule(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule")) return false;
    if (!nextTokenIs(builder, FLAG)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, FLAG);
    result = result && rule_1(builder, level + 1);
    exit_section_(builder, marker, RULE, result);
    return result;
  }

  // (flag_argument ("," flag_argument)*)?
  private static boolean rule_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_1")) return false;
    rule_1_0(builder, level + 1);
    return true;
  }

  // flag_argument ("," flag_argument)*
  private static boolean rule_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = flag_argument(builder, level + 1);
    result = result && rule_1_0_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ("," flag_argument)*
  private static boolean rule_1_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_1_0_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!rule_1_0_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "rule_1_0_1", pos)) break;
    }
    return true;
  }

  // "," flag_argument
  private static boolean rule_1_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_1_0_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && flag_argument(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  /* ********************************************************** */
  // FLAG  ("," keep_option_modifier)* class_specification_header class_specification_body?
  public static boolean rule_with_class_specification(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_with_class_specification")) return false;
    if (!nextTokenIs(builder, FLAG)) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, FLAG);
    result = result && rule_with_class_specification_1(builder, level + 1);
    result = result && class_specification_header(builder, level + 1);
    result = result && rule_with_class_specification_3(builder, level + 1);
    exit_section_(builder, marker, RULE_WITH_CLASS_SPECIFICATION, result);
    return result;
  }

  // ("," keep_option_modifier)*
  private static boolean rule_with_class_specification_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_with_class_specification_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!rule_with_class_specification_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "rule_with_class_specification_1", pos)) break;
    }
    return true;
  }

  // "," keep_option_modifier
  private static boolean rule_with_class_specification_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_with_class_specification_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && keep_option_modifier(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // class_specification_body?
  private static boolean rule_with_class_specification_3(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "rule_with_class_specification_3")) return false;
    class_specification_body(builder, level + 1);
    return true;
  }

  /* ********************************************************** */
  // any_type|any_primitive_type|((qualifiedName|java_primitive) "[]"?)
  public static boolean type(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type")) return false;
    boolean result;
    Marker marker = enter_section_(builder, level, _NONE_, TYPE, "<type>");
    result = any_type(builder, level + 1);
    if (!result) result = any_primitive_type(builder, level + 1);
    if (!result) result = type_2(builder, level + 1);
    exit_section_(builder, level, marker, result, false, null);
    return result;
  }

  // (qualifiedName|java_primitive) "[]"?
  private static boolean type_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_2")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = type_2_0(builder, level + 1);
    result = result && type_2_1(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // qualifiedName|java_primitive
  private static boolean type_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_2_0")) return false;
    boolean result;
    result = qualifiedName(builder, level + 1);
    if (!result) result = java_primitive(builder, level + 1);
    return result;
  }

  // "[]"?
  private static boolean type_2_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_2_1")) return false;
    consumeToken(builder, ARRAY);
    return true;
  }

  /* ********************************************************** */
  // (type ("," type)* ("," ANY_TYPE_AND_NUM_OF_ARGS)?)?
  public static boolean type_list(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list")) return false;
    Marker marker = enter_section_(builder, level, _NONE_, TYPE_LIST, "<type list>");
    type_list_0(builder, level + 1);
    exit_section_(builder, level, marker, true, false, ProguardR8Parser::not_right_paren);
    return true;
  }

  // type ("," type)* ("," ANY_TYPE_AND_NUM_OF_ARGS)?
  private static boolean type_list_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = type(builder, level + 1);
    result = result && type_list_0_1(builder, level + 1);
    result = result && type_list_0_2(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ("," type)*
  private static boolean type_list_0_1(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list_0_1")) return false;
    while (true) {
      int pos = current_position_(builder);
      if (!type_list_0_1_0(builder, level + 1)) break;
      if (!empty_element_parsed_guard_(builder, "type_list_0_1", pos)) break;
    }
    return true;
  }

  // "," type
  private static boolean type_list_0_1_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list_0_1_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeToken(builder, COMMA);
    result = result && type(builder, level + 1);
    exit_section_(builder, marker, null, result);
    return result;
  }

  // ("," ANY_TYPE_AND_NUM_OF_ARGS)?
  private static boolean type_list_0_2(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list_0_2")) return false;
    type_list_0_2_0(builder, level + 1);
    return true;
  }

  // "," ANY_TYPE_AND_NUM_OF_ARGS
  private static boolean type_list_0_2_0(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "type_list_0_2_0")) return false;
    boolean result;
    Marker marker = enter_section_(builder);
    result = consumeTokens(builder, 0, COMMA, ANY_TYPE_AND_NUM_OF_ARGS);
    exit_section_(builder, marker, null, result);
    return result;
  }

}
