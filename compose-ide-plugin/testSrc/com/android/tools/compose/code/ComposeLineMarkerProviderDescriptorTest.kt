/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.code

import com.android.tools.compose.COMPOSABLE_FQ_NAMES_ROOT
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposeLineMarkerProviderDescriptorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setUp() {
    myFixture = projectRule.fixture

    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
  }

  @Test
  fun composableFunction_identifierHasMarker() {
    val psiFile = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun MyButton() {}

      @Composable
      fun HomeScreen() {
        MyButton() // invocation
      }
      """.trimIndent()
    )

    var identifier: LeafPsiElement? = null
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(psiFile.virtualFile)
      identifier = myFixture.moveCaret("MyBut|ton() // invocation") as LeafPsiElement
    }

    val lineMarkerInfo = runReadAction { ComposeLineMarkerProviderDescriptor().getLineMarkerInfo(identifier!!) }
    assertThat(lineMarkerInfo).isNotNull()
  }

  @Test
  fun composableFunction_ktFunctionElementHasNoMarker() {
    val psiFile = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun MyButton() {}

      @Composable
      fun HomeScreen() {
        MyButton() // invocation
      }
      """.trimIndent()
    )

    var functionElement: KtNamedFunction? = null
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(psiFile.virtualFile)
      functionElement = myFixture.moveCaret("MyBut|ton() // invocation").parentOfType()!!
    }

    val lineMarkerInfo = runReadAction { ComposeLineMarkerProviderDescriptor().getLineMarkerInfo(functionElement!!) }
    assertThat(lineMarkerInfo).isNull()
  }

  @Test
  fun nonComposableFunction_noMarker() {
    val psiFile = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun MyButton() {}

      fun HomeScreen() {
        MyButton() // invocation
      }
      """.trimIndent()
    )

    var identifier: LeafPsiElement? = null
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(psiFile.virtualFile)
      identifier = myFixture.moveCaret("MyBut|ton() // invocation") as LeafPsiElement
    }

    val lineMarkerInfo = runReadAction { ComposeLineMarkerProviderDescriptor().getLineMarkerInfo(identifier!!) }
    assertThat(lineMarkerInfo).isNull()
  }
}
