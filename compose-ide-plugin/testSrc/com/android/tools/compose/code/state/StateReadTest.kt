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
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.getEnclosing
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.android.utils.associateWithNotNull
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
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
class StateReadTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin().onEdt()

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
        "val stateVar = rememberSaveable { mutableDoubleStateOf(2.71828) }",
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
    createPsiFile("fun Inner(arg: () -> Unit)", "Inner { stateVar.value }")
      .findAllStateReads()
      .let { assertThat(it).isEmpty() }
  }

  @Test
  fun inlineLambdaArgument() {
    createPsiFile("inline fun Inner(arg: () -> Unit)", "Inner { stateVar.value }")
      .assertContainsSingleStateRead {
        expression("|value|") to StateRead.create(expression("{ |stateVar|."), outerFunction())
      }
  }

  @Test
  fun noinlineLambdaArgument() {
    createPsiFile("inline fun Inner(noinline arg: () -> Unit)", "Inner { stateVar.value }")
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

  @Test
  fun create_functionScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        fun foo() {
          val a = 1
        }
        """
          .trimIndent(),
      )

    val foo = psiFile.getEnclosing<KtNamedFunction>("val")
    val a = psiFile.getEnclosing<KtExpression>("val")
    val stateRead = StateRead.create(a, foo)
    assertThat(stateRead).isNotNull()
    checkNotNull(stateRead)
    val scopeBody = psiFile.getEnclosing<KtBlockExpression>("foo() |{" to "\n}|")
    assertThat(stateRead.stateVar).isEqualTo(a)
    assertThat(stateRead.scope).isEqualTo(scopeBody)
    assertThat(stateRead.scopeName).isEqualTo("foo")
  }

  @Test
  fun create_lambdaScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        fun foo(val block: () -> Unit) {}
        fun bar() {
          foo {
            val a = 1
          }
        }
        """
          .trimIndent(),
      )

    val block = psiFile.getEnclosing<KtLambdaExpression>("val a")
    val a = psiFile.getEnclosing<KtExpression>("val a")
    val stateRead = StateRead.create(a, block)
    assertThat(stateRead).isNotNull()
    checkNotNull(stateRead)
    assertThat(stateRead.stateVar).isEqualTo(a)
    assertThat(stateRead.scope).isEqualTo(block)
    assertThat(stateRead.scopeName)
      .isEqualTo(ComposeBundle.message("state.read.recompose.target.enclosing.lambda"))
  }

  @Test
  fun create_getterScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        val foo: Int
          get() {
            val a = "Hi Andy!"
          }
        """
          .trimIndent(),
      )

    val accessor = psiFile.getEnclosing<KtPropertyAccessor>("Hi Andy!")
    val a = psiFile.getEnclosing<KtExpression>("Hi Andy!")
    val stateRead = StateRead.create(a, accessor)
    assertThat(stateRead).isNotNull()
    checkNotNull(stateRead)
    assertThat(stateRead.stateVar).isEqualTo(a)
    assertThat(stateRead.scope).isEqualTo(accessor.bodyExpression)
    assertThat(stateRead.scopeName).isEqualTo("foo.get()")
  }

  @Test
  fun create_setterScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        var foo: Int = 0
          set(newValue) {
            field = "Hi Andy!".length + newValue
          }
        """
          .trimIndent(),
      )

    val accessor = psiFile.getEnclosing<KtPropertyAccessor>("Hi Andy!")
    val a = psiFile.getEnclosing<KtExpression>("Hi Andy!")
    val stateRead = StateRead.create(a, accessor)
    assertThat(stateRead).isNull() // Setter is not a valid scope
  }

  @Test
  fun create_anonymousFunctionScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        fun foo() {
          val a = fun() {
            val b = "It's me again"
          }
        }
        """
          .trimIndent(),
      )

    val anonymousFunction = psiFile.getEnclosing<KtNamedFunction>("It's me again")
    // Make sure we didn't get back the bigger function
    val namedFunction = psiFile.getEnclosing<KtNamedFunction>("foo")
    assertThat(anonymousFunction).isNotEqualTo(namedFunction)
    val a = psiFile.getEnclosing<KtExpression>("val b")
    val stateRead = StateRead.create(a, anonymousFunction)
    assertThat(stateRead).isNotNull()
    checkNotNull(stateRead)
    assertThat(stateRead.stateVar).isEqualTo(a)
    assertThat(stateRead.scope).isEqualTo(anonymousFunction.bodyExpression)
    assertThat(stateRead.scopeName)
      .isEqualTo(ComposeBundle.message("state.read.recompose.target.enclosing.anonymous.function"))
  }

  @Test
  fun create_unnamedScope() {
    val psiFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        fun foo() {
          val a = 1
        }
        """
          .trimIndent(),
      )

    val a = psiFile.getEnclosing<KtExpression>("val a")
    val unnamedNonLambdaExpression = psiFile.getEnclosing<KtExpression>("1")
    val stateRead = StateRead.create(a, unnamedNonLambdaExpression)
    assertThat(stateRead).isNull()
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

  private fun PsiFile.assertContainsSingleStateRead(
    keyValueSupplier: PsiFile.() -> Pair<KtExpression, StateRead?>
  ) {
    val pair = keyValueSupplier()
    assertThat(findAllStateReads()).containsExactly(pair.first, pair.second)
  }

  private fun PsiFile.findAllStateReads(): Map<KtNameReferenceExpression, StateRead> =
    collectDescendantsOfType<KtNameReferenceExpression>()
      .associateWithNotNull(KtNameReferenceExpression::getStateRead)

  private fun PsiFile.lambda(window: String): KtLambdaExpression = getEnclosing(window)

  private fun PsiFile.expression(window: String): KtExpression = getEnclosing(window)

  private fun PsiFile.stateVar(): KtNameReferenceExpression = getEnclosing("= |stateVar|")

  private fun PsiFile.outerFunction(): KtNamedFunction =
    getEnclosing("|fun $OUTER_FUNCTION" to "\n}|")
}
