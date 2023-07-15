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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.offsetForWindow
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import icons.StudioIcons
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

val EXPECTED_ICON = StudioIcons.Common.INFO

@RunWith(JUnit4::class)
@RunsInEdt
class ComposeStateReadAnnotatorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val annotator = ComposeStateReadAnnotator()

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.stubComposeRuntime()
    fixture.stubKotlinStdlib()
    StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.override(true)
  }

  @Test
  fun delegatedPropertyRead() {
    val psiFile = fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.getValue
      import androidx.compose.runtime.mutableStateOf
      import androidx.compose.runtime.saveable.rememberSaveable
      import androidx.compose.runtime.setValue

      @Composable
      fun HelloScreen() {
        var name by rememberSaveable { mutableStateOf("") }
        HelloContent(name = name) { name = it }
      }

      @Composable
      fun HelloContent(name: String, onNameChange: (String) -> Unit) { }
      """.trimIndent()
    )

    val allElements = psiFile.collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(1);
    with(annotations.first()) {
      assertThat(message).isEqualTo(createMessage("name", "HelloScreen"))
      assertThat(message).isEqualTo(message)
      assertThat(gutterIconRenderer).isNotNull()
      assertThat(gutterIconRenderer!!.icon).isEqualTo(EXPECTED_ICON)
      assertThat(gutterIconRenderer!!.tooltipText).isEqualTo(message)
      assertThat(textAttributes).isEqualTo(ComposeStateReadAnnotator.COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
      assertThat(startOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = |name)"))
      assertThat(endOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = name|)"))
    }
  }

  @Test
  fun assignedPropertyRead() {
    val psiFile = fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.mutableStateOf
      import androidx.compose.runtime.saveable.rememberSaveable

      @Composable
      fun HelloScreen() {
        val assignedName = rememberSaveable { mutableStateOf("") }
        HelloContent(name = assignedName.value) { assignedName.value = it }
      }

      @Composable
      fun HelloContent(name: String, onNameChange: (String) -> Unit) { }
      """.trimIndent()
    )

    val allElements = psiFile.collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(1);
    with(annotations.first()) {
      assertThat(message).isEqualTo(createMessage("assignedName", "HelloScreen"))
      assertThat(message).isEqualTo(message)
      assertThat(gutterIconRenderer).isNotNull()
      assertThat(gutterIconRenderer!!.icon).isEqualTo(EXPECTED_ICON)
      assertThat(gutterIconRenderer!!.tooltipText).isEqualTo(message)
      assertThat(textAttributes).isEqualTo(ComposeStateReadAnnotator.COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
      assertThat(startOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = assignedName.|value)"))
      assertThat(endOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = assignedName.value|)"))
    }
  }
  @Test
  fun listPropertyRead() {
    val psiFile = fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.mutableStateOf
      import androidx.compose.runtime.saveable.rememberSaveable

      @Composable
      fun HelloScreen() {
        val listName = listOf(rememberSaveable { mutableStateOf("") })
        HelloContent(name = listName[0].value) { listName[0].value = it }
      }

      @Composable
      fun HelloContent(name: String, onNameChange: (String) -> Unit) { }
      """.trimIndent()
    )

    val allElements = psiFile.collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(1);
    with(annotations.first()) {
      assertThat(message).isEqualTo(createMessage("listName[0]", "HelloScreen"))
      assertThat(gutterIconRenderer).isNotNull()
      assertThat(gutterIconRenderer!!.icon).isEqualTo(EXPECTED_ICON)
      assertThat(gutterIconRenderer!!.tooltipText).isEqualTo(message)
      assertThat(textAttributes).isEqualTo(ComposeStateReadAnnotator.COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
      assertThat(startOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = listName[0].|value)"))
      assertThat(endOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = listName[0].value|)"))
    }
  }

  @Test
  fun nestedPropertyRead() {
    val psiFile = fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.MutableState
      import androidx.compose.runtime.mutableStateOf
      import androidx.compose.runtime.saveable.rememberSaveable

      @Composable
      fun HelloScreen() {
        val container = StateHolder(rememberSaveable { mutableStateOf("") })
        HelloContent(name = container.name.value) { container.name.value = it }
      }

      @Composable
      fun HelloContent(name: String, onNameChange: (String) -> Unit) { }

      class StateHolder(val name: MutableState<String>)
      """.trimIndent()
    )

    val allElements = psiFile.collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(1);
    with(annotations.first()) {
      assertThat(message).isEqualTo(createMessage("name", "HelloScreen"))
      assertThat(gutterIconRenderer).isNotNull()
      assertThat(gutterIconRenderer!!.icon).isEqualTo(EXPECTED_ICON)
      assertThat(gutterIconRenderer!!.tooltipText).isEqualTo(message)
      assertThat(textAttributes).isEqualTo(ComposeStateReadAnnotator.COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
      assertThat(startOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = container.name.|value)"))
      assertThat(endOffset).isEqualTo(fixture.offsetForWindow("HelloContent(name = container.name.value|)"))
    }
  }

  private fun createMessage(stateVariable: String, composable: String) =
    "State read: when the value of \"$stateVariable\" changes, \"$composable\" will recompose."
}
