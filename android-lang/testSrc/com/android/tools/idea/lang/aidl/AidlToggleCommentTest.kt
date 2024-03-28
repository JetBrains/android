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
class AidlToggleCommentTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val fixture by lazy { projectRule.fixture }

  // This is how this case handled in other languages
  @Test
  fun comment() = doTest("test", "//test")
  @Test
  fun uncomment() = doTest("//test", "test")
  @Test
  fun commentOnEmptyLine() = doTest("<caret>\n", "//<caret>\n")
  @Test
  fun uncommentOnEmptyLine() = doTest("// <caret>\n", "\n<caret>")
  @Test
  fun blockComment() = doTest(
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

  @Test
  fun blockUncomment() = doTest(
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
    fixture.configureByText("file.agsl", before)

    application.invokeAndWait {
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    }

    fixture.checkResult(after)
  }
}