/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.helpers.declarative

import com.android.tools.idea.gradle.declarative.DeclarativeFileType
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

class DeclarativePairedBraceMatcherTest : BasePlatformTestCase() {
  @Language("Declarative")
  private val source =
    """
    androidApplication {
        namespace = "com.example.myapplication"
        foo("bar")
        block { foo = "bar" }
    }
    """.trimIndent()

  private val caret = "<caret>"
  private fun String.offset(window: String): Int {
    val delta = window.indexOf(caret)
    val withoutCursor = window.substring(0, delta) + window.substring(delta + caret.length)
    val index = indexOf(withoutCursor)
    return index + delta
  }

  private fun String.insertCaret(window: String): String {
    val offset = offset(window)
    return substring(0, offset) + caret + substring(offset)
  }

  fun testParentheses() {
    myFixture.configureByText(DeclarativeFileType.INSTANCE, source.insertCaret("foo<caret>(\"bar\")"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("foo(\"bar\"<caret>)"), offset)
  }

  fun testBraces() {
    myFixture.configureByText(DeclarativeFileType.INSTANCE, source.insertCaret("block <caret>{ foo = \"bar\" }"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("block { foo = \"bar\" <caret>}"), offset)
  }
}