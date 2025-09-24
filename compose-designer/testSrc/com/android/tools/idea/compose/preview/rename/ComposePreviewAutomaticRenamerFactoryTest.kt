/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.rename

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposePreviewAutomaticRenamerFactoryTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.inMemory())

  @get:Rule val edtRule = EdtRule()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    fixture.addFileToProject(
      "androidx/compose/runtime/Composable.kt",
      """
      package androidx.compose.runtime
      annotation class Composable
      """
        .trimIndent(),
    )
    fixture.addFileToProject(
      "androidx/compose/ui/tooling/preview/Preview.kt",
      """
      package androidx.compose.ui.tooling.preview
      annotation class Preview
      """
        .trimIndent(),
    )
  }

  @RunsInEdt
  @Test
  fun isApplicableToComposableFunction() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun My<caret>Composable() {}
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    assertTrue(factory.isApplicable(element))
  }

  @RunsInEdt
  @Test
  fun renamesPreviewFunction() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun My<caret>Composable() {}

      @Preview
      @Composable
      fun MyComposablePreview() {
        MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals("MyNewComposablePreview", renamer.getNewName(renamer.elements.first()))
  }

  @RunsInEdt
  @Test
  fun doesNotRenameNonPreviewFunction() = runTest {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun My<caret>Composable() {}

      @Composable
      fun MyComposableNotAPreview() {
        MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = readAction { ComposePreviewAutomaticRenamerFactory() }
    val renamer = readAction { factory.createRenamer(element, "MyNewComposable", mutableListOf()) }
    assertTrue(renamer.elements.isEmpty())
  }

  @RunsInEdt
  @Test
  fun doesNotRenameUnrelatedPreview() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun My<caret>Composable() {}

      @Preview
      @Composable
      fun SomeOtherPreview() {
        MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertTrue(renamer.elements.isEmpty())
  }

  @RunsInEdt
  @Test
  fun composableInsideClass() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      class MyClass {
        @Composable
        fun My<caret>Composable() {}
      }

      @Preview
      @Composable
      fun MyComposablePreview() {
        MyClass().MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals("MyNewComposablePreview", renamer.getNewName(renamer.elements.first()))
  }

  @RunsInEdt
  @Test
  fun previewInDifferentFile() {
    fixture.addFileToProject(
      "Previews.kt",
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview
      @Composable
      fun MyComposablePreview() {
        MyComposable()
      }
      """
        .trimIndent(),
    )
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun My<caret>Composable() {}
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals("MyNewComposablePreview", renamer.getNewName(renamer.elements.first()))
  }

  @RunsInEdt
  @Test
  fun multiplePreviews() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun My<caret>Composable() {}

      @Preview
      @Composable
      fun MyComposablePreview1() {
        MyComposable()
      }

      @Preview
      @Composable
      fun MyComposablePreview2() {
        MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals(2, renamer.elements.size)
    val newNames = renamer.elements.map { renamer.getNewName(it) }.toSet()
    assertEquals(setOf("MyNewComposablePreview1", "MyNewComposablePreview2"), newNames)
  }

  @RunsInEdt
  @Test
  fun alternativeNamingConvention() {
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun My<caret>Composable() {}

      @Preview
      @Composable
      fun PreviewMyComposable() {
        MyComposable()
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals("PreviewMyNewComposable", renamer.getNewName(renamer.elements.first()))
  }

  @RunsInEdt
  @Test
  fun nestedComposable() {
    fixture.addFileToProject(
      "Container.kt",
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun SomeContainer(content: @Composable () -> Unit) {
        content()
      }
      """
        .trimIndent(),
    )
    val file =
      fixture.configureByText(
        "Test.kt",
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun My<caret>Composable() {}

      @Preview
      @Composable
      fun MyComposablePreview() {
        SomeContainer {
          MyComposable()
        }
      }
      """
          .trimIndent(),
      )

    val element = file.findElementAt(fixture.caretOffset)!!.parent as KtNamedFunction
    val factory = ComposePreviewAutomaticRenamerFactory()
    val renamer = factory.createRenamer(element, "MyNewComposable", mutableListOf())
    assertEquals("MyNewComposablePreview", renamer.getNewName(renamer.elements.first()))
  }
}
