/*
 * Copyright (C) 2022 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from Agsl.bnf. Do not edit it manually.
package com.android.tools.idea.lang.agsl.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.agsl.AgslTokenTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AgslParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return document(b, l + 1);
  }

  /* ********************************************************** */
  // token*
  static boolean document(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "document")) return false;
    while (true) {
      int c = current_position_(b);
      if (!token(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "document", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER_GL_PREFIX
  public static boolean glsl_identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "glsl_identifier")) return false;
    if (!nextTokenIs(b, IDENTIFIER_GL_PREFIX)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER_GL_PREFIX);
    exit_section_(b, m, GLSL_IDENTIFIER, r);
    return r;
  }

  /* ********************************************************** */
  // BREAK
  //   |   CONTINUE
  //   |   DO
  //   |   FOR
  //   |   WHILE
  //   |   IF
  //   |   ELSE
  //   |   IN
  //   |   OUT
  //   |   INOUT
  //   |   TRUE
  //   |   FALSE
  //   |   PRECISION
  //   |   RETURN
  //   |   STRUCT
  //   |   type_specifier_no_prec
  //   |   type_qualifier
  //   |   precision_qualifier
  static boolean keyword(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "keyword")) return false;
    boolean r;
    r = consumeToken(b, BREAK);
    if (!r) r = consumeToken(b, CONTINUE);
    if (!r) r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, FOR);
    if (!r) r = consumeToken(b, WHILE);
    if (!r) r = consumeToken(b, IF);
    if (!r) r = consumeToken(b, ELSE);
    if (!r) r = consumeToken(b, IN);
    if (!r) r = consumeToken(b, OUT);
    if (!r) r = consumeToken(b, INOUT);
    if (!r) r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    if (!r) r = consumeToken(b, PRECISION);
    if (!r) r = consumeToken(b, RETURN);
    if (!r) r = consumeToken(b, STRUCT);
    if (!r) r = type_specifier_no_prec(b, l + 1);
    if (!r) r = type_qualifier(b, l + 1);
    if (!r) r = precision_qualifier(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // LEFT_OP
  //   |   RIGHT_OP
  //   |   INC_OP
  //   |   DEC_OP
  //   |   LE_OP
  //   |   GE_OP
  //   |   EQ_OP
  //   |   NE_OP
  //   |   AND_OP
  //   |   OR_OP
  //   |   XOR_OP
  //   |   MUL_ASSIGN
  //   |   DIV_ASSIGN
  //   |   ADD_ASSIGN
  //   |   MOD_ASSIGN
  //   |   LEFT_ASSIGN
  //   |   RIGHT_ASSIGN
  //   |   AND_ASSIGN
  //   |   XOR_ASSIGN
  //   |   OR_ASSIGN
  //   |   SUB_ASSIGN
  static boolean operator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operator")) return false;
    boolean r;
    r = consumeToken(b, LEFT_OP);
    if (!r) r = consumeToken(b, RIGHT_OP);
    if (!r) r = consumeToken(b, INC_OP);
    if (!r) r = consumeToken(b, DEC_OP);
    if (!r) r = consumeToken(b, LE_OP);
    if (!r) r = consumeToken(b, GE_OP);
    if (!r) r = consumeToken(b, EQ_OP);
    if (!r) r = consumeToken(b, NE_OP);
    if (!r) r = consumeToken(b, AND_OP);
    if (!r) r = consumeToken(b, OR_OP);
    if (!r) r = consumeToken(b, XOR_OP);
    if (!r) r = consumeToken(b, MUL_ASSIGN);
    if (!r) r = consumeToken(b, DIV_ASSIGN);
    if (!r) r = consumeToken(b, ADD_ASSIGN);
    if (!r) r = consumeToken(b, MOD_ASSIGN);
    if (!r) r = consumeToken(b, LEFT_ASSIGN);
    if (!r) r = consumeToken(b, RIGHT_ASSIGN);
    if (!r) r = consumeToken(b, AND_ASSIGN);
    if (!r) r = consumeToken(b, XOR_ASSIGN);
    if (!r) r = consumeToken(b, OR_ASSIGN);
    if (!r) r = consumeToken(b, SUB_ASSIGN);
    return r;
  }

  /* ********************************************************** */
  // LEFT_PAREN
  //   |   RIGHT_PAREN
  //   |   LEFT_BRACKET
  //   |   RIGHT_BRACKET
  //   |   LEFT_BRACE
  //   |   RIGHT_BRACE
  //   |   DOT
  //   |   COMMA
  //   |   COLON
  //   |   EQUAL
  //   |   SEMICOLON
  //   |   BANG
  //   |   DASH
  //   |   TILDE
  //   |   PLUS
  //   |   STAR
  //   |   SLASH
  //   |   PERCENT
  //   |   LEFT_ANGLE
  //   |   RIGHT_ANGLE
  //   |   VERTICAL_BAR
  //   |   CARET
  //   |   AMPERSAND
  //   |   QUESTION
  static boolean other_symbol(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "other_symbol")) return false;
    boolean r;
    r = consumeToken(b, LEFT_PAREN);
    if (!r) r = consumeToken(b, RIGHT_PAREN);
    if (!r) r = consumeToken(b, LEFT_BRACKET);
    if (!r) r = consumeToken(b, RIGHT_BRACKET);
    if (!r) r = consumeToken(b, LEFT_BRACE);
    if (!r) r = consumeToken(b, RIGHT_BRACE);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, COMMA);
    if (!r) r = consumeToken(b, COLON);
    if (!r) r = consumeToken(b, EQUAL);
    if (!r) r = consumeToken(b, SEMICOLON);
    if (!r) r = consumeToken(b, BANG);
    if (!r) r = consumeToken(b, DASH);
    if (!r) r = consumeToken(b, TILDE);
    if (!r) r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, STAR);
    if (!r) r = consumeToken(b, SLASH);
    if (!r) r = consumeToken(b, PERCENT);
    if (!r) r = consumeToken(b, LEFT_ANGLE);
    if (!r) r = consumeToken(b, RIGHT_ANGLE);
    if (!r) r = consumeToken(b, VERTICAL_BAR);
    if (!r) r = consumeToken(b, CARET);
    if (!r) r = consumeToken(b, AMPERSAND);
    if (!r) r = consumeToken(b, QUESTION);
    return r;
  }

  /* ********************************************************** */
  // HIGH_PRECISION | MEDIUM_PRECISION | LOW_PRECISION
  static boolean precision_qualifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "precision_qualifier")) return false;
    boolean r;
    r = consumeToken(b, HIGH_PRECISION);
    if (!r) r = consumeToken(b, MEDIUM_PRECISION);
    if (!r) r = consumeToken(b, LOW_PRECISION);
    return r;
  }

  /* ********************************************************** */
  // ASM
  //   |   CLASS
  //   |   UNION
  //   |   ENUM
  //   |   TYPEDEF
  //   |   TEMPLATE
  //   |   THIS
  //   |   PACKED
  //   |   GOTO
  //   |   SWITCH
  //   |   DEFAULT
  //   |   INLINE
  //   |   NOINLINE
  //   |   VOLATILE
  //   |   PUBLIC
  //   |   STATIC
  //   |   EXTERN
  //   |   EXTERNAL
  //   |   INTERFACE
  //   |   FLAT
  //   |   LONG
  //   |   SHORT
  //   |   DOUBLE
  //   |   FIXED
  //   |   UNSIGNED
  //   |   SUPERP
  //   |   INPUT
  //   |   OUTPUT
  //   |   HVEC2
  //   |   HVEC3
  //   |   HVEC4
  //   |   DVEC2
  //   |   DVEC3
  //   |   DVEC4
  //   |   FVEC2
  //   |   FVEC3
  //   |   FVEC4
  //   |   SAMPLER1D
  //   |   SAMPLER3D
  //   |   SAMPLER1DSHADOW
  //   |   SAMPLER2DSHADOW
  //   |   SAMPLER2DRECT
  //   |   SAMPLER3DRECT
  //   |   SAMPLER2DRECTSHADOW
  //   |   SIZEOF
  //   |   CAST
  //   |   NAMESPACE
  //   |   USING
  public static boolean reserved_keyword(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reserved_keyword")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RESERVED_KEYWORD, "<reserved keyword>");
    r = consumeToken(b, ASM);
    if (!r) r = consumeToken(b, CLASS);
    if (!r) r = consumeToken(b, UNION);
    if (!r) r = consumeToken(b, ENUM);
    if (!r) r = consumeToken(b, TYPEDEF);
    if (!r) r = consumeToken(b, TEMPLATE);
    if (!r) r = consumeToken(b, THIS);
    if (!r) r = consumeToken(b, PACKED);
    if (!r) r = consumeToken(b, GOTO);
    if (!r) r = consumeToken(b, SWITCH);
    if (!r) r = consumeToken(b, DEFAULT);
    if (!r) r = consumeToken(b, INLINE);
    if (!r) r = consumeToken(b, NOINLINE);
    if (!r) r = consumeToken(b, VOLATILE);
    if (!r) r = consumeToken(b, PUBLIC);
    if (!r) r = consumeToken(b, STATIC);
    if (!r) r = consumeToken(b, EXTERN);
    if (!r) r = consumeToken(b, EXTERNAL);
    if (!r) r = consumeToken(b, INTERFACE);
    if (!r) r = consumeToken(b, FLAT);
    if (!r) r = consumeToken(b, LONG);
    if (!r) r = consumeToken(b, SHORT);
    if (!r) r = consumeToken(b, DOUBLE);
    if (!r) r = consumeToken(b, FIXED);
    if (!r) r = consumeToken(b, UNSIGNED);
    if (!r) r = consumeToken(b, SUPERP);
    if (!r) r = consumeToken(b, INPUT);
    if (!r) r = consumeToken(b, OUTPUT);
    if (!r) r = consumeToken(b, HVEC2);
    if (!r) r = consumeToken(b, HVEC3);
    if (!r) r = consumeToken(b, HVEC4);
    if (!r) r = consumeToken(b, DVEC2);
    if (!r) r = consumeToken(b, DVEC3);
    if (!r) r = consumeToken(b, DVEC4);
    if (!r) r = consumeToken(b, FVEC2);
    if (!r) r = consumeToken(b, FVEC3);
    if (!r) r = consumeToken(b, FVEC4);
    if (!r) r = consumeToken(b, SAMPLER1D);
    if (!r) r = consumeToken(b, SAMPLER3D);
    if (!r) r = consumeToken(b, SAMPLER1DSHADOW);
    if (!r) r = consumeToken(b, SAMPLER2DSHADOW);
    if (!r) r = consumeToken(b, SAMPLER2DRECT);
    if (!r) r = consumeToken(b, SAMPLER3DRECT);
    if (!r) r = consumeToken(b, SAMPLER2DRECTSHADOW);
    if (!r) r = consumeToken(b, SIZEOF);
    if (!r) r = consumeToken(b, CAST);
    if (!r) r = consumeToken(b, NAMESPACE);
    if (!r) r = consumeToken(b, USING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // keyword
  //   |   operator
  //   |   other_symbol
  //   |   INTCONSTANT
  //   |   FLOATCONSTANT
  //   |   IDENTIFIER
  //   |   reserved_keyword
  //   |   unsupported_keyword
  //   |   glsl_identifier
  public static boolean token(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "token")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TOKEN, "<token>");
    r = keyword(b, l + 1);
    if (!r) r = operator(b, l + 1);
    if (!r) r = other_symbol(b, l + 1);
    if (!r) r = consumeToken(b, INTCONSTANT);
    if (!r) r = consumeToken(b, FLOATCONSTANT);
    if (!r) r = consumeToken(b, IDENTIFIER);
    if (!r) r = reserved_keyword(b, l + 1);
    if (!r) r = unsupported_keyword(b, l + 1);
    if (!r) r = glsl_identifier(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // CONST
  //   |   UNIFORM
  static boolean type_qualifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_qualifier")) return false;
    if (!nextTokenIs(b, "", CONST, UNIFORM)) return false;
    boolean r;
    r = consumeToken(b, CONST);
    if (!r) r = consumeToken(b, UNIFORM);
    return r;
  }

  /* ********************************************************** */
  // VOID
  //   |   FLOAT
  //   |   INT
  //   |   BOOL
  //   |   VEC2
  //   |   VEC3
  //   |   VEC4
  //   |   BVEC2
  //   |   BVEC3
  //   |   BVEC4
  //   |   IVEC2
  //   |   IVEC3
  //   |   IVEC4
  //   |   MAT2
  //   |   MAT3
  //   |   MAT4
  //   // AGSL additional type keywords
  //   |   HALF
  //   |   HALF2
  //   |   HALF3
  //   |   HALF4
  //   |   FLOAT2
  //   |   FLOAT3
  //   |   FLOAT4
  //   |   BOOL2
  //   |   BOOL3
  //   |   BOOL4
  //   |   INT2
  //   |   INT3
  //   |   INT4
  //   |   FLOAT2X2
  //   |   FLOAT3X3
  //   |   FLOAT4X4
  //   |   HALF2X2
  //   |   HALF3X3
  //   |   HALF4X4
  //   |   SHADER
  //   |   COLORFILTER
  //   |   BLENDER
  static boolean type_specifier_no_prec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_specifier_no_prec")) return false;
    boolean r;
    r = consumeToken(b, VOID);
    if (!r) r = consumeToken(b, FLOAT);
    if (!r) r = consumeToken(b, INT);
    if (!r) r = consumeToken(b, BOOL);
    if (!r) r = consumeToken(b, VEC2);
    if (!r) r = consumeToken(b, VEC3);
    if (!r) r = consumeToken(b, VEC4);
    if (!r) r = consumeToken(b, BVEC2);
    if (!r) r = consumeToken(b, BVEC3);
    if (!r) r = consumeToken(b, BVEC4);
    if (!r) r = consumeToken(b, IVEC2);
    if (!r) r = consumeToken(b, IVEC3);
    if (!r) r = consumeToken(b, IVEC4);
    if (!r) r = consumeToken(b, MAT2);
    if (!r) r = consumeToken(b, MAT3);
    if (!r) r = consumeToken(b, MAT4);
    if (!r) r = consumeToken(b, HALF);
    if (!r) r = consumeToken(b, HALF2);
    if (!r) r = consumeToken(b, HALF3);
    if (!r) r = consumeToken(b, HALF4);
    if (!r) r = consumeToken(b, FLOAT2);
    if (!r) r = consumeToken(b, FLOAT3);
    if (!r) r = consumeToken(b, FLOAT4);
    if (!r) r = consumeToken(b, BOOL2);
    if (!r) r = consumeToken(b, BOOL3);
    if (!r) r = consumeToken(b, BOOL4);
    if (!r) r = consumeToken(b, INT2);
    if (!r) r = consumeToken(b, INT3);
    if (!r) r = consumeToken(b, INT4);
    if (!r) r = consumeToken(b, FLOAT2X2);
    if (!r) r = consumeToken(b, FLOAT3X3);
    if (!r) r = consumeToken(b, FLOAT4X4);
    if (!r) r = consumeToken(b, HALF2X2);
    if (!r) r = consumeToken(b, HALF3X3);
    if (!r) r = consumeToken(b, HALF4X4);
    if (!r) r = consumeToken(b, SHADER);
    if (!r) r = consumeToken(b, COLORFILTER);
    if (!r) r = consumeToken(b, BLENDER);
    return r;
  }

  /* ********************************************************** */
  // DISCARD
  //   |   SAMPLER2D
  //   |   SAMPLERCUBE
  //   |   ATTRIBUTE
  //   |   VARYING
  //   |   INVARIANT
  public static boolean unsupported_keyword(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unsupported_keyword")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNSUPPORTED_KEYWORD, "<unsupported keyword>");
    r = consumeToken(b, DISCARD);
    if (!r) r = consumeToken(b, SAMPLER2D);
    if (!r) r = consumeToken(b, SAMPLERCUBE);
    if (!r) r = consumeToken(b, ATTRIBUTE);
    if (!r) r = consumeToken(b, VARYING);
    if (!r) r = consumeToken(b, INVARIANT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

}
