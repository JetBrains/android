/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.kotlin.android.intention

import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation

class ComposeSurroundWithWidgetTest : JavaCodeInsightFixtureTestCase() {
  public override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
  }

  public override fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testSurroundWithWidget() {
    myFixture.addFileToProject(
      "src/androidx/ui/layout/Container.kt",
      // language=kotlin
      """
    package androidx.ui.layout

    class Container
    """.trimIndent()
    )

    val file = myFixture.addFileToProject(
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
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val action = myFixture.availableIntentions.find { it.text == ComposeSurroundWithWidget().text }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example
      
      import androidx.compose.Composable
      import androidx.ui.layout.Container

      @Composable
      fun NewsStory() {
          Container {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent()
    )
  }
}