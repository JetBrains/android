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
package com.android.tools.idea.lang.agsl

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

interface AgslTokenTypeSets {
  companion object {
    val WHITESPACES = TokenSet.create(TokenType.WHITE_SPACE)
    val COMMENTS = TokenSet.create(AgslTokenTypes.COMMENT, AgslTokenTypes.BLOCK_COMMENT)
    val BAD_TOKENS = TokenSet.create(TokenType.BAD_CHARACTER, AgslTokenTypes.GLSL_IDENTIFIER)
    val IDENTIFIERS = TokenSet.create(AgslTokenTypes.IDENTIFIER)
    val NUMBERS = TokenSet.create(AgslTokenTypes.INTCONSTANT, AgslTokenTypes.FLOATCONSTANT)
    val KEYWORDS = TokenSet.create(
      AgslTokenTypes.CONST,
      AgslTokenTypes.UNIFORM,
      AgslTokenTypes.BREAK,
      AgslTokenTypes.CONTINUE,
      AgslTokenTypes.DO,
      AgslTokenTypes.FOR,
      AgslTokenTypes.WHILE,
      AgslTokenTypes.IF,
      AgslTokenTypes.ELSE,
      AgslTokenTypes.IN,
      AgslTokenTypes.OUT,
      AgslTokenTypes.INOUT,
      AgslTokenTypes.FLOAT,
      AgslTokenTypes.INT,
      AgslTokenTypes.VOID,
      AgslTokenTypes.BOOL,
      AgslTokenTypes.TRUE,
      AgslTokenTypes.FALSE,
      AgslTokenTypes.LOW_PRECISION,
      AgslTokenTypes.MEDIUM_PRECISION,
      AgslTokenTypes.HIGH_PRECISION,
      AgslTokenTypes.PRECISION,
      AgslTokenTypes.RETURN,
      AgslTokenTypes.MAT2,
      AgslTokenTypes.MAT3,
      AgslTokenTypes.MAT4,
      AgslTokenTypes.VEC2,
      AgslTokenTypes.VEC3,
      AgslTokenTypes.VEC4,
      AgslTokenTypes.IVEC2,
      AgslTokenTypes.IVEC3,
      AgslTokenTypes.IVEC4,
      AgslTokenTypes.BVEC2,
      AgslTokenTypes.BVEC3,
      AgslTokenTypes.BVEC4,
      AgslTokenTypes.STRUCT,

      // UNSUPPORTED IN AGSL
      AgslTokenTypes.DISCARD,
      AgslTokenTypes.SAMPLER2D,
      AgslTokenTypes.SAMPLERCUBE,
      AgslTokenTypes.ATTRIBUTE,
      AgslTokenTypes.VARYING,
      AgslTokenTypes.INVARIANT,

      // ADDITIONAL KEYWORDS ADDED BY AGSL
      AgslTokenTypes.HALF,
      AgslTokenTypes.HALF2,
      AgslTokenTypes.HALF3,
      AgslTokenTypes.HALF4,
      AgslTokenTypes.FLOAT2,
      AgslTokenTypes.FLOAT3,
      AgslTokenTypes.FLOAT4,
      AgslTokenTypes.BOOL2,
      AgslTokenTypes.BOOL3,
      AgslTokenTypes.BOOL4,
      AgslTokenTypes.INT2,
      AgslTokenTypes.INT3,
      AgslTokenTypes.INT4,
      AgslTokenTypes.FLOAT2X2,
      AgslTokenTypes.FLOAT3X3,
      AgslTokenTypes.FLOAT4X4,
      AgslTokenTypes.HALF2X2,
      AgslTokenTypes.HALF3X3,
      AgslTokenTypes.HALF4X4,
      AgslTokenTypes.SHADER,
      AgslTokenTypes.COLORFILTER,
      AgslTokenTypes.BLENDER,

      // RESERVED KEYWORDS FOR THE FUTURE
      AgslTokenTypes.ASM,
      AgslTokenTypes.CLASS,
      AgslTokenTypes.UNION,
      AgslTokenTypes.ENUM,
      AgslTokenTypes.TYPEDEF,
      AgslTokenTypes.TEMPLATE,
      AgslTokenTypes.THIS,
      AgslTokenTypes.PACKED,
      AgslTokenTypes.GOTO,
      AgslTokenTypes.SWITCH,
      AgslTokenTypes.DEFAULT,
      AgslTokenTypes.INLINE,
      AgslTokenTypes.NOINLINE,
      AgslTokenTypes.VOLATILE,
      AgslTokenTypes.PUBLIC,
      AgslTokenTypes.STATIC,
      AgslTokenTypes.EXTERN,
      AgslTokenTypes.EXTERNAL,
      AgslTokenTypes.INTERFACE,
      AgslTokenTypes.FLAT,
      AgslTokenTypes.LONG,
      AgslTokenTypes.SHORT,
      AgslTokenTypes.DOUBLE,
      AgslTokenTypes.FIXED,
      AgslTokenTypes.UNSIGNED,
      AgslTokenTypes.SUPERP,
      AgslTokenTypes.INPUT,
      AgslTokenTypes.OUTPUT,
      AgslTokenTypes.HVEC2,
      AgslTokenTypes.HVEC3,
      AgslTokenTypes.HVEC4,
      AgslTokenTypes.DVEC2,
      AgslTokenTypes.DVEC3,
      AgslTokenTypes.DVEC4,
      AgslTokenTypes.FVEC2,
      AgslTokenTypes.FVEC3,
      AgslTokenTypes.FVEC4,
      AgslTokenTypes.SAMPLER1D,
      AgslTokenTypes.SAMPLER3D,
      AgslTokenTypes.SAMPLER1DSHADOW,
      AgslTokenTypes.SAMPLER2DSHADOW,
      AgslTokenTypes.SAMPLER2DRECT,
      AgslTokenTypes.SAMPLER3DRECT,
      AgslTokenTypes.SAMPLER2DRECTSHADOW,
      AgslTokenTypes.SIZEOF,
      AgslTokenTypes.CAST,
      AgslTokenTypes.NAMESPACE,
      AgslTokenTypes.USING
    )
    val OPERATORS = TokenSet.create(
      AgslTokenTypes.LEFT_OP,
      AgslTokenTypes.RIGHT_OP,
      AgslTokenTypes.INC_OP,
      AgslTokenTypes.DEC_OP,
      AgslTokenTypes.LE_OP,
      AgslTokenTypes.GE_OP,
      AgslTokenTypes.EQ_OP,
      AgslTokenTypes.NE_OP,
      AgslTokenTypes.AND_OP,
      AgslTokenTypes.OR_OP,
      AgslTokenTypes.XOR_OP,
      AgslTokenTypes.MUL_ASSIGN,
      AgslTokenTypes.DIV_ASSIGN,
      AgslTokenTypes.ADD_ASSIGN,
      AgslTokenTypes.MOD_ASSIGN,
      AgslTokenTypes.LEFT_ASSIGN,
      AgslTokenTypes.RIGHT_ASSIGN,
      AgslTokenTypes.AND_ASSIGN,
      AgslTokenTypes.XOR_ASSIGN,
      AgslTokenTypes.OR_ASSIGN,
      AgslTokenTypes.SUB_ASSIGN,

      // other_symbols
      AgslTokenTypes.LEFT_PAREN,
      AgslTokenTypes.RIGHT_PAREN,
      AgslTokenTypes.LEFT_BRACKET,
      AgslTokenTypes.RIGHT_BRACKET,
      AgslTokenTypes.LEFT_BRACE,
      AgslTokenTypes.RIGHT_BRACE,
      AgslTokenTypes.DOT,
      AgslTokenTypes.COMMA,
      AgslTokenTypes.COLON,
      AgslTokenTypes.EQUAL,
      AgslTokenTypes.SEMICOLON,
      AgslTokenTypes.BANG,
      AgslTokenTypes.DASH,
      AgslTokenTypes.TILDE,
      AgslTokenTypes.PLUS,
      AgslTokenTypes.STAR,
      AgslTokenTypes.SLASH,
      AgslTokenTypes.PERCENT,
      AgslTokenTypes.LEFT_ANGLE,
      AgslTokenTypes.RIGHT_ANGLE,
      AgslTokenTypes.VERTICAL_BAR,
      AgslTokenTypes.CARET,
      AgslTokenTypes.AMPERSAND,
      AgslTokenTypes.QUESTION
    )
  }
}