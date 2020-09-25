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
package com.android.tools.idea.lang.multiDexKeep

import com.android.tools.idea.lang.AndroidParsingTestCase
import com.intellij.psi.TokenType

class MultiDexKeepParserTest : AndroidParsingTestCase(MultiDexKeepFileType.INSTANCE.defaultExtension, MultiDexKeepParserDefinition()) {
  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  private fun check(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = MultiDexKeepLexerAdapter()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }

  fun testParseResultOnFailure() {
    assertEquals(
      """
        MultiDexKeep File
          MultiDexKeepClassNamesImpl(CLASS_NAMES)
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('this')
          PsiErrorElement:class file name or new line expected
            PsiElement(BAD_CHARACTER)(' ')
          PsiElement(class file name)('should')
          PsiElement(BAD_CHARACTER)(' ')
          PsiElement(class file name)('fail')
      """.trimIndent(),
      toParseTreeText("this should fail")
    )
  }

  fun testNoParseErrors() {
    check("com/somePackage/SomeClass.class")
  }

  fun testParsedResult() {
    assertEquals(
      """
        MultiDexKeep File
          MultiDexKeepClassNamesImpl(CLASS_NAMES)
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/SomeClass.class')
      """.trimIndent(),
      toParseTreeText("""
        com/somePackage/SomeClass.class

      """.trimIndent())
    )
  }

  fun testInnerClasses() {
    assertEquals(
      """
        MultiDexKeep File
          MultiDexKeepClassNamesImpl(CLASS_NAMES)
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/SomeClass${'$'}Inner.class')
      """.trimIndent(),
      toParseTreeText("com/somePackage/SomeClass\$Inner.class")
    )
  }

  fun testMultipleLinesParsedResult() {
    assertEquals(
      """
        MultiDexKeep File
          MultiDexKeepClassNamesImpl(CLASS_NAMES)
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/SomeClass.class')
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/AnotherClass.class')
      """.trimIndent(),
      toParseTreeText("""
        com/somePackage/SomeClass.class
        com/somePackage/AnotherClass.class

      """.trimIndent())
    )
  }

  fun testEmptyLinesBetweenClassNamesParsedResult() {
    assertEquals(
      """
        MultiDexKeep File
          MultiDexKeepClassNamesImpl(CLASS_NAMES)
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/SomeClass.class')
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/AnotherClass.class')
            MultiDexKeepClassNameImpl(CLASS_NAME)
              PsiElement(class file name)('com/somePackage/OneLastClass.class')
      """.trimIndent(),
      toParseTreeText("""
        com/somePackage/SomeClass.class



        com/somePackage/AnotherClass.class

        com/somePackage/OneLastClass.class

      """.trimIndent())
    )
  }
}