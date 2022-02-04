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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language

class AidlToggleCommentTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = com.android.tools.idea.lang.getTestDataPath()

  // This is how this case handled in other languages
  fun testComment() = doTest("test", "//test")
  fun testUncomment() = doTest("//test", "test")
  fun testCommentOnEmptyLine() = doTest("<caret>\n", "//<caret>\n")
  fun testUncommentOnEmptyLine() = doTest("// <caret>\n", "\n<caret>")
  fun testBlockComment() = doTest(
    """
    enum ByteEnum {
        <block>// Comment about FOO.
        FOO = 1,
        BAR =</block> 2,
        BAZ,
    }
    """.trimIndent(),
    """
    enum ByteEnum {
    //    // Comment about FOO.
    //    FOO = 1,
    //    BAR = 2,
        BAZ,
    }
    """.trimIndent()
  )

  fun testBlockUncomment() = doTest(
    """
    enum ByteEnum {
        //// Co<block>mment about FOO.
        //FOO = 1,</block>
        //BAR = 2,
        BAZ,
    }
    """.trimIndent(),
    """
    enum ByteEnum {
        // Comment about FOO.
        FOO = 1,
        //BAR = 2,
        BAZ,
    }
    """.trimIndent()
  )

  private fun doTest(@Language("AIDL") before: String, @Language("AIDL") after: String) {
    configureFromFileText("file.aidl", before)
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByText(after)
  }
}