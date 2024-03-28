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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.application
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgslCommenterTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture by lazy { projectRule.fixture }

  // This is how this case handled in other languages
  @Test
  fun comment() = toggleLineComment("test", "//test")
  @Test
  fun uncomment() = toggleLineComment("//test", "test")
  @Test
  fun commentOnEmptyLine() = toggleLineComment("<caret>\n", "//<caret>\n")
  @Test
  fun uncommentOnEmptyLine() = toggleLineComment("// <caret>\n", "\n<caret>")
  @Test
  fun lineCommentBlock() = toggleLineComment(
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

  @Test
  fun lineUncommentBlock() = toggleLineComment(
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

  @Test
  fun commentBlock() = toggleBlockComment(
    """
    <block>uniform shader</block> imageB;
    """.trimIndent(),
    """
    /*uniform shader*/ imageB;
    """.trimIndent()
  )

  @Test
  fun uncommentBlock() = toggleBlockComment(
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
    fixture.configureByText("file.agsl", before)

    application.invokeAndWait {
      PlatformTestUtil.invokeNamedAction(actionId)
    }

    fixture.checkResult(after)
  }
}
