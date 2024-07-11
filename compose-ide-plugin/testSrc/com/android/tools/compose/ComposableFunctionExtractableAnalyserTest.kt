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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.application
import java.util.Collections
import java.util.concurrent.Semaphore
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.KotlinIntroduceConstantHandler
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposableFunctionExtractableAnalyserTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val myFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation()
  }

  private class ExtractionHelper : ExtractionEngineHelper(EXTRACT_FUNCTION) {
    private val finishedSemaphore = Semaphore(0)

    fun waitUntilFinished() {
      finishedSemaphore.acquire()
    }

    override fun configureAndRun(
      project: Project,
      editor: Editor,
      descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
      onFinish: (ExtractionResult) -> Unit,
    ) {
      val newDescriptor =
        descriptorWithConflicts.descriptor.copy(
          suggestedNames = Collections.singletonList("newComposableFunction")
        )
      doRefactor(
        ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT)
      ) { er: ExtractionResult ->
        onFinish(er)
        finishedSemaphore.release()
      }
    }
  }

  private class InteractiveExtractionHelper : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
    private val finishedSemaphore = Semaphore(0)

    fun waitUntilFinished() {
      finishedSemaphore.acquire()
    }

    override fun validate(descriptor: ExtractableCodeDescriptor) =
      KotlinIntroduceConstantHandler.InteractiveExtractionHelper.validate(descriptor)

    override fun configureAndRun(
      project: Project,
      editor: Editor,
      descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
      onFinish: (ExtractionResult) -> Unit,
    ) {
      KotlinIntroduceConstantHandler.InteractiveExtractionHelper.configureAndRun(
        project,
        editor,
        descriptorWithConflicts,
      ) { er: ExtractionResult ->
        onFinish(er)
        finishedSemaphore.release()
      }
    }
  }

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
      """
        .trimIndent(),
    )

    val helper = ExtractionHelper()
    application.invokeAndWait {
      ExtractKotlinFunctionHandler(helper = helper)
        .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
    }
    helper.waitUntilFinished()

    myFixture.checkResult(
      // language=kotlin
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
      """
        .trimIndent()
    )
  }

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
      """
        .trimIndent(),
    )

    val helper = ExtractionHelper()
    application.invokeAndWait {
      ExtractKotlinFunctionHandler(helper = helper)
        .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
    }
    helper.waitUntilFinished()

    myFixture.checkResult(
      // language=kotlin
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
      """
        .trimIndent()
    )
  }

  @Test
  fun testConstantInComposableFunction() {
    // Regression test for b/301481575

    myFixture.loadNewFile(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun sourceFunction() {
        print(<selection>"foo"</selection>)
      }
      """
        .trimIndent(),
    )

    val helper = InteractiveExtractionHelper()
    application.invokeAndWait {
      KotlinIntroduceConstantHandler(helper = helper)
        .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
    }
    helper.waitUntilFinished()

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      private const val s = "foo"

      @Composable
      fun sourceFunction() {
        print(s)
      }
      """
        .trimIndent()
    )
  }
}
