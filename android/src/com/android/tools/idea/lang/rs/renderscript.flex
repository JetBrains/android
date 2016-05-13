/*
 * Copyright (C) 2013 The Android Open Source Project
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

package org.jetbrains.android.lang.rs;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/** A lexer for Renderscript files, generated from renderscript.flex using JFlex. */
%%

%class _RenderscriptLexer
%implements FlexLexer

%unicode 2.0

%function advance
%type IElementType

%{
  StringBuilder stringBuilder = new StringBuilder(30);
%}

/* main character classes */
LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]

WhiteSpace = {LineTerminator} | [ \t\f]

/* comments */
Comment = {TraditionalComment} | {EndOfLineComment}
TraditionalComment = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment = "//" {InputCharacter}* {LineTerminator}?

/* identifiers */
Identifier = [:jletter:][:jletterdigit:]*

/* integer literals */
DecIntegerLiteral = 0 | [1-9][0-9]*
DecLongLiteral    = {DecIntegerLiteral} [lL]

HexIntegerLiteral = 0 [xX] 0* {HexDigit} {1,8}
HexLongLiteral    = 0 [xX] 0* {HexDigit} {1,16} [lL]
HexDigit          = [0-9a-fA-F]

OctIntegerLiteral = 0+ [1-3]? {OctDigit} {1,15}
OctLongLiteral    = 0+ 1? {OctDigit} {1,21} [lL]
OctDigit          = [0-7]

/* floating point literals */
FloatLiteral  = ({FLit1}|{FLit2}|{FLit3}) {Exponent}? [fF]
DoubleLiteral = ({FLit1}|{FLit2}|{FLit3}) {Exponent}?

FLit1    = [0-9]+ \. [0-9]*
FLit2    = \. [0-9]+
FLit3    = [0-9]+
Exponent = [eE] [+-]? [0-9]+

CharacterLiteral="'"([^\\\'\r\n]|{EscapeSequence})*("'"|\\)?
StringLiteral=\"([^\\\"\r\n]|{EscapeSequence})*(\"|\\)?
EscapeSequence=\\[^\r\n]

VectorDigit = [2-4]
RSPrimeType = "char"|"double"|"float"|"half"|"int"|"long"|"short"|"uchar"|"ulong"|"uint"|"ushort"
RSVectorType = {RSPrimeType}{VectorDigit}
%%

<YYINITIAL> {
  "auto"       |
  "case"       |
  "char"       |
  "const"      |
  "break"      |
  "bool"       |
  "continue"   |
  "default"    |
  "do"         |
  "double"     |
  "else"       |
  "enum"       |
  "extern"     |
  "float"      |
  "for"        |
  "goto"       |
  "half"       |
  "if"         |
  "inline"     |
  "int"        |
  "int8_t"     |
  "int16_t"    |
  "int32_t"    |
  "int64_t"    |
  "long"       |
  "register"   |
  "restrict"   |
  "return"     |
  "short"      |
  "signed"     |
  "sizeof"     |
  "static"     |
  "struct"     |
  "switch"     |
  "typedef"    |
  "uchar"      |
  "ulong"      |
  "uint"       |
  "uint8_t"    |
  "uint16_t"   |
  "uint32_t"   |
  "uint64_t"   |
  "union"      |
  "unsigned"   |
  "ushort"     |
  "void"       |
  "volatile"   |
  "while"      |
  "#pragma"    |
  "_Bool"      |
  "_Complex"   |
  "_Imaginary"        { return RenderscriptTokenType.KEYWORD; }

  "TRUE"       |
  "FALSE"      |
  "null"              { return RenderscriptTokenType.KEYWORD; }

  {RSVectorType}              |
  "rs_matrix2x2"              |
  "rs_matrix3x3"              |
  "rs_matrix4x4"              |
  "rs_quaternion"             |
  "rs_allocation"             |
  "rs_allocation_cubemap_face"|
  "rs_allocation_usage_type"  |
  "rs_data_kind"              |
  "rs_data_type"              |
  "rs_element"                |
  "rs_sampler"                |
  "rs_sampler_value"          |
  "rs_script"                 |
  "rs_type"                   |
  "rs_time_t"                 |
  "rs_tm"                     |
  "rs_for_each_strategy_t"    |
  "rs_kernel_context"         |
  "rs_script_call_t"          |
  "RS_KERNEL"                 |
  "__attribute__"     { return RenderscriptTokenType.KEYWORD; }

  /* RenderScript defined APIs */
  "abs"                               |
  "acos"                              |
  "acosh"                             |
  "acospi"                            |
  "asin"                              |
  "asinh"                             |
  "asinpi"                            |
  "atan"                              |
  "atan2"                             |
  "atan2pi"                           |
  "atanh"                             |
  "atanpi"                            |
  "cbrt"                              |
  "ceil"                              |
  "clamp"                             |
  "clz"                               |
  "copysign"                          |
  "cos"                               |
  "cosh"                              |
  "cospi"                             |
  "degrees"                           |
  "erf"                               |
  "erfc"                              |
  "exp"                               |
  "exp10"                             |
  "exp2"                              |
  "expm1"                             |
  "fabs"                              |
  "fdim"                              |
  "floor"                             |
  "fma"                               |
  "fmax"                              |
  "fmin"                              |
  "fmod"                              |
  "fract"                             |
  "frexp"                             |
  "half_recip"                        |
  "half_rsqrt"                        |
  "half_sqrt"                         |
  "hypot"                             |
  "ilogb"                             |
  "ldexp"                             |
  "lgamma"                            |
  "log"                               |
  "log10"                             |
  "log1p"                             |
  "log2"                              |
  "logb"                              |
  "mad"                               |
  "max"                               |
  "min"                               |
  "mix"                               |
  "modf"                              |
  "nan"                               |
  "native_acos"                       |
  "native_acosh"                      |
  "native_acospi"                     |
  "native_asin"                       |
  "native_asinh"                      |
  "native_asinpi"                     |
  "native_atan"                       |
  "native_atan2"                      |
  "native_atan2pi"                    |
  "native_atanh"                      |
  "native_atanpi"                     |
  "native_cbrt"                       |
  "native_cos"                        |
  "native_cosh"                       |
  "native_cospi"                      |
  "native_divide"                     |
  "native_exp"                        |
  "native_exp10"                      |
  "native_exp2"                       |
  "native_expm1"                      |
  "native_hypot"                      |
  "native_log"                        |
  "native_log10"                      |
  "native_log1p"                      |
  "native_log2"                       |
  "native_powr"                       |
  "native_recip"                      |
  "native_rootn"                      |
  "native_rsqrt"                      |
  "native_sin"                        |
  "native_sincos"                     |
  "native_sinh"                       |
  "native_sinpi"                      |
  "native_sqrt"                       |
  "native_tan"                        |
  "native_tanh"                       |
  "native_tanpi"                      |
  "nextafter"                         |
  "pow"                               |
  "pown"                              |
  "powr"                              |
  "radians"                           |
  "remainder"                         |
  "remquo"                            |
  "rint"                              |
  "rootn"                             |
  "round"                             |
  "rsRand"                            |
  "rsqrt"                             |
  "sign"                              |
  "sin"                               |
  "sincos"                            |
  "sinh"                              |
  "sinpi"                             |
  "sqrt"                              |
  "step"                              |
  "tan"                               |
  "tanh"                              |
  "tanpi"                             |
  "tgamma"                            |
  "trunc"                             |
  "cross"                             |
  "distance"                          |
  "dot"                               |
  "fast_distance"                     |
  "fast_length"                       |
  "fast_normalize"                    |
  "length"                            |
  "native_distance"                   |
  "native_length"                     |
  "native_normalize"                  |
  "normalize"                         |
  "convert_"{RSVectorType}            |
  "rsExtractFrustumPlanes"            |
  "rsIsSphereInFrustum"               |
  "rsMatrixGet"                       |
  "rsMatrixInverse"                   |
  "rsMatrixInverseTranspose"          |
  "rsMatrixLoad"                      |
  "rsMatrixLoadFrustum"               |
  "rsMatrixLoadIdentity"              |
  "rsMatrixLoadMultiply"              |
  "rsMatrixLoadOrtho"                 |
  "rsMatrixLoadPerspective"           |
  "rsMatrixLoadRotate"                |
  "rsMatrixLoadScale"                 |
  "rsMatrixLoadTranslate"             |
  "rsMatrixMultiply"                  |
  "rsMatrixRotate"                    |
  "rsMatrixScale"                     |
  "rsMatrixSet"                       |
  "rsMatrixTranslate"                 |
  "rsMatrixTranspose"                 |
  "rsQuaternionAdd"                   |
  "rsQuaternionConjugate"             |
  "rsQuaternionDot"                   |
  "rsQuaternionGetMatrixUnit"         |
  "rsQuaternionLoadRotate"            |
  "rsQuaternionLoadRotateUnit"        |
  "rsQuaternionMultiply"              |
  "rsQuaternionNormalize"             |
  "rsQuaternionSet"                   |
  "rsQuaternionSlerp"                 |
  "rsAtomicAdd"                       |
  "rsAtomicAnd"                       |
  "rsAtomicCas"                       |
  "rsAtomicDec"                       |
  "rsAtomicInc"                       |
  "rsAtomicMax"                       |
  "rsAtomicMin"                       |
  "rsAtomicOr"                        |
  "rsAtomicSub"                       |
  "rsAtomicXor"                       |
  "rsGetDt"                           |
  "rsLocaltime"                       |
  "rsTime"                            |
  "rsUptimeMillis"                    |
  "rsUptimeNanos"                     |
  "rsAllocationCopy1DRange"           |
  "rsAllocationCopy2DRange"           |
  "rsAllocationVLoadX"                |
  "rsAllocationVLoadX_"{RSVectorType} |
  "rsAllocationVStoreX"               |
  "rsAllocationVStoreX_"{RSVectorType}|
  "rsGetElementAt"                    |
  "rsGetElementAt_"{RSPrimeType}      |
  "rsGetElementAt_"{RSVectorType}     |
  "rsGetElementAtYuv_uchar_U"         |
  "rsGetElementAtYuv_uchar_V"         |
  "rsGetElementAtYuv_uchar_Y"         |
  "rsSample"                          |
  "rsSetElementAt"                    |
  "rsSetElementAt_"{RSPrimeType}      |
  "rsSetElementAt_"{RSVectorType}     |
  "rsAllocationGetDimFaces"           |
  "rsAllocationGetDimLOD"             |
  "rsAllocationGetDimX"               |
  "rsAllocationGetDimY"               |
  "rsAllocationGetDimZ"               |
  "rsAllocationGetElement"            |
  "rsClearObject"                     |
  "rsElementGetBytesSize"             |
  "rsElementGetDataKind"              |
  "rsElementGetDataType"              |
  "rsElementGetSubElement"            |
  "rsElementGetSubElementArraySize"   |
  "rsElementGetSubElementCount"       |
  "rsElementGetSubElementName"        |
  "rsElementGetSubElementNameLength"  |
  "rsElementGetSubElementOffsetBytes" |
  "rsElementGetVectorSize"            |
  "rsIsObject"                        |
  "rsSamplerGetAnisotropy"            |
  "rsSamplerGetMagnification"         |
  "rsSamplerGetMinification"          |
  "rsSamplerGetWrapS"                 |
  "rsSamplerGetWrapT"                 |
  "rsForEach"                         |
  "rsGetArray0"                       |
  "rsGetArray1"                       |
  "rsGetArray2"                       |
  "rsGetArray3"                       |
  "rsGetDimArray0"                    |
  "rsGetDimArray1"                    |
  "rsGetDimArray2"                    |
  "rsGetDimArray3"                    |
  "rsGetDimHasFaces"                  |
  "rsGetDimLod"                       |
  "rsGetDimX"                         |
  "rsGetDimY"                         |
  "rsGetDimZ"                         |
  "rsGetFace"                         |
  "rsGetLod"                          |
  "rsAllocationIoReceive"             |
  "rsAllocationIoSend"                |
  "rsSendToClient"                    |
  "rsSendToClientBlocking"            |
  "rsDebug"                           |
  "rsPackColorTo8888"                 |
  "rsUnpackColor8888"                 |
  "rsYuvToRGBA"       { return RenderscriptTokenType.KEYWORD; }

  "("          |
  ")"          |
  "{"          |
  "}"          |
  "["          |
  "]"                 { return RenderscriptTokenType.BRACE; }

  ";"          |
  ","          |
  "."                 { return RenderscriptTokenType.SEPARATOR; }

  "="          |
  "!"          |
  "<"          |
  ">"          |
  "~"          |
  "?"          |
  ":"          |
  "=="         |
  "<="         |
  ">="         |
  "!="         |
  "&&"         |
  "||"         |
  "++"         |
  "--"         |
  "+"          |
  "-"          |
  "*"          |
  "/"          |
  "&"          |
  "|"          |
  "^"          |
  "%"          |
  "<<"         |
  ">>"         |
  "+="         |
  "-="         |
  "*="         |
  "/="         |
  "&="         |
  "|="         |
  "^="         |
  "%="         |
  "<<="        |
  ">>="               { return RenderscriptTokenType.OPERATOR; }

  {DecIntegerLiteral} |
  {DecLongLiteral}    |
  {HexIntegerLiteral} |
  {HexLongLiteral}    |
  {OctIntegerLiteral} |
  {OctLongLiteral}    |
  {FloatLiteral}      |
  {DoubleLiteral}     |
  {DoubleLiteral}[dD] { return RenderscriptTokenType.NUMBER; }

  {Comment}           { return RenderscriptTokenType.COMMENT; }
  {WhiteSpace}        { return TokenType.WHITE_SPACE; }

  {Identifier}        { return RenderscriptTokenType.IDENTIFIER; }
  {CharacterLiteral}  { return RenderscriptTokenType.CHARACTER; }
  {StringLiteral}     { return RenderscriptTokenType.STRING; }
}

[^]                   { return TokenType.BAD_CHARACTER; }
<<EOF>>               { return null; }