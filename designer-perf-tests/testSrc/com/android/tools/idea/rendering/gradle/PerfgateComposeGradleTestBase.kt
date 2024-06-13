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
package com.android.tools.idea.rendering.gradle

import com.android.tools.idea.compose.gradle.ComposePreviewFakeUiGradleRule
import com.android.tools.idea.compose.gradle.preview.TestComposePreviewView
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.rendering.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.rendering.MetricMeasurement
import com.android.tools.idea.rendering.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.perflogger.Benchmark
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.Assert
import org.junit.Rule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val NUMBER_OF_SAMPLES = 5

private val composeGradleTimeBenchmark = Benchmark.Builder("Compose Preview Gradle Benchmark")
  .setProject("Design Tools")
  .setDescription("Base line for Compose Preview time and memory (mean) after $NUMBER_OF_SAMPLES" +
                  " samples. The tests are configured using a FakeUi+Gradle approach to make" +
                  " them run in a context similar to production")
  .build()

open class PerfgateComposeGradleTestBase {
  @get:Rule
  val projectRule = ComposePreviewFakeUiGradleRule(
    projectPath = SIMPLE_COMPOSE_PROJECT_PATH,
    previewFilePath = "app/src/main/java/google/simpleapplication/MainActivity.kt",
    testDataPath = "tools/adt/idea/designer-perf-tests/testData",
    kotlinVersion = DEFAULT_KOTLIN_VERSION,
    enableRenderQuality = false
  )

  protected val fixture: CodeInsightTestFixture
    get() = projectRule.fixture
  protected val composePreviewRepresentation: ComposePreviewRepresentation
    get() = projectRule.composePreviewRepresentation
  private val psiMainFile: PsiFile
    get() = projectRule.psiMainFile
  protected val previewView: TestComposePreviewView
    get() = projectRule.previewView

  private suspend fun fullRefresh(allRefreshesFinishTimeout: Duration) {
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = allRefreshesFinishTimeout) {
      composePreviewRepresentation.invalidate()
      composePreviewRepresentation.requestRefreshForTest()
    }
  }

  /**
   * First, without using the [measurements], add [nPreviewsToAdd] @Previews on top of the first @Preview found in [psiMainFile], and wait
   * for a refresh to happen.
   * Then, execute the [measuredRunnable] under all [measurements] (see [measureOperation]).
   */
  protected fun addPreviewsAndMeasure(
    nPreviewsToAdd: Int,
    nExpectedPreviewInstances: Int,
    measurements: List<MetricMeasurement<Unit>>,
    measuredRunnable: suspend () -> Unit = { fullRefresh(maxOf(15, nExpectedPreviewInstances).seconds) }
  ) = runBlocking {
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = maxOf(15, nExpectedPreviewInstances).seconds) {
      runWriteActionAndWait {
        fixture.openFileInEditor(psiMainFile.virtualFile)
        fixture.moveCaret("|@Preview")
        fixture.editor.executeAndSave { fixture.editor.insertText(generatePreviewAnnotations(nPreviewsToAdd)) }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
        if(AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled) {
          composePreviewRepresentation.requestRefreshForTest()
        }
      }
    }
    Assert.assertEquals(nExpectedPreviewInstances, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)

    composeGradleTimeBenchmark.measureOperation(measurements, samplesCount = NUMBER_OF_SAMPLES, printSamples = true) {
      runBlocking {
        measuredRunnable()
      }
    }
  }

  private fun generatePreviewAnnotations(nPreviews: Int) : String {
    val builder = StringBuilder()
    repeat(nPreviews) {
      // Use 'showSystemUi = true' for the previews to be somewhat big
      builder.appendLine("@Preview(name = \"new ${it}\", showSystemUi = true, showBackground = true)")
    }
    return builder.toString()
  }
}