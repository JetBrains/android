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
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor as K2ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts as K2ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration as K2ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult as K2ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper as K2ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.KotlinIntroduceConstantHandler as K2KotlinIntroduceConstantHandler
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

  private class K2ExtractionHelper : K2ExtractionEngineHelper(EXTRACT_FUNCTION) {
    private val finishedSemaphore = Semaphore(0)

    fun waitUntilFinished() {
      finishedSemaphore.acquire()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun configureAndRun(
      project: Project,
      editor: Editor,
      descriptorWithConflicts: K2ExtractableCodeDescriptorWithConflicts,
      onFinish: (K2ExtractionResult) -> Unit,
    ) {
      // `descriptorWithConflicts.descriptor.copy(..)` runs the constructor of
      // ExtractableCodeDescriptor
      // that calls AA for return type check. Since the copied one has the exactly same type as the
      // initial descriptor, if we only copy the boolean value, we do not need `allowAnalysisOnEdt`.
      // To do so, we have to update `ExtractableCodeDescriptor`.
      allowAnalysisOnEdt {
        val newDescriptor =
          descriptorWithConflicts.descriptor.copy(
            suggestedNames = Collections.singletonList("newComposableFunction")
          )
        doRefactor(
          K2ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT)
        ) { er: K2ExtractionResult ->
          onFinish(er)
          finishedSemaphore.release()
        }
      }
    }
  }

  private class K2InteractiveExtractionHelper : K2ExtractionEngineHelper(INTRODUCE_CONSTANT) {
    private val finishedSemaphore = Semaphore(0)

    fun waitUntilFinished() {
      finishedSemaphore.acquire()
    }

    override fun validate(
      descriptor: K2ExtractableCodeDescriptor
    ): K2ExtractableCodeDescriptorWithConflicts =
      K2KotlinIntroduceConstantHandler.InteractiveExtractionHelper.validate(descriptor)

    override fun configureAndRun(
      project: Project,
      editor: Editor,
      descriptorWithConflicts: K2ExtractableCodeDescriptorWithConflicts,
      onFinish: (K2ExtractionResult) -> Unit,
    ) {
      K2KotlinIntroduceConstantHandler.InteractiveExtractionHelper.configureAndRun(
        project,
        editor,
        descriptorWithConflicts,
      ) { er: K2ExtractionResult ->
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

    if (KotlinPluginModeProvider.isK2Mode()) {
      val helper = K2ExtractionHelper()
      application.invokeAndWait {
        KotlinFirExtractFunctionHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    } else {
      val helper = ExtractionHelper()
      application.invokeAndWait {
        ExtractKotlinFunctionHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    }

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
    // TODO(353683851): The upstream IntelliJ has an issue related to
    //  annotation rendering for the container function. Enable K2 after
    //  landing https://kotlin.jetbrains.space/p/kotlin/reviews/1095/files
    //  to studio-main.
    if (KotlinPluginModeProvider.isK2Mode()) return

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

    if (KotlinPluginModeProvider.isK2Mode()) {
      val helper = K2ExtractionHelper()
      application.invokeAndWait {
        KotlinFirExtractFunctionHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    } else {
      val helper = ExtractionHelper()
      application.invokeAndWait {
        ExtractKotlinFunctionHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    }

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

    if (KotlinPluginModeProvider.isK2Mode()) {
      val helper = K2InteractiveExtractionHelper()
      application.invokeAndWait {
        K2KotlinIntroduceConstantHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    } else {
      val helper = InteractiveExtractionHelper()
      application.invokeAndWait {
        KotlinIntroduceConstantHandler(helper = helper)
          .invoke(myFixture.project, myFixture.editor, myFixture.file!!, null)
      }
      helper.waitUntilFinished()
    }

    val constValName = if (KotlinPluginModeProvider.isK2Mode()) "string" else "s"
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      private const val $constValName = "foo"

      @Composable
      fun sourceFunction() {
        print($constValName)
      }
      """
        .trimIndent()
    )
  }
}
