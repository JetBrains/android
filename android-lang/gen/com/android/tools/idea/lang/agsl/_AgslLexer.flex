package com.android.tools.idea.lang.agsl;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.agsl.AgslTokenTypes.*;

%%

%{
  public _AgslLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _AgslLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

SPACE=[ \t\n\x0B\f\r]+
COMMENT="//"[^\r\n]*
BLOCK_COMMENT="/"[*][^*]*[*]+([^/*][^*]*[*]+)*"/"
FLOATCONSTANT=([0-9]+\.[0-9]*|[0-9]*\.[0-9]+)([eE][-+]?[0-9]+)?|1[eE][-+]?[0-9]+
INTCONSTANT=0[xX][\da-fA-F]+|[0-9]+
IDENTIFIER_GL_PREFIX=gl_[a-zA-Z_0-9]*
IDENTIFIER=[_a-zA-Z][a-zA-Z_0-9]*

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

  "attribute"                 { return ATTRIBUTE; }
  "const"                     { return CONST; }
  "uniform"                   { return UNIFORM; }
  "varying"                   { return VARYING; }
  "break"                     { return BREAK; }
  "continue"                  { return CONTINUE; }
  "do"                        { return DO; }
  "for"                       { return FOR; }
  "while"                     { return WHILE; }
  "if"                        { return IF; }
  "else"                      { return ELSE; }
  "in"                        { return IN; }
  "out"                       { return OUT; }
  "inout"                     { return INOUT; }
  "float"                     { return FLOAT; }
  "int"                       { return INT; }
  "void"                      { return VOID; }
  "bool"                      { return BOOL; }
  "true"                      { return TRUE; }
  "false"                     { return FALSE; }
  "lowp"                      { return LOW_PRECISION; }
  "mediump"                   { return MEDIUM_PRECISION; }
  "highp"                     { return HIGH_PRECISION; }
  "precision"                 { return PRECISION; }
  "invariant"                 { return INVARIANT; }
  "return"                    { return RETURN; }
  "mat2"                      { return MAT2; }
  "mat3"                      { return MAT3; }
  "mat4"                      { return MAT4; }
  "vec2"                      { return VEC2; }
  "vec3"                      { return VEC3; }
  "vec4"                      { return VEC4; }
  "ivec2"                     { return IVEC2; }
  "ivec3"                     { return IVEC3; }
  "ivec4"                     { return IVEC4; }
  "bvec2"                     { return BVEC2; }
  "bvec3"                     { return BVEC3; }
  "bvec4"                     { return BVEC4; }
  "sampler2d"                 { return SAMPLER2D; }
  "samplerCube"               { return SAMPLERCUBE; }
  "struct"                    { return STRUCT; }
  "discard"                   { return DISCARD; }
  "half"                      { return HALF; }
  "half2"                     { return HALF2; }
  "half3"                     { return HALF3; }
  "half4"                     { return HALF4; }
  "float2"                    { return FLOAT2; }
  "float3"                    { return FLOAT3; }
  "float4"                    { return FLOAT4; }
  "bool2"                     { return BOOL2; }
  "bool3"                     { return BOOL3; }
  "bool4"                     { return BOOL4; }
  "int2"                      { return INT2; }
  "int3"                      { return INT3; }
  "int4"                      { return INT4; }
  "float2x2"                  { return FLOAT2X2; }
  "float3x3"                  { return FLOAT3X3; }
  "float4x4"                  { return FLOAT4X4; }
  "half2x2"                   { return HALF2X2; }
  "half3x3"                   { return HALF3X3; }
  "half4x4"                   { return HALF4X4; }
  "shader"                    { return SHADER; }
  "colorFilter"               { return COLORFILTER; }
  "blender"                   { return BLENDER; }
  "asm"                       { return ASM; }
  "class"                     { return CLASS; }
  "union"                     { return UNION; }
  "enum"                      { return ENUM; }
  "typedef"                   { return TYPEDEF; }
  "template"                  { return TEMPLATE; }
  "this"                      { return THIS; }
  "packed"                    { return PACKED; }
  "goto"                      { return GOTO; }
  "switch"                    { return SWITCH; }
  "default"                   { return DEFAULT; }
  "inline"                    { return INLINE; }
  "noinline"                  { return NOINLINE; }
  "volatile"                  { return VOLATILE; }
  "public"                    { return PUBLIC; }
  "static"                    { return STATIC; }
  "extern"                    { return EXTERN; }
  "external"                  { return EXTERNAL; }
  "interface"                 { return INTERFACE; }
  "flat"                      { return FLAT; }
  "long"                      { return LONG; }
  "short"                     { return SHORT; }
  "double"                    { return DOUBLE; }
  "fixed"                     { return FIXED; }
  "unsigned"                  { return UNSIGNED; }
  "superp"                    { return SUPERP; }
  "input"                     { return INPUT; }
  "output"                    { return OUTPUT; }
  "hvec2"                     { return HVEC2; }
  "hvec3"                     { return HVEC3; }
  "hvec4"                     { return HVEC4; }
  "dvec2"                     { return DVEC2; }
  "dvec3"                     { return DVEC3; }
  "dvec4"                     { return DVEC4; }
  "fvec2"                     { return FVEC2; }
  "fvec3"                     { return FVEC3; }
  "fvec4"                     { return FVEC4; }
  "sampler1D"                 { return SAMPLER1D; }
  "sampler3D"                 { return SAMPLER3D; }
  "sampler1DShadow"           { return SAMPLER1DSHADOW; }
  "sampler2DShadow"           { return SAMPLER2DSHADOW; }
  "sampler2DRect"             { return SAMPLER2DRECT; }
  "sampler3DRect"             { return SAMPLER3DRECT; }
  "sampler2DRectShadow"       { return SAMPLER2DRECTSHADOW; }
  "sizeof"                    { return SIZEOF; }
  "cast"                      { return CAST; }
  "namespace"                 { return NAMESPACE; }
  "using"                     { return USING; }
  "<<"                        { return LEFT_OP; }
  ">>"                        { return RIGHT_OP; }
  "++"                        { return INC_OP; }
  "--"                        { return DEC_OP; }
  "<="                        { return LE_OP; }
  ">="                        { return GE_OP; }
  "=="                        { return EQ_OP; }
  "!="                        { return NE_OP; }
  "&&"                        { return AND_OP; }
  "||"                        { return OR_OP; }
  "^^"                        { return XOR_OP; }
  "*="                        { return MUL_ASSIGN; }
  "/="                        { return DIV_ASSIGN; }
  "+="                        { return ADD_ASSIGN; }
  "%="                        { return MOD_ASSIGN; }
  "<<="                       { return LEFT_ASSIGN; }
  ">>="                       { return RIGHT_ASSIGN; }
  "&="                        { return AND_ASSIGN; }
  "^="                        { return XOR_ASSIGN; }
  "|="                        { return OR_ASSIGN; }
  "-="                        { return SUB_ASSIGN; }
  "("                         { return LEFT_PAREN; }
  ")"                         { return RIGHT_PAREN; }
  "["                         { return LEFT_BRACKET; }
  "]"                         { return RIGHT_BRACKET; }
  "{"                         { return LEFT_BRACE; }
  "}"                         { return RIGHT_BRACE; }
  "."                         { return DOT; }
  ","                         { return COMMA; }
  ":"                         { return COLON; }
  "="                         { return EQUAL; }
  ";"                         { return SEMICOLON; }
  "!"                         { return BANG; }
  "-"                         { return DASH; }
  "~"                         { return TILDE; }
  "+"                         { return PLUS; }
  "*"                         { return STAR; }
  "/"                         { return SLASH; }
  "%"                         { return PERCENT; }
  "<"                         { return LEFT_ANGLE; }
  ">"                         { return RIGHT_ANGLE; }
  "|"                         { return VERTICAL_BAR; }
  "^"                         { return CARET; }
  "&"                         { return AMPERSAND; }
  "?"                         { return QUESTION; }

  {SPACE}                     { return SPACE; }
  {COMMENT}                   { return COMMENT; }
  {BLOCK_COMMENT}             { return BLOCK_COMMENT; }
  {FLOATCONSTANT}             { return FLOATCONSTANT; }
  {INTCONSTANT}               { return INTCONSTANT; }
  {IDENTIFIER_GL_PREFIX}      { return IDENTIFIER_GL_PREFIX; }
  {IDENTIFIER}                { return IDENTIFIER; }

}

[^] { return BAD_CHARACTER; }
