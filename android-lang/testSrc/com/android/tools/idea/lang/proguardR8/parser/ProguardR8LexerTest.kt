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
package com.android.tools.idea.lang.proguardR8.parser

import com.android.tools.idea.lang.AndroidLexerTestCase
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.ANY_TYPE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.ANY_TYPE_AND_NUM_OF_ARGS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.ASTERISK
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.AT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.CLOSE_BRACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.COLON
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.COMMA
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.DOT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FILE_NAME
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FILE_NAME_DOUBLE_QUOTED
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FILE_NAME_SINGLE_QUOTED
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.FLAG
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.IMPLEMENTS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.INT
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.JAVA_IDENTIFIER
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.JAVA_IDENTIFIER_WITH_WILDCARDS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.LPAREN
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.OPEN_BRACE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.PUBLIC
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.RPAREN
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SEMICOLON
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.STATIC
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._INIT_
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes._METHODS_

class ProguardR8LexerTest : AndroidLexerTestCase(ProguardR8Lexer()) {

  fun testOneRule() {
    assertTokenTypes(
      "-android",
      "-android" to FLAG
    )
  }

  fun testSimpleRule() {
    assertTokenTypes(
      "-android -dontpreverify",
      "-android" to FLAG,
      SPACE,
      "-dontpreverify" to FLAG
    )

    assertTokenTypes(
      "-android\n-dontpreverify",
      "-android" to FLAG,
      NEWLINE,
      "-dontpreverify" to FLAG
    )
  }

  fun testSimpleRuleWithArg() {
    assertTokenTypes(
      """
        -outjars bin/application.apk
        -libraryjars /usr/local/android-sdk/platforms/android-28/android.jar
      """.trimIndent(),

      "-outjars" to FLAG,
      SPACE,
      "bin/application.apk" to FILE_NAME,
      NEWLINE,
      "-libraryjars" to FLAG,
      SPACE,
      "/usr/local/android-sdk/platforms/android-28/android.jar" to FILE_NAME
    )
  }

  fun testRuleWithClassSpecification() {
    assertTokenTypes(
      """
        -keepclasseswithmembers class * {
        public <init>(android.content.Context, android.util.AttributeSet, int);
        }
      """.trimIndent(),
      "-keepclasseswithmembers" to FLAG,
      SPACE,
      "class" to CLASS,
      SPACE,
      "*" to ASTERISK,
      SPACE,
      "{" to OPEN_BRACE,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "<init>" to _INIT_,
      "(" to LPAREN,
      "android" to JAVA_IDENTIFIER,
      "." to DOT,
      "content" to JAVA_IDENTIFIER,
      "." to DOT,
      "Context" to JAVA_IDENTIFIER,
      "," to COMMA,
      SPACE,
      "android" to JAVA_IDENTIFIER,
      "." to DOT,
      "util" to JAVA_IDENTIFIER,
      "." to DOT,
      "AttributeSet" to JAVA_IDENTIFIER,
      "," to COMMA,
      SPACE,
      "int" to INT,
      ")" to RPAREN,
      ";" to SEMICOLON,
      NEWLINE,
      "}" to CLOSE_BRACE
    )
  }

  fun testDistinguishAnnotationAndFilename() {
    assertTokenTypes(
      """
        -keepclassmembers class * implements @javax.annotation.Resource java.io.Serializable
      """.trimIndent(),
      "-keepclassmembers" to FLAG,
      SPACE,
      "class" to CLASS,
      SPACE,
      "*" to ASTERISK,
      SPACE,
      "implements" to IMPLEMENTS,
      SPACE,
      "@" to AT,
      "javax" to JAVA_IDENTIFIER,
      "." to DOT,
      "annotation" to JAVA_IDENTIFIER,
      "." to DOT,
      "Resource" to JAVA_IDENTIFIER,
      SPACE,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "io" to JAVA_IDENTIFIER,
      "." to DOT,
      "Serializable" to JAVA_IDENTIFIER
    )
  }

  fun testDifferentJavaArgumentTypes() {
    assertTokenTypes(
      """
        -assumenoexternalsideeffects class **java.lang.StringBuilder {
        public java.lang.StringBuilder();
        public java.lang.StringBuilder(...);
        public java.lang.StringBuilder(int);
        public java.lang.StringBuilder append(java.lang.StringBuffer);
        }
      """.trimIndent(),
      "-assumenoexternalsideeffects" to FLAG,
      SPACE,
      "class" to CLASS,
      SPACE,
      "**java" to JAVA_IDENTIFIER_WITH_WILDCARDS,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      SPACE,
      "{" to OPEN_BRACE,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      "(" to LPAREN,
      ")" to RPAREN,
      ";" to SEMICOLON,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      "(" to LPAREN,
      "..." to ANY_TYPE_AND_NUM_OF_ARGS,
      ")" to RPAREN,
      ";" to SEMICOLON,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      "(" to LPAREN,
      "int" to INT,
      ")" to RPAREN,
      ";" to SEMICOLON,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      SPACE,
      "append" to JAVA_IDENTIFIER,
      "(" to LPAREN,
      "java" to JAVA_IDENTIFIER,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuffer" to JAVA_IDENTIFIER,
      ")" to RPAREN,
      ";" to SEMICOLON,
      NEWLINE,
      "}" to CLOSE_BRACE
    )
  }

  fun testFileList() {
    assertTokenTypes(
      """-injars "my program.jar":'/your directory/your program.jar';<java.home>/lib/rt.jar""".trimIndent(),
      "-injars" to FLAG,
      SPACE,
      "\"my program.jar\"" to FILE_NAME_DOUBLE_QUOTED,
      ":" to COLON,
      "'/your directory/your program.jar'" to FILE_NAME_SINGLE_QUOTED,
      ";" to SEMICOLON,
      "<java.home>/lib/rt.jar" to FILE_NAME
    )
  }

  fun testSubclassName() {
    assertTokenTypes(
      "-keepclassmembers class **.R$*",
      "-keepclassmembers" to FLAG,
      SPACE,
      "class" to CLASS,
      SPACE,
      "**" to JAVA_IDENTIFIER_WITH_WILDCARDS,
      "." to DOT,
      "R$*" to JAVA_IDENTIFIER_WITH_WILDCARDS
    )
  }

  fun testClassSpecification() {
    assertTokenTypes(
      """
        -assumenoexternalsideeffects class **java.lang.StringBuilder {
        static *** fieldName;
        public <methods>;
        }
      """.trimIndent(),
      "-assumenoexternalsideeffects" to FLAG,
      SPACE,
      "class" to CLASS,
      SPACE,
      "**java" to JAVA_IDENTIFIER_WITH_WILDCARDS,
      "." to DOT,
      "lang" to JAVA_IDENTIFIER,
      "." to DOT,
      "StringBuilder" to JAVA_IDENTIFIER,
      SPACE,
      "{" to OPEN_BRACE,
      NEWLINE,
      "static" to STATIC,
      SPACE,
      "***" to ANY_TYPE,
      SPACE,
      "fieldName" to JAVA_IDENTIFIER,
      ";" to SEMICOLON,
      NEWLINE,
      "public" to PUBLIC,
      SPACE,
      "<methods>" to _METHODS_,
      ";" to SEMICOLON,
      NEWLINE,
      "}" to CLOSE_BRACE
    )
  }
}
