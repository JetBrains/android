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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language

class AgslCommenterTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = com.android.tools.idea.lang.getTestDataPath()

  // This is how this case handled in other languages
  fun testComment() = toggleLineComment("test", "//test")
  fun testUncomment() = toggleLineComment("//test", "test")
  fun testCommentOnEmptyLine() = toggleLineComment("<caret>\n", "//<caret>\n")
  fun testUncommentOnEmptyLine() = toggleLineComment("// <caret>\n", "\n<caret>")
  fun testLineCommentBlock() = toggleLineComment(
    """
    uniform shader imageA;
    <block>uniform shader imageB;
    uniform ivec2</block> imageDimensions;
    uniform float progress;
    """.trimIndent(),
    """
    uniform shader imageA;
    //uniform shader imageB;
    //uniform ivec2 imageDimensions;
    uniform float progress;
    """.trimIndent()
  )

  fun testLineUncommentBlock() = toggleLineComment(
    """
    uniform shader imageA;
    <block>//uniform shader imageB;
    //uniform ivec2</block> imageDimensions;
    uniform float progress;
    """.trimIndent(),
    """
    uniform shader imageA;
    uniform shader imageB;
    uniform ivec2 imageDimensions;
    uniform float progress;
    """.trimIndent()
  )

  fun testCommentBlock() = toggleBlockComment(
    """
    <block>uniform shader</block> imageB;
    """.trimIndent(),
    """
    /*uniform shader*/ imageB;
    """.trimIndent()
  )

  fun testUncommentBlock() = toggleBlockComment(
    """
    uniform shader imageA;
    <block>/*uniform shader*/</block> imageB;
    """.trimIndent(),
    """
    uniform shader imageA;
    uniform shader imageB;
    """.trimIndent()
  )

  private fun toggleLineComment(@Language("AGSL") before: String, @Language("AGSL") after: String) {
    doTest(before, after, IdeActions.ACTION_COMMENT_LINE)
  }

  private fun toggleBlockComment(@Language("AGSL") before: String, @Language("AGSL") after: String) {
    doTest(before, after, IdeActions.ACTION_COMMENT_BLOCK)
  }

  private fun doTest(@Language("AGSL") before: String, @Language("AGSL") after: String, actionId: String) {
    configureFromFileText("file.agsl", before)
    PlatformTestUtil.invokeNamedAction(actionId)
    checkResultByText(after)
  }
}
