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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.lexer.Lexer
import com.intellij.testFramework.LexerTestCase

class AgslLexerTest : LexerTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(true)
  }

  override fun tearDown() {
    try {
      StudioFlags.AGSL_LANGUAGE_SUPPORT.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  fun test_NOT_lexingWithAgslOff() {
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(false)
    doTest(
      "struct 123 1.0 abc",
      "empty token ('struct 123 1.0 abc')"
    )
  }

  fun testLiterals() {
    doTest(
      "a _ _ab AB_C D123 A1B",
      "AgslTokenType.IDENTIFIER ('a')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('_')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('_ab')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('AB_C')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('D123')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('A1B')"
    )
  }

  fun testNumbers() {
    // Integers
    doTest(
      "0 1 2 23 2345",
      "AgslTokenType.INTCONSTANT ('0')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.INTCONSTANT ('1')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.INTCONSTANT ('2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.INTCONSTANT ('23')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.INTCONSTANT ('2345')"
    )
    // Hex
    doTest(
      "0x0 0XAF",
      "AgslTokenType.INTCONSTANT ('0x0')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.INTCONSTANT ('0XAF')"
    )
    // Octal
    doTest(
      "07",
      "AgslTokenType.INTCONSTANT ('07')"
    )
    // Floating point
    doTest(
      "1. .1 1.0 1.0e+5 1e+5 1E5 .",
      "AgslTokenType.FLOATCONSTANT ('1.')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.FLOATCONSTANT ('.1')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.FLOATCONSTANT ('1.0')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.FLOATCONSTANT ('1.0e+5')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.FLOATCONSTANT ('1e+5')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.FLOATCONSTANT ('1E5')\n" +
      "WHITE_SPACE (' ')\n" +
      // ".1" and "1." are both floating numbers, but
      // a dot by itself is not a floating number
      "AgslTokenType.. ('.')"
    )
  }

  fun testKeywords() {
    // From EGSL 1.0 spec, section 3.7:
    doTest(
      "attribute const uniform varying " +
      "break continue do for while " +
      "if else " +
      "in out inout" +
      "float int void bool true false " +
      "lowp mediump highp precision invariant" +
      // Note that discard is disallowed in AGSL but we
      // recognize it as a keywords and produce a parser
      // wawrning instead (as well as for a few others)
      "discard " +
      "return " +
      "mat2 mat3 mat4 " +
      "vec2 vec3 vec4 ivec2 ivec3 ivec4 bvec2 bvec3 bvec4 " +
      "sampler2D samplerCube " +
      "struct " +
      // Additional keywords/aliases added in AGSL
      "half half2, half3, half4 " +
      "float2 float3 float4 " +
      "bool2 bool3 bool4 " +
      "int2 int3 int4 " +
      "float2x2 float3x3 float4x4 " +
      "half2x2 half3x3 half4x4 " +
      "shader colorFilter blender" +
      "not_a_keyword",
      "AgslTokenType.attribute ('attribute')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.const ('const')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.uniform ('uniform')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.varying ('varying')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.break ('break')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.continue ('continue')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.do ('do')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.for ('for')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.while ('while')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.if ('if')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.else ('else')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.in ('in')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.out ('out')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('inoutfloat')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.int ('int')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.void ('void')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bool ('bool')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.true ('true')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.false ('false')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.lowp ('lowp')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.mediump ('mediump')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.highp ('highp')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.precision ('precision')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('invariantdiscard')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.return ('return')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.mat2 ('mat2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.mat3 ('mat3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.mat4 ('mat4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.vec2 ('vec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.vec3 ('vec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.vec4 ('vec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.ivec2 ('ivec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.ivec3 ('ivec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.ivec4 ('ivec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bvec2 ('bvec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bvec3 ('bvec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bvec4 ('bvec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('sampler2D')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.samplerCube ('samplerCube')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.struct ('struct')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half ('half')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half2 ('half2')\n" +
      "AgslTokenType., (',')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half3 ('half3')\n" +
      "AgslTokenType., (',')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half4 ('half4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float2 ('float2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float3 ('float3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float4 ('float4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bool2 ('bool2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bool3 ('bool3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.bool4 ('bool4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.int2 ('int2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.int3 ('int3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.int4 ('int4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float2x2 ('float2x2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float3x3 ('float3x3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.float4x4 ('float4x4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half2x2 ('half2x2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half3x3 ('half3x3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half4x4 ('half4x4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.shader ('shader')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.colorFilter ('colorFilter')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.IDENTIFIER ('blendernot_a_keyword')"
    )

    // Reserved keywords
    doTest(
      "asm" +
      " class union enum typedef template this packed" +
      " goto switch default" +
      " inline noinline volatile public static extern external interface flat" +
      " long short double half fixed unsigned superp" +
      " input output" +
      " hvec2 hvec3 hvec4 dvec2 dvec3 dvec4 fvec2 fvec3 fvec4" +
      " sampler1D sampler3D" +
      " sampler1DShadow sampler2DShadow" +
      " sampler2DRect sampler3DRect sampler2DRectShadow" +
      " sizeof cast" +
      " namespace using",
      "AgslTokenType.asm ('asm')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.class ('class')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.union ('union')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.enum ('enum')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.typedef ('typedef')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.template ('template')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.this ('this')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.packed ('packed')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.goto ('goto')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.switch ('switch')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.default ('default')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.inline ('inline')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.noinline ('noinline')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.volatile ('volatile')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.public ('public')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.static ('static')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.extern ('extern')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.external ('external')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.interface ('interface')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.flat ('flat')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.long ('long')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.short ('short')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.double ('double')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.half ('half')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.fixed ('fixed')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.unsigned ('unsigned')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.superp ('superp')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.input ('input')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.output ('output')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.hvec2 ('hvec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.hvec3 ('hvec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.hvec4 ('hvec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.dvec2 ('dvec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.dvec3 ('dvec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.dvec4 ('dvec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.fvec2 ('fvec2')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.fvec3 ('fvec3')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.fvec4 ('fvec4')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler1D ('sampler1D')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler3D ('sampler3D')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler1DShadow ('sampler1DShadow')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler2DShadow ('sampler2DShadow')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler2DRect ('sampler2DRect')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler3DRect ('sampler3DRect')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sampler2DRectShadow ('sampler2DRectShadow')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.sizeof ('sizeof')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.cast ('cast')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.namespace ('namespace')\n" +
      "WHITE_SPACE (' ')\n" +
      "AgslTokenType.using ('using')"
    )
  }

  fun testComments() {
    doTest(
      "// line comment 1\n" +
      "   // line comment 2\n" +
      "/* block\n" +
      "   // just part of the block comment\n" +
      "   * /\n" +
      "   */" +
      "identifier",
      "AgslTokenType.COMMENT ('// line comment 1')\n" +
      "WHITE_SPACE ('\\n   ')\n" +
      "AgslTokenType.COMMENT ('// line comment 2')\n" +
      "WHITE_SPACE ('\\n')\n" +
      "AgslTokenType.BLOCK_COMMENT ('/* block\\n   // just part of the block comment\\n   * /\\n   */')\n" +
      "AgslTokenType.IDENTIFIER ('identifier')"
    )
  }

  override fun createLexer(): Lexer {
    return AgslParserDefinition().createLexer()
  }

  override fun getDirPath(): String {
    return ""
  }
}