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

import com.android.tools.compose.ComposeFqNames
import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for [ComposeModifierCompletionContributor].
 */
class ComposeModifierCompletionContributorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  private val project: Project by lazy { myFixture.project }

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(ComposeFqNames.root)
    myFixture.addFileToProject(
      "src/${ComposeLibraryNamespace.ANDROIDX_COMPOSE.packageName.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package ${ComposeLibraryNamespace.ANDROIDX_COMPOSE.packageName}

    interface Modifier {
      fun function():Unit
      companion object : Modifier {
        fun function() {}
      }
    }

    fun Modifier.extensionFunction():Modifier { return this }
    fun Modifier.extensionFunctionReturnsNonModifier():Int { return 1 }
    """.trimIndent()
    )
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
  }

  @Test
  fun testPrioritizeExtensionFunctionForMethodCalledOnModifier() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
        val m = Modifier.$caret
      }
      """.trimIndent())

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("function"))
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))
    assertThat(lookupStrings.indexOf("extensionFunctionReturnsNonModifier")).isLessThan(lookupStrings.indexOf("function"))

    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      """
      package com.example

      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun HomeScreen2() {
        val m = Modifier.extensionFunction().$caret
      }
      """.trimIndent())

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("function"))

    myFixture.loadNewFile(
      "src/com/example/Test3.kt",
      """
      package com.example

      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun HomeScreen3(modifier: Modifier = Modifier) {
        modifier.$caret
      }
      """.trimIndent())

    myFixture.completeBasic()

    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("function"))
  }

  @Test
  fun testModifierAsArgument() {
    myFixture.addFileToProject(
      "src/com/example/myWidgetWithModifier.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidgetWithModifier(modifier: Modifier) {}
    """.trimIndent())

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget() {
          myWidgetWithModifier(<caret>
      }
      """.trimIndent()
    )

    myFixture.completeBasic()

    var lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))

    myFixture.type("extensionFunction\t")

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
      """.trimIndent()
    )


    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun myWidge2() {
          myWidgetWithModifier(modifier = <caret>
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))

    myFixture.type('e')
    myFixture.completeBasic()
    lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))

    myFixture.type("xtensionFunction\t")

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.extensionFunction

      @Composable
      fun myWidge2() {
          myWidgetWithModifier(modifier = Modifier.extensionFunction()
      }
      """.trimIndent()
    )
  }

  @Test
  fun testModifierAsProperty() {

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
      """.trimIndent()
    )

    myFixture.completeBasic()

    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.indexOf("extensionFunction")).isLessThan(lookupStrings.indexOf("extensionFunctionReturnsNonModifier"))

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
      """.trimIndent()
    )
  }
}