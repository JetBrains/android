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
package com.android.tools.idea.templates.live

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.loadNewFile
import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import java.util.HashSet

class AndroidComposeTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    myFixture.addFileToProject(
      "src/androidx/compose/foundation/layout/ColumnAndRow.kt",
      // language=kotlin
      """
    package androidx.compose.foundation.layout

    class Row
    class Column
    """.trimIndent()
    )
  }

  override fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testRowTemplate() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")</selection><caret>
      }
      """.trimIndent()
    )

    val template = TemplateSettings.getInstance().getTemplate("WR", "AndroidCompose")
    InvokeTemplateAction(template, myFixture.editor, project, HashSet()).perform()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Row

      @Composable
      fun NewsStory() {
          Row {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
      """.trimIndent()
    )
  }

  fun testColumnTemplate() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")</selection><caret>
      }
      """.trimIndent()
    )

    val template = TemplateSettings.getInstance().getTemplate("WC", "AndroidCompose")
    InvokeTemplateAction(template, myFixture.editor, project, HashSet()).perform()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Column

      @Composable
      fun NewsStory() {
          Column {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
      """.trimIndent()
    )
  }
}