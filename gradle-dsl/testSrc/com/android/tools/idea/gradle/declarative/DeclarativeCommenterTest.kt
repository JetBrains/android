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
package com.android.tools.idea.gradle.declarative

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language

class DeclarativeCommenterTest : LightPlatformCodeInsightTestCase() {
  fun testComment() = toggleLineComment("test", "//test")
  fun testUncomment() = toggleLineComment("//test", "test")
  fun testCommentOnEmptyLine() = toggleLineComment("<caret>\n", "//<caret>\n")
  fun testUncommentOnEmptyLine() = toggleLineComment("// <caret>\n", "\n<caret>")
  fun testLineCommentBlock() = toggleLineComment(
    """
    android {
      <block>multi line comment
      description</block>
      defaultConfig { }
    }
    """.trimIndent(),
    """
    android {
    //  multi line comment
    //  description
      defaultConfig { }
    }
    """.trimIndent()
  )

  fun testLineUncommentBlock() = toggleLineComment(
    """
    android {
    <block>//  compileOptions {
    //    sourceCompatibility = JavaVersion.VERSION_1_8
    //    targetCompatibility = JavaVersion.VERSION_1_8
    //  }</block>
      defaultConfig { }
    }
    """.trimIndent(),
    """
    android {
      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
      }
      defaultConfig { }
    }
    """.trimIndent()
  )

  fun testCommentBlock() = toggleBlockComment(
    """
    <block>comment</block> plugins{}
    """.trimIndent(),
    """
    /*comment*/ plugins{}
    """.trimIndent()
  )

  fun testUncommentBlock() = toggleBlockComment(
    """
    plugins{
      <block>/*apply(libs.plugins.app)*/</block>
      apply(libs.plugins.lib)
    }
    """.trimIndent(),
    """
    plugins{
      apply(libs.plugins.app)
      apply(libs.plugins.lib)
    }
    """.trimIndent()
  )

  private fun toggleLineComment(@Language("Declarative") before: String, @Language("Declarative") after: String) {
    doTest(before, after, IdeActions.ACTION_COMMENT_LINE)
  }

  private fun toggleBlockComment(@Language("Declarative") before: String, @Language("Declarative") after: String) {
    doTest(before, after, IdeActions.ACTION_COMMENT_BLOCK)
  }

  private fun doTest(@Language("Declarative") before: String, @Language("Declarative") after: String, actionId: String) {
    configureFromFileText("build.gradle.dcl", before)
    PlatformTestUtil.invokeNamedAction(actionId)
    checkResultByText(after)
  }
}