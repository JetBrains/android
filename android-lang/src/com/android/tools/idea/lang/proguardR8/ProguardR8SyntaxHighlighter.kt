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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.parser.ProguardR8Lexer
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.ABSTRACT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.AT_INTERFACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.BOOLEAN
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.BYTE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.CHAR
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.CLOSE_BRACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.COMMA
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.DOUBLE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.DOUBLE_QUOTED_CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.DOUBLE_QUOTED_STRING
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.ENUM
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.EXTENDS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FILE_NAME
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FINAL
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FLAG
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FLOAT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.IMPLEMENTS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.INT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.INTERFACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.JAVA_IDENTIFIER
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.JAVA_IDENTIFIER_WITH_WILDCARDS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.LINE_CMT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.LONG
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.LPAREN
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.NATIVE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.OPEN_BRACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.PRIVATE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.PROTECTED
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.PUBLIC
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.RPAREN
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SEMICOLON
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SHORT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SINGLE_QUOTED_CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SINGLE_QUOTED_STRING
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.STATIC
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.STRICTFP
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SYNCHRONIZED
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.TRANSIENT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.VOID
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.VOLATILE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._CLINIT_
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._FIELDS_
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._INIT_
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._METHODS_
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

val JAVA_KEY_WORDS = TokenSet.create(CLASS, INTERFACE, ENUM, EXTENDS, IMPLEMENTS, STATIC, ABSTRACT, PRIVATE, PROTECTED, PUBLIC,
                                     SYNCHRONIZED, STRICTFP, FINAL, NATIVE, VOLATILE, TRANSIENT, AT_INTERFACE)

val JAVA_PRIMITIVE = TokenSet.create(BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, VOID)

val JAVA_IDENTIFIER_TOKENS = TokenSet.create(JAVA_IDENTIFIER, JAVA_IDENTIFIER_WITH_WILDCARDS, SINGLE_QUOTED_CLASS, DOUBLE_QUOTED_CLASS)

val PATHS = TokenSet.create(FILE_NAME, SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING)

val METHOD_FIELD_WILDCARDS = TokenSet.create(_INIT_, _CLINIT_, _FIELDS_, _METHODS_)

enum class ProguardR8TextAttributes(fallback: TextAttributesKey) {
  BAD_CHARACTER(HighlighterColors.BAD_CHARACTER),
  KEYWORD(DefaultLanguageHighlighterColors.KEYWORD),
  PARAMETER(DefaultLanguageHighlighterColors.INSTANCE_FIELD),
  BRACES(DefaultLanguageHighlighterColors.BRACES),
  PARENTHESES(DefaultLanguageHighlighterColors.PARENTHESES),
  STRING(DefaultLanguageHighlighterColors.STRING),
  METHOD_FIELD_WILDCARDS(DefaultLanguageHighlighterColors.STATIC_FIELD),
  LINE_COMMENT(DefaultLanguageHighlighterColors.LINE_COMMENT),
  COMMA(DefaultLanguageHighlighterColors.COMMA),
  SEMICOLON(DefaultLanguageHighlighterColors.SEMICOLON),
  FLAG(DefaultLanguageHighlighterColors.CONSTANT),
  IDENTIFIER(DefaultLanguageHighlighterColors.IDENTIFIER),
  ANNOTATION(DefaultLanguageHighlighterColors.METADATA)
  ;

  val key = TextAttributesKey.createTextAttributesKey("PROGUARD_R8_$name", fallback)
  val keys = arrayOf(key)
}

class ProguardR8SyntaxHighlighter : SyntaxHighlighterBase() {

  override fun getHighlightingLexer(): Lexer {
    return ProguardR8Lexer()
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    // Return the appropriate text attributes depending on the type of token.
    when (tokenType) {
      LINE_CMT -> return ProguardR8TextAttributes.LINE_COMMENT.keys
      TokenType.BAD_CHARACTER -> return ProguardR8TextAttributes.BAD_CHARACTER.keys
      in JAVA_IDENTIFIER_TOKENS -> return ProguardR8TextAttributes.IDENTIFIER.keys
      in METHOD_FIELD_WILDCARDS -> return ProguardR8TextAttributes.METHOD_FIELD_WILDCARDS.keys
      in PATHS -> return ProguardR8TextAttributes.STRING.keys
      LPAREN, RPAREN -> return ProguardR8TextAttributes.PARENTHESES.keys
      CLOSE_BRACE, OPEN_BRACE -> return ProguardR8TextAttributes.BRACES.keys
      SEMICOLON -> return ProguardR8TextAttributes.SEMICOLON.keys
      COMMA -> return ProguardR8TextAttributes.COMMA.keys

      FLAG -> return ProguardR8TextAttributes.FLAG.keys
      else -> return TextAttributesKey.EMPTY_ARRAY
    }
  }
}

class ProguardR8SyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
    return ProguardR8SyntaxHighlighter()
  }
}
