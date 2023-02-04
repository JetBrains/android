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
package com.android.tools.compose


import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for [ComposeFoldingBuilder].
 */
class ComposeFoldingBuilderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
    myFixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun adjust():Modifier
      companion object : Modifier {
        fun adjust():Modifier {}
      }
    }
    """.trimIndent()
    )
  }

  @Test
  fun test() {
    // We can't use standard [myFixture.testFolding] because we need properly load file to be able resolve references inside
    // [ComposeFoldingBuilder].
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
        val m = Modifier
          .adjust()
          .adjust()
      }
      """.trimIndent()
    )

    val res = myFixture.getFoldingDescription(false)

    assertThat(res).isEqualTo("""
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      @Composable
      fun HomeScreen() <fold text='{...}'>{
        val m = <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
      }</fold>
    """.trimIndent())
  }
}