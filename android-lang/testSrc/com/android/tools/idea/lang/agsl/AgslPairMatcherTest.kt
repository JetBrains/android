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

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

class AgslPairMatcherTest : BasePlatformTestCase() {
  @Language("AGSL")
  private val source =
    """
    float triangleNoise(vec2 n) {
        n  = fract(n * vec2(5.3987, 5.4421));
        n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
        float xy = n.x * n.y;
        return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
    } // end
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

  fun testBraces() {
    myFixture.configureByText(AgslFileType.INSTANCE, source.insertCaret("(vec2 n) |{"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("|} // end"), offset)
  }

  fun testParentheses() {
    myFixture.configureByText(AgslFileType.INSTANCE, source.insertCaret("|(vec2 n)"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("(vec2 n|)"), offset)
  }

  fun testNestedOuterParentheses() {
    myFixture.configureByText(AgslFileType.INSTANCE, source.insertCaret("n += dot|(n.yx, n.xy + vec2(21.5351, 14.3137))"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("n += dot(n.yx, n.xy + vec2(21.5351, 14.3137)|)"), offset)
  }

  fun testNestedInnerParentheses() {
    myFixture.configureByText(AgslFileType.INSTANCE, source.insertCaret("n += dot(n.yx, n.xy + vec2|(21.5351, 14.3137))"))
    val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    assertEquals(source.offset("n += dot(n.yx, n.xy + vec2(21.5351, 14.3137|))"), offset)
  }
}
