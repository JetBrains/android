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
package com.android.tools.idea.lang.aidl

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

class AidlPairedBraceMatcherTest : BasePlatformTestCase() {
  @Language("AIDL")
  private val source =
    """
    @Backing(type="byte")
    union Union {
      int[] ns = {};
    }
    """.trimIndent()

  private fun String.offset(window: String): Int {
    val delta = window.indexOf('|')
    assertTrue("must include | as caret position", delta != -1)
    val withoutCursor = window.substring(0, delta) + window.substring(delta + 1)
    val index = indexOf(withoutCursor)
    assertTrue("Did not find `$withoutCursor` in $this", index != -1)
    return index + delta
  }

  private fun String.insertCaret(window: String): String {
    val offset = offset(window)
    return substring(0, offset) + "<caret>" + substring(offset)
  }

  fun testBrackets() {
    myFixture.configureByText(AidlFileType.INSTANCE, source.insertCaret("int|[] ns = {};"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("int[|] ns = {};"), offset)
  }

  fun testParentheses() {
    myFixture.configureByText(AidlFileType.INSTANCE, source.insertCaret("king|(type=\"byte\")"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("king(type=\"byte\"|)"), offset)
  }
}