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
package com.android.tools.idea.lang.agsl;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;

public interface AgslTokenTypes {


  IElementType ADD_ASSIGN = new AgslTokenType("+=");
  IElementType AMPERSAND = new AgslTokenType("&");
  IElementType AND_ASSIGN = new AgslTokenType("&=");
  IElementType AND_OP = new AgslTokenType("&&");
  IElementType ASM = new AgslTokenType("asm");
  IElementType ATTRIBUTE = new AgslTokenType("attribute");
  IElementType BANG = new AgslTokenType("!");
  IElementType BLENDER = new AgslTokenType("blender");
  IElementType BLOCK_COMMENT = new AgslTokenType("BLOCK_COMMENT");
  IElementType BOOL = new AgslTokenType("bool");
  IElementType BOOL2 = new AgslTokenType("bool2");
  IElementType BOOL3 = new AgslTokenType("bool3");
  IElementType BOOL4 = new AgslTokenType("bool4");
  IElementType BREAK = new AgslTokenType("break");
  IElementType BVEC2 = new AgslTokenType("bvec2");
  IElementType BVEC3 = new AgslTokenType("bvec3");
  IElementType BVEC4 = new AgslTokenType("bvec4");
  IElementType CARET = new AgslTokenType("^");
  IElementType CAST = new AgslTokenType("cast");
  IElementType CLASS = new AgslTokenType("class");
  IElementType COLON = new AgslTokenType(":");
  IElementType COLORFILTER = new AgslTokenType("colorFilter");
  IElementType COMMA = new AgslTokenType(",");
  IElementType COMMENT = new AgslTokenType("COMMENT");
  IElementType CONST = new AgslTokenType("const");
  IElementType CONTINUE = new AgslTokenType("continue");
  IElementType DASH = new AgslTokenType("-");
  IElementType DEC_OP = new AgslTokenType("--");
  IElementType DEFAULT = new AgslTokenType("default");
  IElementType DISCARD = new AgslTokenType("discard");
  IElementType DIV_ASSIGN = new AgslTokenType("/=");
  IElementType DO = new AgslTokenType("do");
  IElementType DOT = new AgslTokenType(".");
  IElementType DOUBLE = new AgslTokenType("double");
  IElementType DVEC2 = new AgslTokenType("dvec2");
  IElementType DVEC3 = new AgslTokenType("dvec3");
  IElementType DVEC4 = new AgslTokenType("dvec4");
  IElementType ELSE = new AgslTokenType("else");
  IElementType ENUM = new AgslTokenType("enum");
  IElementType EQUAL = new AgslTokenType("=");
  IElementType EQ_OP = new AgslTokenType("==");
  IElementType EXTERN = new AgslTokenType("extern");
  IElementType EXTERNAL = new AgslTokenType("external");
  IElementType FALSE = new AgslTokenType("false");
  IElementType FIXED = new AgslTokenType("fixed");
  IElementType FLAT = new AgslTokenType("flat");
  IElementType FLOAT = new AgslTokenType("float");
  IElementType FLOAT2 = new AgslTokenType("float2");
  IElementType FLOAT2X2 = new AgslTokenType("float2x2");
  IElementType FLOAT3 = new AgslTokenType("float3");
  IElementType FLOAT3X3 = new AgslTokenType("float3x3");
  IElementType FLOAT4 = new AgslTokenType("float4");
  IElementType FLOAT4X4 = new AgslTokenType("float4x4");
  IElementType FLOATCONSTANT = new AgslTokenType("FLOATCONSTANT");
  IElementType FOR = new AgslTokenType("for");
  IElementType FVEC2 = new AgslTokenType("fvec2");
  IElementType FVEC3 = new AgslTokenType("fvec3");
  IElementType FVEC4 = new AgslTokenType("fvec4");
  IElementType GE_OP = new AgslTokenType(">=");
  IElementType GOTO = new AgslTokenType("goto");
  IElementType HALF = new AgslTokenType("half");
  IElementType HALF2 = new AgslTokenType("half2");
  IElementType HALF2X2 = new AgslTokenType("half2x2");
  IElementType HALF3 = new AgslTokenType("half3");
  IElementType HALF3X3 = new AgslTokenType("half3x3");
  IElementType HALF4 = new AgslTokenType("half4");
  IElementType HALF4X4 = new AgslTokenType("half4x4");
  IElementType HIGH_PRECISION = new AgslTokenType("highp");
  IElementType HVEC2 = new AgslTokenType("hvec2");
  IElementType HVEC3 = new AgslTokenType("hvec3");
  IElementType HVEC4 = new AgslTokenType("hvec4");
  IElementType IDENTIFIER = new AgslTokenType("IDENTIFIER");
  IElementType IDENTIFIER_GL_PREFIX = new AgslTokenType("IDENTIFIER_GL_PREFIX");
  IElementType IF = new AgslTokenType("if");
  IElementType IN = new AgslTokenType("in");
  IElementType INC_OP = new AgslTokenType("++");
  IElementType INLINE = new AgslTokenType("inline");
  IElementType INOUT = new AgslTokenType("inout");
  IElementType INPUT = new AgslTokenType("input");
  IElementType INT = new AgslTokenType("int");
  IElementType INT2 = new AgslTokenType("int2");
  IElementType INT3 = new AgslTokenType("int3");
  IElementType INT4 = new AgslTokenType("int4");
  IElementType INTCONSTANT = new AgslTokenType("INTCONSTANT");
  IElementType INTERFACE = new AgslTokenType("interface");
  IElementType INVARIANT = new AgslTokenType("invariant");
  IElementType IVEC2 = new AgslTokenType("ivec2");
  IElementType IVEC3 = new AgslTokenType("ivec3");
  IElementType IVEC4 = new AgslTokenType("ivec4");
  IElementType LEFT_ANGLE = new AgslTokenType("<");
  IElementType LEFT_ASSIGN = new AgslTokenType("<<=");
  IElementType LEFT_BRACE = new AgslTokenType("{");
  IElementType LEFT_BRACKET = new AgslTokenType("[");
  IElementType LEFT_OP = new AgslTokenType("<<");
  IElementType LEFT_PAREN = new AgslTokenType("(");
  IElementType LE_OP = new AgslTokenType("<=");
  IElementType LONG = new AgslTokenType("long");
  IElementType LOW_PRECISION = new AgslTokenType("lowp");
  IElementType MAT2 = new AgslTokenType("mat2");
  IElementType MAT3 = new AgslTokenType("mat3");
  IElementType MAT4 = new AgslTokenType("mat4");
  IElementType MEDIUM_PRECISION = new AgslTokenType("mediump");
  IElementType MOD_ASSIGN = new AgslTokenType("%=");
  IElementType MUL_ASSIGN = new AgslTokenType("*=");
  IElementType NAMESPACE = new AgslTokenType("namespace");
  IElementType NE_OP = new AgslTokenType("!=");
  IElementType NOINLINE = new AgslTokenType("noinline");
  IElementType OR_ASSIGN = new AgslTokenType("|=");
  IElementType OR_OP = new AgslTokenType("||");
  IElementType OUT = new AgslTokenType("out");
  IElementType OUTPUT = new AgslTokenType("output");
  IElementType PACKED = new AgslTokenType("packed");
  IElementType PERCENT = new AgslTokenType("%");
  IElementType PLUS = new AgslTokenType("+");
  IElementType PRECISION = new AgslTokenType("precision");
  IElementType PUBLIC = new AgslTokenType("public");
  IElementType QUESTION = new AgslTokenType("?");
  IElementType RETURN = new AgslTokenType("return");
  IElementType RIGHT_ANGLE = new AgslTokenType(">");
  IElementType RIGHT_ASSIGN = new AgslTokenType(">>=");
  IElementType RIGHT_BRACE = new AgslTokenType("}");
  IElementType RIGHT_BRACKET = new AgslTokenType("]");
  IElementType RIGHT_OP = new AgslTokenType(">>");
  IElementType RIGHT_PAREN = new AgslTokenType(")");
  IElementType SAMPLER1D = new AgslTokenType("sampler1D");
  IElementType SAMPLER1DSHADOW = new AgslTokenType("sampler1DShadow");
  IElementType SAMPLER2D = new AgslTokenType("sampler2d");
  IElementType SAMPLER2DRECT = new AgslTokenType("sampler2DRect");
  IElementType SAMPLER2DRECTSHADOW = new AgslTokenType("sampler2DRectShadow");
  IElementType SAMPLER2DSHADOW = new AgslTokenType("sampler2DShadow");
  IElementType SAMPLER3D = new AgslTokenType("sampler3D");
  IElementType SAMPLER3DRECT = new AgslTokenType("sampler3DRect");
  IElementType SAMPLERCUBE = new AgslTokenType("samplerCube");
  IElementType SEMICOLON = new AgslTokenType(";");
  IElementType SHADER = new AgslTokenType("shader");
  IElementType SHORT = new AgslTokenType("short");
  IElementType SIZEOF = new AgslTokenType("sizeof");
  IElementType SLASH = new AgslTokenType("/");
  IElementType STAR = new AgslTokenType("*");
  IElementType STATIC = new AgslTokenType("static");
  IElementType STRUCT = new AgslTokenType("struct");
  IElementType SUB_ASSIGN = new AgslTokenType("-=");
  IElementType SUPERP = new AgslTokenType("superp");
  IElementType SWITCH = new AgslTokenType("switch");
  IElementType TEMPLATE = new AgslTokenType("template");
  IElementType THIS = new AgslTokenType("this");
  IElementType TILDE = new AgslTokenType("~");
  IElementType TRUE = new AgslTokenType("true");
  IElementType TYPEDEF = new AgslTokenType("typedef");
  IElementType UNIFORM = new AgslTokenType("uniform");
  IElementType UNION = new AgslTokenType("union");
  IElementType UNSIGNED = new AgslTokenType("unsigned");
  IElementType USING = new AgslTokenType("using");
  IElementType VARYING = new AgslTokenType("varying");
  IElementType VEC2 = new AgslTokenType("vec2");
  IElementType VEC3 = new AgslTokenType("vec3");
  IElementType VEC4 = new AgslTokenType("vec4");
  IElementType VERTICAL_BAR = new AgslTokenType("|");
  IElementType VOID = new AgslTokenType("void");
  IElementType VOLATILE = new AgslTokenType("volatile");
  IElementType WHILE = new AgslTokenType("while");
  IElementType XOR_ASSIGN = new AgslTokenType("^=");
  IElementType XOR_OP = new AgslTokenType("^^");

  class Factory {
  }
}
