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

import com.android.tools.compose.ComposeBundle
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.offsetForWindow
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val OUTER_FUNCTION = "Outer"

private val COMPOSE_RUNTIME_IMPORTS =
  listOf(
    "Composable",
    "MutableState",
    "getValue",
    "mutableDoubleStateOf",
    "mutableFloatStateOf",
    "mutableIntStateOf",
    "mutableLongStateOf",
    "mutableStateOf",
    "saveable.rememberSaveable",
    "setValue",
  )

@RunWith(JUnit4::class)
@RunsInEdt
class ComposeStateReadAnnotatorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin().onEdt()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val annotator = ComposeStateReadAnnotator()

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.stubComposeRuntime()
    fixture.stubKotlinStdlib()
    StudioFlags.COMPOSE_STATE_READ_INLAY_HINTS_ENABLED.override(true)
  }

  @Test
  fun delegatedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateVar) { stateVar = it }",
        "var stateVar by rememberSaveable { mutableStateOf(\"\") }",
      )
      .assertSingleHighlight("|stateVar|)", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun assignedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateVar.value) { stateVar.value = it }",
      )
      .assertSingleHighlight("|value|)", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun listPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateListVar[0].value) { stateListVar[0].value = it }",
        "val stateListVar = listOf(rememberSaveable { mutableStateOf(\"\") })",
      )
      .assertSingleHighlight(
        "|value|)",
        stateVariable = "stateListVar[0]",
        composeScope = OUTER_FUNCTION,
      )
  }

  @Test
  fun nestedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = container.stateProp.value) { container.stateProp.value = it }",
        "val container = StateHolder(rememberSaveable { mutableStateOf(\"\") })",
        "class StateHolder(val stateProp: MutableState<String>)",
      )
      .assertSingleHighlight("|value|)", stateVariable = "stateProp", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun primitivePropertyRead_int() {
    createPsiFile(
        "fun Inner(arg: Int, (Int) -> Unit)",
        "Inner(arg = stateVar.intValue) { stateVar.intValue = it }",
        "val stateVar = rememberSaveable { mutableIntStateOf(42) }",
      )
      .assertSingleHighlight("|intValue|", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun primitivePropertyRead_long() {
    createPsiFile(
        "fun Inner(arg: Long, (Long) -> Unit)",
        "Inner(arg = stateVar.longValue) { stateVar.longValue = it }",
        "val stateVar = rememberSaveable { mutableLongStateOf(8657309L) }",
      )
      .assertSingleHighlight("|longValue|", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun primitivePropertyRead_float() {
    createPsiFile(
        "fun Inner(arg: Float, (Float) -> Unit)",
        "Inner(arg = stateVar.floatValue) { stateVar.floatValue = it }",
        "val stateVar = rememberSaveable { mutableFloatStateOf(3.14159f) }",
      )
      .assertSingleHighlight("|floatValue|", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun primitivePropertyRead_double() {
    createPsiFile(
        "fun Inner(arg: Double, (Double) -> Unit)",
        "Inner(arg = stateVar.doubleValue) { stateVar.doubleValue = it }",
        "val stateVar = rememberSaveable { mutableDoubleStateOf(2.71828) }",
      )
      .assertSingleHighlight("|doubleValue|", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun composableLambdaArgument() {
    createPsiFile("fun Inner(arg: @Composable () -> Unit)", "Inner { stateVar.value }")
      .assertSingleHighlight("|value|")
  }

  @Test
  fun noncomposableLambdaArgument() {
    createPsiFile("fun Inner(arg: () -> Unit)", "Inner { stateVar.value }").assertNoHighlight()
  }

  @Test
  fun inlineLambdaArgument() {
    createPsiFile("inline fun Inner(arg: () -> Unit)", "Inner { stateVar.value }")
      .assertSingleHighlight("|value|", composeScope = OUTER_FUNCTION)
  }

  @Test
  fun noinlineLambdaArgument() {
    createPsiFile("inline fun Inner(noinline arg: () -> Unit)", "Inner { stateVar.value }")
      .assertNoHighlight()
  }

  @Test
  fun composableNoinlineLambdaArgument() {
    createPsiFile(
        "fun Inner(arg: @Composable () -> Unit, otherArg: Int)",
        "Inner({ stateVar.value }, 3)",
      )
      .assertSingleHighlight("|value|")
  }

  @Test
  fun composablePositionalLambdaArgument() {
    createPsiFile(
        "fun Inner(arg: @Composable () -> Unit, otherArg: Int)",
        "Inner({ stateVar.value }, 3)",
      )
      .assertSingleHighlight("|value|")
  }

  @Test
  fun composableNamedLambdaArgument() {
    createPsiFile(
        "fun Inner(beforeArg: Int, arg: @Composable () -> Unit, afterArg: Int)",
        "Inner(arg = { stateVar.value }, beforeArg = 17, afterArg = 42)",
      )
      .assertSingleHighlight("|value|")
  }

  private fun createPsiFile(
    innerFunctionSignature: String,
    innerFunctionInvocation: String,
    stateVarCreation: String = "val stateVar = rememberSaveable { mutableStateOf(\"\") }",
    extraCode: String = "",
  ): PsiFile {
    return fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example
      ${COMPOSE_RUNTIME_IMPORTS.joinToString("\n      ") { "import androidx.compose.runtime.$it" }}
      @Composable $innerFunctionSignature {}
      @Composable
      fun $OUTER_FUNCTION {
        $stateVarCreation
        $innerFunctionInvocation
      }
      $extraCode
      """
        .trimIndent(),
    )
  }

  private fun PsiFile.assertNoHighlight() {
    val allElements = collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(0)
  }

  private fun PsiFile.assertSingleHighlight(
    window: String,
    stateVariable: String = "stateVar",
    composeScope: String = ComposeBundle.message("state.read.recompose.target.enclosing.lambda"),
  ) {
    val allElements = collectDescendantsOfType<PsiElement>()
    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *allElements.toTypedArray())

    assertThat(annotations).hasSize(1)

    val windowStart = window.reversed().replaceFirst("|", "").reversed()
    val windowEnd = window.replaceFirst("|", "")

    with(annotations.single()) {
      val expectedMessage =
        ComposeBundle.message("state.read.message.titled", stateVariable, composeScope)
      assertThat(message).isEqualTo(expectedMessage)
      assertThat(gutterIconRenderer).isNull()
      assertThat(textAttributes).isEqualTo(COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
      assertThat(startOffset).isEqualTo(fixture.offsetForWindow(windowStart))
      assertThat(endOffset).isEqualTo(fixture.offsetForWindow(windowEnd))
      assertThat(quickFixes?.map { it.quickFix })
        .containsExactly(EnableComposeStateReadInlayHintsAction)
    }
  }
}
