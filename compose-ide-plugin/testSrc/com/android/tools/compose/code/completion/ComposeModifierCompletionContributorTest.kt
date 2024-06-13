/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose.code.completion

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.compose.COMPOSE_UI_PACKAGE
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/** Test for [ComposeModifierCompletionContributor]. */
@RunWith(JUnit4::class)
class ComposeModifierCompletionContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation()
    myFixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun function():Unit
      companion object : Modifier {
        fun function() {}
      }
    }

    fun Modifier.extensionFunction():Modifier { return this }
    fun Modifier.extensionFunctionReturnsNonModifier():Int { return 1 }
    """
        .trimIndent(),
    )

    myFixture.addFileToProject(
      "src/com/example/myWidgetWithModifier.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidgetWithModifier(modifier: Modifier) {}
    """
        .trimIndent(),
    )
  }

  @Test
  fun prioritizeExtensionFunctionForMethodCalledOnModifier() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
        val m = Modifier.$caret
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction"))
      .isLessThan(lookupStrings.indexOf("function"))
    assertThat(lookupStrings.indexOf("extensionFunction"))
      .isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))
    assertThat(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))
      .isLessThan(lookupStrings.indexOf("function"))

    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun HomeScreen2() {
        val m = Modifier.extensionFunction().$caret
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction"))
      .isLessThan(lookupStrings.indexOf("function"))

    myFixture.loadNewFile(
      "src/com/example/Test3.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen3(modifier: Modifier = Modifier) {
        modifier.$caret
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction"))
      .isLessThan(lookupStrings.indexOf("function"))

    myFixture.loadNewFile(
      "src/com/example/Test4.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen4() {
        Modifier.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction"))
      .isLessThan(lookupStrings.indexOf("function"))
  }

  @RunsInEdt
  @Test
  fun modifierAsArgument() {
    fun checkArgumentCompletion() {
      myFixture.lookup.currentItem = myFixture.lookupElements?.find { it.lookupString.contains("extensionFunction") }
      myFixture.lookup.currentItem =
        myFixture.lookupElements!!.find { it.lookupString.contains("extensionFunction") }
      myFixture.finishLookup('\n')
      myFixture.checkResult(
        """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidget() {
          myWidgetWithModifier(Modifier.extensionFunction()
      }
      """
          .trimIndent()
      )
    }

    fun checkNamedArgumentCompletion() {
      myFixture.lookup.currentItem =
        myFixture.lookupElements!!.find { it.lookupString.contains("extensionFunction") }
      myFixture.finishLookup('\n')
      myFixture.checkResult(
        """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidget() {
          myWidgetWithModifier(modifier = Modifier.extensionFunction()
      }
      """
          .trimIndent()
      )
    }

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidget() {
          myWidgetWithModifier(Modifier.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings).contains("extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("extensionFunction")).isEqualTo(0)

    checkArgumentCompletion()

    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget() {
          myWidgetWithModifier(<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("Modifier.extensionFunction")
    assertThat(lookupStrings).doesNotContain("Modifier.extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("Modifier.extensionFunction")).isEqualTo(0)

    // to check that we still suggest "Modifier.extensionFunction" when prefix doesn't much with
    // function name and only with "Modifier".
    // See [ComposeModifierCompletionContributor.ModifierLookupElement.getAllLookupStrings]
    myFixture.type("M")

    checkArgumentCompletion()

    myFixture.loadNewFile(
      "src/com/example/Test3.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget() {
          myWidgetWithModifier(modifier = <caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("Modifier.extensionFunction")
    assertThat(lookupStrings).doesNotContain("Modifier.extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("Modifier.extensionFunction")).isEqualTo(0)

    checkNamedArgumentCompletion()

    myFixture.loadNewFile(
      "src/com/example/Test4.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidget() {
          myWidgetWithModifier(modifier = Modifier.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings).contains("extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("extensionFunction")).isEqualTo(0)

    checkNamedArgumentCompletion()
  }

  @RunsInEdt
  @Test
  fun modifierImportAlias() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier as Modifier1

      @Composable
      fun myWidget() {
          myWidgetWithModifier(Modifier1.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings).contains("extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("extensionFunction")).isEqualTo(0)

    myFixture.lookup.currentItem =
      myFixture.lookupElements!!.find { it.lookupString.contains("extensionFunction") }
    myFixture.finishLookup('\n')
    // TODO(302558638): Fix this redundant import issue for K1.
    val redundantImport = if (!isK2Mode()) "import androidx.compose.ui.Modifier\n      " else ""
    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      ${redundantImport}import androidx.compose.ui.extensionFunction
      import androidx.compose.ui.Modifier as Modifier1

      @Composable
      fun myWidget() {
          myWidgetWithModifier(Modifier1.extensionFunction()
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun modifierAsProperty() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidget() {
          val myModifier:Modifier = <caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("Modifier.extensionFunction")
    // If user didn't type Modifier don't suggest extensions that doesn't return Modifier.
    assertThat(lookupStrings).doesNotContain("Modifier.extensionFunctionReturnsNonModifier")

    myFixture.type("extensionFunction\t")

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidget() {
          val myModifier:Modifier = Modifier.extensionFunction()
      }
      """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidget() {
          val myModifier:Modifier = Modifier.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings).contains("extensionFunctionReturnsNonModifier")

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidget() {
          val myModifier:Modifier = Modifier.extensionFunction().<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings).contains("extensionFunctionReturnsNonModifier")
    assertThat(lookupStrings.indexOf("extensionFunction")).isEqualTo(0)
  }

  @RunsInEdt
  @Test
  fun modifierImportAliasForProperty() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier as Modifier1

      @Composable
      fun myWidget() {
          val myModifier:Modifier1 = <caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("Modifier.extensionFunction")
    // If user didn't type Modifier don't suggest extensions that doesn't return Modifier.
    assertThat(lookupStrings).doesNotContain("Modifier.extensionFunctionReturnsNonModifier")

    myFixture.type("extensionFunction\t")

    // K1 imports `Modifier` for `Modifier.extensionFunction()` below, but we already have
    // `Modifier1`, so we don't actually need it. K2 fixes this issue.
    val imports =
      if (isK2Mode()) {
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.extensionFunction
      import androidx.compose.ui.Modifier as Modifier1
      """
      } else {
        """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction
      import androidx.compose.ui.Modifier as Modifier1
      """
      }

    myFixture.checkResult(
      """
      package com.example
      $imports
      @Composable
      fun myWidget() {
          val myModifier:Modifier1 = ${if (isK2Mode()) "Modifier1" else "Modifier"}.extensionFunction()
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun modifierIsNotImported() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget() {
          myWidgetWithModifier(modifier = Modifier.<caret>
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).contains("extensionFunction")
    assertThat(lookupStrings.indexOf("extensionFunction")).isEqualTo(0)

    myFixture.lookup.currentItem =
      myFixture.lookupElements!!.find { it.lookupString == "extensionFunction" }
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidget() {
          myWidgetWithModifier(modifier = Modifier.extensionFunction()
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun newExtensionFunction() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      fun Modifier.foo() = extensionFunction().<caret>

      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings)
      .containsAllOf("extensionFunction", "extensionFunctionReturnsNonModifier")
      .inOrder()
  }

  /** Regression test for b/279049842 */
  @Test
  fun invisibleObjectExtensionMethods() {
    val contributor = ComposeModifierCompletionContributor()
    val mockResultSet: CompletionResultSet = mock()

    // This is a super-contrived example to try to regression test b/279049842. I haven't been able
    // to reproduce the real scenario, which
    // involves internal items coming from other libs/modules and relies on Kotlin plugin bugs which
    // (hopefully) would get fixed at some
    // some point.

    ApplicationManager.getApplication().invokeAndWait {
      myFixture.loadNewFile(
        "src/com/example/Test.kt",
        // language=kotlin
        """
        package com.example

        import androidx.compose.ui.Modifier

        object ParentObject {
          object VisibleChild {
            fun Modifier.visibleChildFunction(): Modifier = this
          }

          // Contrived for testing. The private wrapping object makes this not visible to the function below, and the
          // internal wrapping object is needed to pass checks in [ComposeModifierCompletionContributor].
          private object PrivateChild {
            internal object NestedChild {
              fun Modifier.notVisibleChildFunction(): Modifier = this
            }
          }
        }

        fun functionNeedingModifier(modifier: Modifier) {}

        fun doSomething() {
            functionNeedingModifier(modifier = MyModifier.)
        }
        """
          .trimIndent(),
      )
    }

    runReadAction {
      val functionCompletionCall: KtCallExpression =
        myFixture.findParentElement("functionNeeding|Modifier(modifier = MyModifier")

      val visibleChildFunctionCompletion =
        mockCompletionResult(
          myFixture.findParentElement<KtFunction>("Modifier.visibleChild|Function():")
        )
      contributor.consumerCompletionResultFromRemainingContributor(
        visibleChildFunctionCompletion,
        emptySet(),
        functionCompletionCall,
        mockResultSet,
      )
      verify(mockResultSet).passResult(visibleChildFunctionCompletion)

      val notVisibleChildFunctionCompletion =
        mockCompletionResult(
          myFixture.findParentElement<KtFunction>("Modifier.notVisibleChild|Function():")
        )
      contributor.consumerCompletionResultFromRemainingContributor(
        notVisibleChildFunctionCompletion,
        emptySet(),
        functionCompletionCall,
        mockResultSet,
      )
      verify(mockResultSet, never()).passResult(notVisibleChildFunctionCompletion)
    }
  }

  private fun mockCompletionResult(psiElement: PsiElement): CompletionResult {
    val completionResult: CompletionResult = mock()
    val lookupElement: LookupElement = mock()

    whenever(completionResult.lookupElement).thenReturn(lookupElement)
    whenever(lookupElement.psiElement).thenReturn(psiElement)

    return completionResult
  }
}
