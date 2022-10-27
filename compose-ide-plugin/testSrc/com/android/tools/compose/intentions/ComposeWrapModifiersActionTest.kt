/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose.intentions

import com.android.tools.compose.COMPOSE_UI_PACKAGE
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings

/**
 * Test for [ComposeWrapModifiersAction].
 */
class ComposeWrapModifiersActionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
    myFixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun adjust():Modifier
      companion object : Modifier {
        fun adjust():Modifier {}
      }
    }

    fun Modifier.extentionFunction():Modifier { return this}
    """.trimIndent()
    )

    val settings = CodeStyle.getSettings(project).getCustomSettings(KotlinCodeStyleSettings::class.java)
    settings.CONTINUATION_INDENT_FOR_CHAINED_CALLS = false
  }

  fun testCaretAtDifferentPosition() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modif<caret>ier.adjust().adjust()
          val m2 = Modifier.adjust().adjust()
          val m3 = Modifier.adjust().adjust()
      }
      """.trimIndent()
    )

    var action = myFixture.availableIntentions.find { it.text == "Wrap modifiers" }
    Truth.assertThat(action).isNotNull()
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      action!!.invoke(project, myFixture.editor, myFixture.file)
    }

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier
              .adjust()
              .adjust()
          val m2 = Modifier.adjust().adjust()
          val m3 = Modifier.adjust().adjust()
      }
      """.trimIndent()
    )

    myFixture.moveCaret("val m2 = Modifier.adj|ust().adjust()")
    action = myFixture.availableIntentions.find { it.text == "Wrap modifiers" }
    Truth.assertThat(action).isNotNull()
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      action!!.invoke(project, myFixture.editor, myFixture.file)
    }

    myFixture.moveCaret("val m3 = Modifier.adjust().adju|st()")
    action = myFixture.availableIntentions.find { it.text == "Wrap modifiers" }
    Truth.assertThat(action).isNotNull()
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      action!!.invoke(project, myFixture.editor, myFixture.file)
    }


    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier
              .adjust()
              .adjust()
          val m2 = Modifier
              .adjust()
              .adjust()
          val m3 = Modifier
              .adjust()
              .adjust()
      }
      """.trimIndent()
    )
  }

  fun `test don't suggest when it's already wrapped`() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier
              .ad<caret>just()
              .adjust()
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Wrap modifiers" }
    Truth.assertThat(action).isNull()
  }
}