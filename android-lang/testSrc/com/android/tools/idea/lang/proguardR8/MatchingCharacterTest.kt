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

import com.intellij.openapi.fileTypes.LanguageFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MatchingCharacterTest(private val fileType: LanguageFileType)  : ProguardR8TestCase() {
  @Test
  fun testMatchesBraces() {
    myFixture.configureByText(
      fileType,
      """
      -keep class MyClass <caret>
      """.trimIndent()
    )

    myFixture.type('{')

    myFixture.checkResult(
      """
        -keep class MyClass {}
      """.trimIndent()
    )
  }
  @Test
  fun testMatchesParenthesis() {
    myFixture.configureByText(
      fileType,
      """
      -keep class MyClass {
        int method<caret>
      """.trimIndent()
    )

    myFixture.type('(')

    myFixture.checkResult(
      """
        -keep class MyClass {
          int method()
      """.trimIndent()
    )
  }
  @Test
  fun testMatchesQuotes() {
    myFixture.configureByText(
      fileType,
      """
      -include <caret>
      """.trimIndent()
    )

    myFixture.type('\'')

    myFixture.checkResult(
      """
        -include ''
      """.trimIndent()
    )
    myFixture.configureByText(
      fileType,
      """
      -include <caret>
      """.trimIndent()
    )

    myFixture.type('"')

    myFixture.checkResult(
      """
        -include ""
      """.trimIndent()
    )
  }
}