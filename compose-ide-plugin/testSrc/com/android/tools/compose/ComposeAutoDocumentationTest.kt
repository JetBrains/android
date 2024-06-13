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
package com.android.tools.compose

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposeAutoDocumentationTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Test
  fun noDocumentationForNullElement() {
    val psiElement: PsiElement? = null
    assertThat(psiElement.shouldShowDocumentation()).isFalse()
  }

  @RunsInEdt
  @Test
  fun documentationForComposables() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    val file =
      fixture.addFileToProject(
        "/src/the/hold/steady/Albums.kt",
        // language=kotlin
        """
      package the.hold.steady

      import androidx.compose.runtime.Composable

      @Composable
      fun BoysAndGirlsInAmerica() {}

      @Composable
      fun StayPositive(required: Int, optional: Int = 42) {}

      @Composable
      fun ThePriceOfProgress(optional: Int = 42, children: @Composable() () -> Unit) {}

      fun OpenDoorPolicy() {}
      """
          .trimIndent(),
      )

    fixture.openFileInEditor(file.virtualFile)

    val windows = listOf("BoysAnd|Girls", "Stay|Positive", "Price|Of")

    windows.forEach {
      assertThat(fixture.findParentElement<KtNamedFunction>(it).shouldShowDocumentation()).isTrue()
    }

    assertThat(fixture.findParentElement<KtNamedFunction>("Open|Door").shouldShowDocumentation())
      .isFalse()
  }

  @RunsInEdt
  @Test
  fun documentationForModifierExtensionFunctions() {
    fixture.addFileToProject(
      "/src/androidx/compose/ui/Modifier.kt",
      // language=kotlin
      """
      package androidx.compose.ui
      interface Modifier
      """
        .trimIndent(),
    )
    val file =
      fixture.addFileToProject(
        "/src/metric/Albums.kt",
        // language=kotlin
        """
      package metric
      // For whatever reason, these don't come back qualified in the test, so fully qualify here.
      fun androidx.compose.ui.Modifier.artOfDoubt(): Modifier = this
      fun androidx.compose.ui.Modifier.formentera(): Modifier = this
      fun String.growUpAndBlowAway(): Int = 8675309
      """
          .trimIndent(),
      )
    fixture.openFileInEditor(file.virtualFile)

    val windows = listOf("artOf|Doubt", "formen|tera")

    windows.forEach {
      assertThat(fixture.findParentElement<KtNamedFunction>(it).shouldShowDocumentation()).isTrue()
    }

    assertThat(fixture.findParentElement<KtNamedFunction>("Blow|Away").shouldShowDocumentation())
      .isFalse()
  }

  @RunsInEdt
  @Test
  fun documentationForModifierBlahBlah() {
    val file =
      fixture.addFileToProject(
        "/src/androidx/compose/ui/Modifier.kt",
        // language=kotlin
        """
      package androidx.compose.ui
      interface Modifier {
        fun fantasies() {}
        companion object: Modifier {
          fun synthetica() = 3
          fun pagansInVegas() = 42L
        }
      }
      """
          .trimIndent(),
      )

    fixture.openFileInEditor(file.virtualFile)

    val windows = listOf("synth|etica", "In|Vegas")

    windows.forEach {
      assertThat(fixture.findParentElement<KtNamedFunction>(it).shouldShowDocumentation()).isTrue()
    }
  }
}
