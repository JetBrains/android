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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections

class ComposableFunctionExtractableAnalyserTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
    StudioFlags.COMPOSE_FUNCTION_EXTRACTION.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_FUNCTION_EXTRACTION.clearOverride()
  }

  private val helper = object : ExtractionEngineHelper(EXTRACT_FUNCTION) {
    override fun configureAndRun(project: Project,
                                 editor: Editor,
                                 descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                                 onFinish: (ExtractionResult) -> Unit) {
      val newDescriptor = descriptorWithConflicts.descriptor.copy(suggestedNames = Collections.singletonList("newComposableFunction"))
      doRefactor(ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT), onFinish)
    }
  }

  @RunsInEdt
  @Test
  fun testComposableFunction() {
    myFixture.loadNewFile(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun sourceFunction() {
        <selection>print(true)</selection>
      }
      """.trimIndent()
    )

    ExtractKotlinFunctionHandler(helper = helper).invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)

    myFixture.checkResult(
      //language=kotlin
      """
        package com.example

        import androidx.compose.runtime.Composable

        @Composable
        fun sourceFunction() {
            newComposableFunction()
        }

        @Composable
        private fun newComposableFunction() {
            print(true)
        }
      """.trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun testComposableContext() {
    myFixture.loadNewFile(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget(context: @Composable () -> Unit) {}

      fun setContent() {
        myWidget {
          <selection>print(true)</selection>
        }
      }
      """.trimIndent()
    )

    ExtractKotlinFunctionHandler(helper = helper).invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)

    myFixture.checkResult(
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun myWidget(context: @Composable () -> Unit) {}

      fun setContent() {
        myWidget {
            newComposableFunction()
        }
      }

      @Composable
      private fun newComposableFunction() {
          print(true)
      }
      """.trimIndent()
    )
  }
}