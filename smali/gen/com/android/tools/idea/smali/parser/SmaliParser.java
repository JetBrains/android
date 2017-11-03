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

// Generated from Smali.bnf, do not modify
package com.android.tools.idea.smali.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.smali.psi.SmaliTypes.*;
import static com.android.tools.idea.smali.parser.SmaliParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class SmaliParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == ACCESS_MODIFIER) {
      r = access_modifier(b, 0);
    }
    else if (t == ANNOTATION_PROPERTY) {
      r = annotation_property(b, 0);
    }
    else if (t == ANNOTATIONS_SPEC) {
      r = annotations_spec(b, 0);
    }
    else if (t == BOOL) {
      r = bool(b, 0);
    }
    else if (t == CLASS_NAME) {
      r = class_name(b, 0);
    }
    else if (t == CLASS_SPEC) {
      r = class_spec(b, 0);
    }
    else if (t == FIELD_NAME) {
      r = field_name(b, 0);
    }
    else if (t == FIELD_SPEC) {
      r = field_spec(b, 0);
    }
    else if (t == FIELD_VALUE) {
      r = field_value(b, 0);
    }
    else if (t == IMPLEMENTS_SPEC) {
      r = implements_spec(b, 0);
    }
    else if (t == METHOD_BODY) {
      r = method_body(b, 0);
    }
    else if (t == METHOD_SPEC) {
      r = method_spec(b, 0);
    }
    else if (t == METHOD_START) {
      r = method_start(b, 0);
    }
    else if (t == PARAMETER_DECLARATION) {
      r = parameter_declaration(b, 0);
    }
    else if (t == PRIMITIVE_TYPE) {
      r = primitive_type(b, 0);
    }
    else if (t == PROPERTY_VALUE) {
      r = property_value(b, 0);
    }
    else if (t == REGULAR_METHOD_START) {
      r = regular_method_start(b, 0);
    }
    else if (t == RETURN_TYPE) {
      r = return_type(b, 0);
    }
    else if (t == SINGLE_VALUE) {
      r = single_value(b, 0);
    }
    else if (t == SINGLE_VALUES) {
      r = single_values(b, 0);
    }
    else if (t == SOURCE_SPEC) {
      r = source_spec(b, 0);
    }
    else if (t == SUPER_SPEC) {
      r = super_spec(b, 0);
    }
    else if (t == VALUE_ARRAY) {
      r = value_array(b, 0);
    }
    else if (t == VOID_TYPE) {
      r = void_type(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return smali_file(b, l + 1);
  }

  /* ********************************************************** */
  // AM_PUBLIC | AM_PRIVATE | AM_PROTECTED | AM_STATIC | AM_FINAL | AM_SYNCHRONIZED | AM_VOLATILE | AM_TRANSIENT |
  //                     AM_NATIVE | AM_INTERFACE | AM_ABSTRACT | AM_BRIDGE | AM_SYNTHETIC
  public static boolean access_modifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "access_modifier")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ACCESS_MODIFIER, "<access modifier>");
    r = consumeToken(b, AM_PUBLIC);
    if (!r) r = consumeToken(b, AM_PRIVATE);
    if (!r) r = consumeToken(b, AM_PROTECTED);
    if (!r) r = consumeToken(b, AM_STATIC);
    if (!r) r = consumeToken(b, AM_FINAL);
    if (!r) r = consumeToken(b, AM_SYNCHRONIZED);
    if (!r) r = consumeToken(b, AM_VOLATILE);
    if (!r) r = consumeToken(b, AM_TRANSIENT);
    if (!r) r = consumeToken(b, AM_NATIVE);
    if (!r) r = consumeToken(b, AM_INTERFACE);
    if (!r) r = consumeToken(b, AM_ABSTRACT);
    if (!r) r = consumeToken(b, AM_BRIDGE);
    if (!r) r = consumeToken(b, AM_SYNTHETIC);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // DOT_ANNOTATION_END
  static boolean annotation_end(PsiBuilder b, int l) {
    return consumeToken(b, DOT_ANNOTATION_END);
  }

  /* ********************************************************** */
  // IDENTIFIER '=' property_value
  public static boolean annotation_property(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_property")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && consumeToken(b, "=");
    r = r && property_value(b, l + 1);
    exit_section_(b, m, ANNOTATION_PROPERTY, r);
    return r;
  }

  /* ********************************************************** */
  // DOT_ANNOTATION 'system' class_name
  //                      annotation_property*
  //                      annotation_end
  public static boolean annotations_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotations_spec")) return false;
    if (!nextTokenIs(b, DOT_ANNOTATION)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ANNOTATIONS_SPEC, null);
    r = consumeToken(b, DOT_ANNOTATION);
    p = r; // pin = 1
    r = r && report_error_(b, consumeToken(b, "system"));
    r = p && report_error_(b, class_name(b, l + 1)) && r;
    r = p && report_error_(b, annotations_spec_3(b, l + 1)) && r;
    r = p && annotation_end(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // annotation_property*
  private static boolean annotations_spec_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotations_spec_3")) return false;
    int c = current_position_(b);
    while (true) {
      if (!annotation_property(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "annotations_spec_3", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // (annotations_spec (COMMENT)*)+
  static boolean annotations_specs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotations_specs")) return false;
    if (!nextTokenIs(b, DOT_ANNOTATION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = annotations_specs_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!annotations_specs_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "annotations_specs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // annotations_spec (COMMENT)*
  private static boolean annotations_specs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotations_specs_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = annotations_spec(b, l + 1);
    r = r && annotations_specs_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean annotations_specs_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotations_specs_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "annotations_specs_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean bool(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bool")) return false;
    if (!nextTokenIs(b, "<bool>", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOOL, "<bool>");
    r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // JAVA_IDENTIFIER
  public static boolean class_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_name")) return false;
    if (!nextTokenIs(b, JAVA_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, JAVA_IDENTIFIER);
    exit_section_(b, m, CLASS_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // DOT_CLASS (access_modifier)* class_name
  public static boolean class_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_spec")) return false;
    if (!nextTokenIs(b, DOT_CLASS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CLASS_SPEC, null);
    r = consumeToken(b, DOT_CLASS);
    p = r; // pin = 1
    r = r && report_error_(b, class_spec_1(b, l + 1));
    r = p && class_name(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (access_modifier)*
  private static boolean class_spec_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_spec_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!class_spec_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "class_spec_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (access_modifier)
  private static boolean class_spec_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_spec_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = access_modifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'constructor' ('<clinit>'|'<init>') parameter_declaration return_type?
  static boolean constructor_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "constructor");
    r = r && constructor_start_1(b, l + 1);
    r = r && parameter_declaration(b, l + 1);
    r = r && constructor_start_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<clinit>'|'<init>'
  private static boolean constructor_start_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_start_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "<clinit>");
    if (!r) r = consumeToken(b, "<init>");
    exit_section_(b, m, null, r);
    return r;
  }

  // return_type?
  private static boolean constructor_start_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_start_3")) return false;
    return_type(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean field_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_name")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, FIELD_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // DOT_FIELD (access_modifier)* field_name':'field_type (field_value)?
  public static boolean field_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_spec")) return false;
    if (!nextTokenIs(b, DOT_FIELD)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FIELD_SPEC, null);
    r = consumeToken(b, DOT_FIELD);
    p = r; // pin = 1
    r = r && report_error_(b, field_spec_1(b, l + 1));
    r = p && report_error_(b, field_name(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, ":")) && r;
    r = p && report_error_(b, field_type(b, l + 1)) && r;
    r = p && field_spec_5(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (access_modifier)*
  private static boolean field_spec_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_spec_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!field_spec_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "field_spec_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (access_modifier)
  private static boolean field_spec_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_spec_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = access_modifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (field_value)?
  private static boolean field_spec_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_spec_5")) return false;
    field_spec_5_0(b, l + 1);
    return true;
  }

  // (field_value)
  private static boolean field_spec_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_spec_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = field_value(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (field_spec (COMMENT)*)+
  static boolean field_specs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_specs")) return false;
    if (!nextTokenIs(b, DOT_FIELD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = field_specs_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!field_specs_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "field_specs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // field_spec (COMMENT)*
  private static boolean field_specs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_specs_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = field_spec(b, l + 1);
    r = r && field_specs_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean field_specs_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_specs_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "field_specs_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // class_name | primitive_type
  static boolean field_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_type")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = class_name(b, l + 1);
    if (!r) r = primitive_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '=' single_value
  public static boolean field_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_value")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FIELD_VALUE, "<field value>");
    r = consumeToken(b, "=");
    p = r; // pin = 1
    r = r && single_value(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DOT_IMPLEMENTS class_name
  public static boolean implements_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_spec")) return false;
    if (!nextTokenIs(b, DOT_IMPLEMENTS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IMPLEMENTS_SPEC, null);
    r = consumeToken(b, DOT_IMPLEMENTS);
    p = r; // pin = 1
    r = r && class_name(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (implements_spec (COMMENT)*)+
  static boolean implements_specs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_specs")) return false;
    if (!nextTokenIs(b, DOT_IMPLEMENTS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = implements_specs_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!implements_specs_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "implements_specs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // implements_spec (COMMENT)*
  private static boolean implements_specs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_specs_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = implements_spec(b, l + 1);
    r = r && implements_specs_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean implements_specs_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_specs_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "implements_specs_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  public static boolean method_body(PsiBuilder b, int l) {
    Marker m = enter_section_(b, l, _NONE_, METHOD_BODY, null);
    exit_section_(b, l, m, true, false, method_recover_parser_);
    return true;
  }

  /* ********************************************************** */
  // DOT_METHOD_END
  static boolean method_end(PsiBuilder b, int l) {
    return consumeToken(b, DOT_METHOD_END);
  }

  /* ********************************************************** */
  // !(method_end)
  static boolean method_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !method_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (method_end)
  private static boolean method_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_end(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DOT_METHOD (access_modifier)* method_start
  //                 method_body
  //                 method_end
  public static boolean method_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_spec")) return false;
    if (!nextTokenIs(b, DOT_METHOD)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, METHOD_SPEC, null);
    r = consumeToken(b, DOT_METHOD);
    p = r; // pin = 1
    r = r && report_error_(b, method_spec_1(b, l + 1));
    r = p && report_error_(b, method_start(b, l + 1)) && r;
    r = p && report_error_(b, method_body(b, l + 1)) && r;
    r = p && method_end(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (access_modifier)*
  private static boolean method_spec_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_spec_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!method_spec_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "method_spec_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (access_modifier)
  private static boolean method_spec_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_spec_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = access_modifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (method_spec (COMMENT)*)+
  static boolean method_specs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_specs")) return false;
    if (!nextTokenIs(b, DOT_METHOD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_specs_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!method_specs_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "method_specs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // method_spec (COMMENT)*
  private static boolean method_specs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_specs_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_spec(b, l + 1);
    r = r && method_specs_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean method_specs_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_specs_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "method_specs_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // constructor_start | regular_method_start
  public static boolean method_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_start")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, METHOD_START, "<method start>");
    r = constructor_start(b, l + 1);
    if (!r) r = regular_method_start(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // class_name | primitive_type
  static boolean parameterList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = class_name(b, l + 1);
    if (!r) r = primitive_type(b, l + 1);
    exit_section_(b, l, m, r, false, parameterListRecover_parser_);
    return r;
  }

  /* ********************************************************** */
  // !(')')
  static boolean parameterListRecover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterListRecover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, R_PARENTHESIS);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // L_PARENTHESIS parameterList? R_PARENTHESIS
  public static boolean parameter_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_declaration")) return false;
    if (!nextTokenIs(b, L_PARENTHESIS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PARAMETER_DECLARATION, null);
    r = consumeToken(b, L_PARENTHESIS);
    p = r; // pin = 1
    r = r && report_error_(b, parameter_declaration_1(b, l + 1));
    r = p && consumeToken(b, R_PARENTHESIS) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // parameterList?
  private static boolean parameter_declaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_declaration_1")) return false;
    parameterList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'Z' /* boolean */ | 'B' /* byte */ | 'C' /* char */ | 'D' /* double */ | 'F' /* float */ | 'I' /* int */ | 'J' /* long */
  //                    | 'S'
  public static boolean primitive_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primitive_type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIMITIVE_TYPE, "<primitive type>");
    r = consumeToken(b, "Z");
    if (!r) r = consumeToken(b, "B");
    if (!r) r = consumeToken(b, "C");
    if (!r) r = consumeToken(b, "D");
    if (!r) r = consumeToken(b, "F");
    if (!r) r = consumeToken(b, "I");
    if (!r) r = consumeToken(b, "J");
    if (!r) r = consumeToken(b, "S");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // single_value | value_array
  public static boolean property_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_value")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PROPERTY_VALUE, "<property value>");
    r = single_value(b, l + 1);
    if (!r) r = value_array(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER parameter_declaration return_type?
  public static boolean regular_method_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regular_method_start")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && parameter_declaration(b, l + 1);
    r = r && regular_method_start_2(b, l + 1);
    exit_section_(b, m, REGULAR_METHOD_START, r);
    return r;
  }

  // return_type?
  private static boolean regular_method_start_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regular_method_start_2")) return false;
    return_type(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // class_name | primitive_type | void_type
  public static boolean return_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "return_type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RETURN_TYPE, "<return type>");
    r = class_name(b, l + 1);
    if (!r) r = primitive_type(b, l + 1);
    if (!r) r = void_type(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTED_STRING | class_name | REGULAR_NUMBER| HEX_NUMBER | CHAR | bool
  public static boolean single_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_value")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SINGLE_VALUE, "<single value>");
    r = consumeToken(b, DOUBLE_QUOTED_STRING);
    if (!r) r = class_name(b, l + 1);
    if (!r) r = consumeToken(b, REGULAR_NUMBER);
    if (!r) r = consumeToken(b, HEX_NUMBER);
    if (!r) r = consumeToken(b, CHAR);
    if (!r) r = bool(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // single_value (',' single_value)*
  public static boolean single_values(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_values")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SINGLE_VALUES, "<single values>");
    r = single_value(b, l + 1);
    r = r && single_values_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (',' single_value)*
  private static boolean single_values_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_values_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!single_values_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "single_values_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // ',' single_value
  private static boolean single_values_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_values_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && single_value(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // class_spec
  //                (COMMENT)*
  //                super_spec
  //                (COMMENT)*
  //                (source_spec)?
  //                (COMMENT)*
  //                (implements_specs)?
  //                (annotations_specs)?
  //                (COMMENT)*
  //                (field_specs)?
  //                (COMMENT)*
  //                (method_specs)?
  //                (COMMENT)*
  static boolean smali_file(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file")) return false;
    if (!nextTokenIs(b, DOT_CLASS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = class_spec(b, l + 1);
    r = r && smali_file_1(b, l + 1);
    r = r && super_spec(b, l + 1);
    r = r && smali_file_3(b, l + 1);
    r = r && smali_file_4(b, l + 1);
    r = r && smali_file_5(b, l + 1);
    r = r && smali_file_6(b, l + 1);
    r = r && smali_file_7(b, l + 1);
    r = r && smali_file_8(b, l + 1);
    r = r && smali_file_9(b, l + 1);
    r = r && smali_file_10(b, l + 1);
    r = r && smali_file_11(b, l + 1);
    r = r && smali_file_12(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean smali_file_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (COMMENT)*
  private static boolean smali_file_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_3")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_3", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (source_spec)?
  private static boolean smali_file_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_4")) return false;
    smali_file_4_0(b, l + 1);
    return true;
  }

  // (source_spec)
  private static boolean smali_file_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = source_spec(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean smali_file_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_5")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_5", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (implements_specs)?
  private static boolean smali_file_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_6")) return false;
    smali_file_6_0(b, l + 1);
    return true;
  }

  // (implements_specs)
  private static boolean smali_file_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = implements_specs(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (annotations_specs)?
  private static boolean smali_file_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_7")) return false;
    smali_file_7_0(b, l + 1);
    return true;
  }

  // (annotations_specs)
  private static boolean smali_file_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = annotations_specs(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean smali_file_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_8")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_8", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (field_specs)?
  private static boolean smali_file_9(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_9")) return false;
    smali_file_9_0(b, l + 1);
    return true;
  }

  // (field_specs)
  private static boolean smali_file_9_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_9_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = field_specs(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean smali_file_10(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_10")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_10", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (method_specs)?
  private static boolean smali_file_11(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_11")) return false;
    smali_file_11_0(b, l + 1);
    return true;
  }

  // (method_specs)
  private static boolean smali_file_11_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_11_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_specs(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMENT)*
  private static boolean smali_file_12(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "smali_file_12")) return false;
    int c = current_position_(b);
    while (true) {
      if (!consumeToken(b, COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "smali_file_12", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // DOT_SOURCE DOUBLE_QUOTED_STRING
  public static boolean source_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "source_spec")) return false;
    if (!nextTokenIs(b, DOT_SOURCE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SOURCE_SPEC, null);
    r = consumeTokens(b, 1, DOT_SOURCE, DOUBLE_QUOTED_STRING);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DOT_SUPER class_name
  public static boolean super_spec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "super_spec")) return false;
    if (!nextTokenIs(b, DOT_SUPER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SUPER_SPEC, null);
    r = consumeToken(b, DOT_SUPER);
    p = r; // pin = 1
    r = r && class_name(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // L_CURLY (single_values)? R_CURLY
  public static boolean value_array(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_array")) return false;
    if (!nextTokenIs(b, L_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, VALUE_ARRAY, null);
    r = consumeToken(b, L_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, value_array_1(b, l + 1));
    r = p && consumeToken(b, R_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (single_values)?
  private static boolean value_array_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_array_1")) return false;
    value_array_1_0(b, l + 1);
    return true;
  }

  // (single_values)
  private static boolean value_array_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_array_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = single_values(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'V'
  public static boolean void_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "void_type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VOID_TYPE, "<void type>");
    r = consumeToken(b, "V");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  final static Parser method_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return method_recover(b, l + 1);
    }
  };
  final static Parser parameterListRecover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return parameterListRecover(b, l + 1);
    }
  };
}
