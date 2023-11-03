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

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.android.utils.associateWithNotNull
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
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
class StateReadTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.stubComposeRuntime()
    fixture.stubKotlinStdlib()
  }

  @Test
  fun delegatedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateVar) { stateVar = it }",
        "var stateVar by rememberSaveable { mutableStateOf(\"\") }",
      )
      .assertContainsSingleStateRead {
        val element = stateVar()
        element to StateRead.create(element, outerFunction())
      }
  }

  @Test
  fun assignedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateVar.value) { stateVar.value = it }",
      )
      .assertContainsSingleStateRead {
        expression("|value|)") to StateRead.create(stateVar(), outerFunction())
      }
  }

  @Test
  fun listPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = stateListVar[0].value) { stateListVar[0].value = it }",
        "val stateListVar = listOf(rememberSaveable { mutableStateOf(\"\") })",
      )
      .assertContainsSingleStateRead {
        expression("|value|)") to
          StateRead.create(expression("= |stateListVar[0]|"), outerFunction())
      }
  }

  @Test
  fun nestedPropertyRead() {
    createPsiFile(
        "fun Inner(arg: String, onNameChange: (String) -> Unit)",
        "Inner(arg = container.stateProp.value) { container.stateProp.value = it }",
        "val container = StateHolder(rememberSaveable { mutableStateOf(\"\") })",
        "class StateHolder(val stateProp: MutableState<String>)",
      )
      .assertContainsSingleStateRead {
        expression("|value|)") to
          StateRead.create(expression("= container.|stateProp|"), outerFunction())
      }
  }

  @Test
  fun primitivePropertyRead_int() {
    createPsiFile(
        "fun Inner(arg: Int, (Int) -> Unit)",
        "Inner(arg = stateVar.intValue) { stateVar.intValue = it }",
        "val stateVar = rememberSaveable { mutableIntStateOf(42) }",
      )
      .assertContainsSingleStateRead {
        expression("|intValue|)") to StateRead.create(stateVar(), outerFunction())
      }
  }

  @Test
  fun primitivePropertyRead_long() {
    createPsiFile(
        "fun Inner(arg: Long, (Long) -> Unit)",
        "Inner(arg = stateVar.longValue) { stateVar.longValue = it }",
        "val stateVar = rememberSaveable { mutableLongStateOf(8657309L) }",
      )
      .assertContainsSingleStateRead {
        expression("|longValue|)") to StateRead.create(stateVar(), outerFunction())
      }
  }

  @Test
  fun primitivePropertyRead_float() {
    createPsiFile(
        "fun Inner(arg: Float, (Float) -> Unit)",
        "Inner(arg = stateVar.floatValue) { stateVar.floatValue = it }",
        "val stateVar = rememberSaveable { mutableFloatStateOf(3.14159f) }",
      )
      .assertContainsSingleStateRead {
        expression("|floatValue|)") to StateRead.create(stateVar(), outerFunction())
      }
  }

  @Test
  fun primitivePropertyRead_double() {
    createPsiFile(
        "fun Inner(arg: Double, (Double) -> Unit)",
        "Inner(arg = stateVar.doubleValue) { stateVar.doubleValue = it }",
        "val stateVar = rememberSaveable { mutableDoubleStateOf(2.71828) }"
      )
      .assertContainsSingleStateRead {
        expression("|doubleValue|)") to StateRead.create(stateVar(), outerFunction())
      }
  }

  @Test
  fun composableLambdaArgument() {
    createPsiFile("fun Inner(arg: @Composable () -> Unit)", "Inner { stateVar.value }")
      .assertContainsSingleStateRead {
        expression("|value|") to
          StateRead.create(expression("{ |stateVar|."), lambda("|stateVar.value|"))
      }
  }

  @Test
  fun noncomposableLambdaArgument() {
    createPsiFile(
        "fun Inner(arg: () -> Unit)",
        "Inner { stateVar.value }",
      )
      .findAllStateReads()
      .let { assertThat(it).isEmpty() }
  }

  @Test
  fun inlineLambdaArgument() {
    createPsiFile(
        "inline fun Inner(arg: () -> Unit)",
        "Inner { stateVar.value }",
      )
      .assertContainsSingleStateRead {
        expression("|value|") to StateRead.create(expression("{ |stateVar|."), outerFunction())
      }
  }

  @Test
  fun noinlineLambdaArgument() {
    createPsiFile(
        "inline fun Inner(noinline arg: () -> Unit)",
        "Inner { stateVar.value }",
      )
      .findAllStateReads()
      .let { assertThat(it).isEmpty() }
  }

  @Test
  fun composableNoinlineLambdaArgument() {
    createPsiFile(
        "fun Inner(arg: @Composable () -> Unit, otherArg: Int)",
        "Inner({ stateVar.value }, 3)",
      )
      .assertContainsSingleStateRead {
        expression("|value|") to
          StateRead.create(expression("{ |stateVar|."), lambda("|stateVar.value|"))
      }
  }

  @Test
  fun composablePositionalLambdaArgument() {
    createPsiFile(
        "fun Inner(arg: @Composable () -> Unit, otherArg: Int)",
        "Inner({ stateVar.value }, 3)",
      )
      .assertContainsSingleStateRead {
        expression("|value|") to
          StateRead.create(expression("{ |stateVar|."), lambda("|stateVar.value|"))
      }
  }

  @Test
  fun composableNamedLambdaArgument() {
    createPsiFile(
        "fun Inner(beforeArg: Int, arg: @Composable () -> Unit, afterArg: Int)",
        "Inner(arg = { stateVar.value }, beforeArg = 17, afterArg = 42)",
      )
      .assertContainsSingleStateRead {
        expression("|value|") to
          StateRead.create(expression("{ |stateVar|."), lambda("|stateVar.value|"))
      }
  }

  @Test
  fun inlineLambdaInsideComposableLambda() {
    createPsiFile(
        "fun Inner(arg: @Composable () -> Unit)",
        "Inner { MoreInner { stateVar.value } }",
        extraCode = "inline fun MoreInner(arg: () -> Unit)",
      )
      .assertContainsSingleStateRead {
        expression("|value|") to
          StateRead.create(expression("{ |stateVar|."), lambda("|MoreInner { stateVar.value }|"))
      }
  }

  private fun createPsiFile(
    innerFunctionSignature: String,
    innerFunctionInvocation: String,
    stateVarCreation: String = "val stateVar = rememberSaveable { mutableStateOf(\"\") }",
    extraCode: String = ""
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
        .trimIndent()
    )
  }

  private fun PsiFile.assertContainsSingleStateRead(
    keyValueSupplier: PsiFile.() -> Pair<KtExpression, StateRead?>
  ) {
    val pair = keyValueSupplier()
    assertThat(findAllStateReads()).containsExactly(pair.first, pair.second)
  }

  private fun PsiFile.findAllStateReads(): Map<KtNameReferenceExpression, StateRead> =
    collectDescendantsOfType<KtNameReferenceExpression>()
      .associateWithNotNull(KtNameReferenceExpression::getStateRead)

  private inline fun <reified T> PsiFile.getSmallestEnclosing(window: String): T {
    val windowStart = window.reversed().replaceFirst("|", "").reversed()
    val windowEnd = window.replaceFirst("|", "")
    return getSmallestEnclosing(windowStart to windowEnd)
  }

  private fun PsiFile.lambda(window: String) = getSmallestEnclosing<KtLambdaExpression>(window)

  private fun PsiFile.expression(window: String) = getSmallestEnclosing<KtExpression>(window)

  private fun PsiFile.stateVar(): KtNameReferenceExpression = getSmallestEnclosing("= |stateVar|")

  private fun PsiFile.outerFunction(): KtNamedFunction =
    getSmallestEnclosing("|fun $OUTER_FUNCTION" to "\n}|")

  private inline fun <reified T> PsiFile.getSmallestEnclosing(window: Pair<String, String>): T {
    val startOffset = offsetForWindow(window.first)
    val endOffset = offsetForWindow(window.second, startOffset)
    var candidate = findElementAt(startOffset)
    // Climb up until we find something
    while (
      candidate != null &&
        (candidate !is T || candidate.startOffset > startOffset || candidate.endOffset < endOffset)
    ) {
      candidate = candidate.parent
    }
    assertWithMessage(
        "Did not find an enclosing ${T::class} in $this between ${window.first} and ${window.second}"
      )
      .that(candidate)
      .isNotNull()
    return checkNotNull(candidate as T)
  }

  private fun PsiFile.offsetForWindow(window: String, startIndex: Int = 0): Int {
    val delta = window.indexOf("|")
    require(delta >= 0) { "No '|' character found in window: \"$window\"" }
    val target = window.substring(0, delta) + window.substring(delta + 1)
    val start = text.indexOf(target, startIndex - delta)
    assertWithMessage("Didn't find the string $target in the source of $this")
      .that(start)
      .isAtLeast(0)
    return start + delta
  }
}
