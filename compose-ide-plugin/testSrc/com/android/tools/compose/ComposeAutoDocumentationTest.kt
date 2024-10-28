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
import com.android.tools.idea.testing.getEnclosing
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.application
import org.jetbrains.android.compose.addComposeRuntimeDep
import org.jetbrains.android.compose.addComposeUiDep
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposeAutoDocumentationTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture by lazy { projectRule.fixture }

  private val project by lazy { projectRule.project }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.addComposeRuntimeDep()
    fixture.addComposeUiDep()
  }

  @Test
  fun noDocumentationForNullElement() {
    val psiElement: PsiElement? = null
    assertThat(psiElement.shouldShowDocumentation()).isFalse()
  }

  @Test
  fun documentationForComposables() {
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

    application.invokeAndWait { fixture.openFileInEditor(file.virtualFile) }

    val windows = listOf("BoysAnd|Girls", "Stay|Positive", "Price|Of")

    windows.forEach {
      assertThat(
          runReadAction { fixture.getEnclosing<KtNamedFunction>(it).shouldShowDocumentation() }
        )
        .isTrue()
    }

    assertThat(
        runReadAction {
          fixture.getEnclosing<KtNamedFunction>("Open|Door").shouldShowDocumentation()
        }
      )
      .isFalse()
  }

  @Test
  fun documentationForModifierExtensionFunctions() {
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
    application.invokeAndWait { fixture.openFileInEditor(file.virtualFile) }

    val windows = listOf("artOf|Doubt", "formen|tera")

    windows.forEach {
      assertThat(
          runReadAction { fixture.getEnclosing<KtNamedFunction>(it).shouldShowDocumentation() }
        )
        .isTrue()
    }

    assertThat(
        runReadAction {
          fixture.getEnclosing<KtNamedFunction>("Blow|Away").shouldShowDocumentation()
        }
      )
      .isFalse()
  }

  @Test
  fun documentationForModifierBlahBlah() {
    runReadAction {
      val modifierClass =
        JavaPsiFacade.getInstance(project)
          .findClass("androidx.compose.ui.Modifier", GlobalSearchScope.everythingScope(project))
      requireNotNull(modifierClass) { "Must be able to find Modifier definition." }

      assertThat(modifierClass.methods).isNotEmpty()
      for (method in modifierClass.methods) {
        val navigationElement = method.navigationElement
        assertThat(navigationElement).isInstanceOf(KtNamedFunction::class.java)
        assertThat(navigationElement.shouldShowDocumentation()).isTrue()
      }
    }
  }
}
