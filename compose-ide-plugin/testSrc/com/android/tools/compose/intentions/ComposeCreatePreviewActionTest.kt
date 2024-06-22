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

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSE_UI_TOOLING_PREVIEW_PACKAGE
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

/** Test for [ComposeCreatePreviewActionK1] and [ComposeCreatePreviewActionK2] */
class ComposeCreatePreviewActionTest : JavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
    myFixture.stubPreviewAnnotation()
  }

  private fun executeCommandAction(action: IntentionAction) {
    if (KotlinPluginModeProvider.isK2Mode()) {
      // In K2, analysis APIs must be used out of write action. This rule is enforced to avoid
      // invalid analysis result.
      CommandProcessor.getInstance()
        .executeCommand(
          myFixture.project,
          { action.invoke(myFixture.project, myFixture.editor, myFixture.file) },
          action.familyName,
          null,
        )
    } else {
      WriteCommandAction.runWriteCommandAction(
        myFixture.project,
        Runnable {
          // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like
          // fist item was selected.
          // In our case wrap in Container will be selected.
          action.invoke(myFixture.project, myFixture.editor, myFixture.file)
        },
      )
    }
  }

  fun testCursorAtAnnotation() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME

      <caret>@Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
      """
        .trimIndent(),
    )

    val action = myFixture.availableIntentions.find { it.familyName == "Create Preview" }
    assertThat(action).isNotNull()

    action?.let { executeCommandAction(it) } ?: error("Action is null")

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME
      import $COMPOSE_UI_TOOLING_PREVIEW_PACKAGE.Preview

      @Preview
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
    """
        .trimIndent()
    )
  }

  fun testSelection() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME
      <caret><selection>
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }

      </selection>
      """
        .trimIndent(),
    )

    var action = myFixture.availableIntentions.find { it.familyName == "Create Preview" }
    assertThat(action).isNotNull()

    action?.let { executeCommandAction(it) } ?: error("Action is null")

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME
      import $COMPOSE_UI_TOOLING_PREVIEW_PACKAGE.Preview

      @Preview
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }


    """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME
      <caret>

      <selection>
      @Composable
      fun NewsStory2() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }

      </selection>
      """
        .trimIndent(),
    )

    action = myFixture.availableIntentions.find { it.familyName == "Create Preview" }
    assertThat(action).isNotNull()

    action?.let { executeCommandAction(it) } ?: error("Action is null")

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import $COMPOSABLE_ANNOTATION_FQ_NAME
      import $COMPOSE_UI_TOOLING_PREVIEW_PACKAGE.Preview


      @Preview
      @Composable
      fun NewsStory2() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }


    """
        .trimIndent()
    )
  }
}
