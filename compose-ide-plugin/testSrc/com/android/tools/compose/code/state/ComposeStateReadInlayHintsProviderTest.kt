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
package com.android.tools.compose.code.state

import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.compose.ComposeBundle
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.offsetForWindow
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions

@RunsInEdt
@RunWith(JUnit4::class)
class ComposeStateReadInlayHintsProviderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val provider = ComposeStateReadInlayHintsProvider()
  private val sink: InlayTreeSink = mock()
  private val treeBuilder: PresentationTreeBuilder = mock()

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.stubComposeRuntime()
    fixture.stubKotlinStdlib()
    StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.override(true)
  }

  @Test
  fun createCollector_notKtFile() {
    val javaFile =
      fixture.loadNewFile(
        "com/example/Foo.java",
        // language=java
        """
        package com.example;
        class Foo {}
        """
          .trimIndent()
      )

    assertThat(provider.createCollector(javaFile, fixture.editor)).isNull()
  }

  @Test
  fun createCollector_flagOff() {
    StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.override(false)

    val kotlinFile =
      fixture.loadNewFile(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        class Foo
        """
          .trimIndent()
      )

    assertThat(provider.createCollector(kotlinFile, fixture.editor)).isNull()
  }

  @Test
  fun createCollector() {
    val kotlinFile =
      fixture.loadNewFile(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        class Foo
        """
          .trimIndent()
      )

    assertThat(provider.createCollector(kotlinFile, fixture.editor))
      .isSameAs(ComposeStateReadInlayHintsCollector)
  }

  @Test
  fun collectFromElement_notKtNameReferenceExpression() {
    fixture.loadNewFile(
      "com/example/Foo.java",
      // language=java
      """
      package com.example;
      class Foo {}
      """
        .trimIndent()
    )
    val element = fixture.moveCaret("F|oo")

    ComposeStateReadInlayHintsCollector.collectFromElement(element, sink)

    verifyNoInteractions(sink)
  }

  @Test
  fun collectFromElement_notStateRead() {
    fixture.loadNewFile(
      "com/example/Foo.kt",
      // language=kotlin
      """
      package com.example
      import androidx.compose.runtime.Composable
      const val STR = "Doing a thing"

      @Composable
      fun DoAThing() {
        Text(STR)
      }
      """
        .trimIndent()
    )
    val element = fixture.findParentElement<KtNameReferenceExpression>("(ST|R)")

    ComposeStateReadInlayHintsCollector.collectFromElement(element, sink)

    verifyNoInteractions(sink)
  }

  @Test
  fun collectFromElement_stateRead() {
    fixture.loadNewFile(
      "com/example/Foo.kt",
      // language=kotlin
      """
        package com.example
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.MutableState
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.saveable.rememberSaveable
        @Composable
        fun Bar(arg: String, onNameChange: (String) -> Unit)
        @Composable
        fun Foo {
          var stateVar = rememberSaveable { mutableStateOf("foo") }
          Bar(arg = stateVar.value) { stateVar.value = it }
        }
        """
    )
    val element = fixture.findParentElement<KtNameReferenceExpression>("= stateVar.va|lue)")

    ComposeStateReadInlayHintsCollector.collectFromElement(element, sink)

    val tooltip = ComposeBundle.message("state.read.message", "stateVar", "Foo")
    val positionCaptor: ArgumentCaptor<InlineInlayPosition> = argumentCaptor()
    val builderCaptor: ArgumentCaptor<PresentationTreeBuilder.() -> Unit> = argumentCaptor()
    verify(sink)
      .addPresentation(
        positionCaptor.captureNonNull(),
        payloads = eq(null),
        tooltip = eq(tooltip),
        hasBackground = eq(true),
        builder = builderCaptor.captureNonNull()
      )
    with(positionCaptor.value) {
      assertThat(offset).isEqualTo(fixture.offsetForWindow("stateVar.value|)"))
      assertThat(relatedToPrevious).isTrue()
      assertThat(priority).isEqualTo(0)
    }
    builderCaptor.value.invoke(treeBuilder)
    verify(treeBuilder).text(ComposeBundle.message("state.read"))
    verifyNoMoreInteractions(sink, treeBuilder)
  }
}

fun ArgumentCaptor<InlineInlayPosition>.captureNonNull() =
  capture() ?: InlineInlayPosition(0, false)

fun <T> ArgumentCaptor<T.() -> Unit>.captureNonNull() = capture() ?: {}
