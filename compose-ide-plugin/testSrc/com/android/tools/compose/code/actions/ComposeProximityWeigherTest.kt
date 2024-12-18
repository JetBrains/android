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
package com.android.tools.compose.code.actions

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlin.test.assertNotNull
import org.jetbrains.android.compose.addComposeRuntimeDep
import org.jetbrains.android.compose.addComposeUiDep
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [ComposeProximityWeigher].
 *
 * Unfortunately it's not possible to validate the order of items in the "Add Imports" list end to
 * end in a unit test, because most of the logic involved is internal in the Kotlin plugin. We can
 * execute the correct intention, but in a unit test it will not pop up a dialog, instead just
 * selecting the first item.
 *
 * This file contains some tests that validate that the Weigher is correctly wired up, by validating
 * that the intention results in an import being added that wouldn't have been used if
 * [ComposeProximityWeigher] isn't running. The remaining tests are more traditional unit tests,
 * working directly with [ComposeProximityWeigher] outside the context of the intention.
 */
@RunWith(JUnit4::class)
class ComposeProximityWeigherTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val project by lazy { fixture.project }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.addComposeRuntimeDep()
    fixture.addComposeUiDep()
  }

  @Test
  fun composeModifierPromotedOverJavaModifier() {
    // Regression test for b/355257785.
    // The java Modifier class should exist because this test runs with an SDK, and the Compose
    // Modifier interface should exist because we've added Compose dependencies.
    // Due to the specific logic in the platform, it's important to validate the ordering is correct
    // when both of these object are coming from libraries, not from the source (as would be the
    // "normal" case when adding files to a project via the test fixture).

    // First ensure that both types actually exist and can resolve in the editor.
    fixture.loadNewFile(
      "src/com/example/Resolve.kt",
      // language=kotlin
      """
      package com.example
      fun foo(
        m1: androidx.compose.ui.Modifier,
        m2: java.lang.reflect.Modifier,
      ) {}
      """
        .trimIndent(),
    )
    invokeAndWaitIfNeeded { fixture.checkHighlighting(false, false, false) }

    // Now that we know both types exist and can be referenced, check that the Compose Modifier is
    // preferred for import.
    val psiFile =
      fixture.loadNewFile(
        "src/com/example/Test.kt",
        // language=kotlin
        """
        package com.example

        import androidx.compose.runtime.Composable

        @Composable
        fun HomeScreen(modifier: Mod${caret}ifier) {}
        """
          .trimIndent(),
      )

    val action = assertNotNull(fixture.getIntentionAction("Import class 'Modifier'"))
    invokeAndWaitIfNeeded { action.invoke(project, fixture.editor, psiFile) }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen(modifier: Mod${caret}ifier) {}
      """
        .trimIndent()
    )
  }

  @Test
  fun validateWeigherIsBeforeJavaInheritance() {
    // IntelliJ's com.intellij.psi.util.proximity.JavaInheritanceWeigher is promoting Java classes
    // above everything else in the import list,
    // presumably by accident. We can avoid that behavior for @Composable functions by ensuring our
    // weigher runs before that one. This test
    // validates that scenario, by including a Java class in the potential import list and ensuring
    // it's below a promoted class.
    fixture.addFileToProject(
      "src/android/graphics/Color.java",
      // language=java
      """
      package android.graphics;

      public class Color {}
      """,
    )

    fixture.addFileToProject(
      "src/androidx/compose/ui/graphics/Color.kt",
      // language=kotlin
      """
      package androidx.compose.ui.graphics

      value class Color
      """,
    )

    fixture.addFileToProject(
      "src/androidx/compose/material/Surface.kt",
      // language=kotlin
      """
      package androidx.compose.material

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.graphics.Color

      @Composable
      fun Surface(color: Color) {}
      """,
    )

    val psiFile =
      fixture.loadNewFile(
        "src/com/example/Test.kt",
        // language=kotlin
        """
      package com.example

      import androidx.compose.material.Surface
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Surface(color = Co<caret>lor.White) {
        }
      }
      """
          .trimIndent(),
      )

    val action = assertNotNull(fixture.getIntentionAction("Import class 'Color'"))
    invokeAndWaitIfNeeded { action.invoke(project, fixture.editor, psiFile) }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.material.Surface
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.graphics.Color

      @Composable
      fun HomeScreen() {
        Surface(color = Co<caret>lor.White) {
        }
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun validateBasicOrdering() {
    val composableFunction =
      addFileAndFindElement(
        "src/com/example/composable/ComposableFunction.kt",
        // language=kotlin
        """
      package com.example.composable

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction()
      """
          .trimIndent(),
        "ComposableFunction",
        KtNamedFunction::class.java,
      )

    val deprecatedComposableFunction =
      addFileAndFindElement(
        "src/com/example/composable/DeprecatedComposableFunction.kt",
        // language=kotlin
        """
      package com.example.composable

      import androidx.compose.runtime.Composable

      @Composable
      @Deprecated
      fun DeprecatedComposableFunction()
      """
          .trimIndent(),
        "ComposableFunction",
        KtNamedFunction::class.java,
      )

    val nonComposableFunction =
      addFileAndFindElement(
        "src/com/example/noncomposable/NonComposableFunction.kt",
        // language=kotlin
        """
      package com.example.noncomposable

      fun NonComposableFunction()
      """
          .trimIndent(),
        "NonComposableFunction",
        KtNamedFunction::class.java,
      )

    val manuallyWeightedElement =
      addFileAndFindElement(
        "src/androidx/compose/ui/Modifier.kt",
        // language=kotlin
        """
      package androidx.compose.ui

      object Modifier
      """
          .trimIndent(),
        "Modifier",
        KtObjectDeclaration::class.java,
      )

    val locationFile =
      fixture.loadNewFile(
        "src/com/example/Test.kt",
        // language=kotlin
        """
      package com.example
      """
          .trimIndent(),
      )

    val proximityLocation = ProximityLocation(locationFile, fixture.module)
    val sortedList = runReadAction {
      listOf(
          nonComposableFunction,
          deprecatedComposableFunction,
          composableFunction,
          manuallyWeightedElement,
        )
        .sortedByDescending { element ->
          ComposeProximityWeigher().weigh(element, proximityLocation)
        }
    }

    assertThat(sortedList)
      .containsExactly(
        manuallyWeightedElement,
        composableFunction,
        nonComposableFunction,
        deprecatedComposableFunction,
      )
      .inOrder()
  }

  fun <T : PsiElement> addFileAndFindElement(
    relativePath: String,
    fileText: String,
    targetElementText: String,
    targetElementClass: Class<T>,
  ): T {
    val psiFile = fixture.addFileToProject(relativePath, fileText)
    return runReadAction {
      PsiTreeUtil.getParentOfType(
        psiFile.findElementAt(psiFile.text.indexOf(targetElementText)),
        targetElementClass,
      )!!
    }
  }
}
