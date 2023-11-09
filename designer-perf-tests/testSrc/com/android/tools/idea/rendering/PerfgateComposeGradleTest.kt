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
package com.android.tools.idea.rendering

import com.android.testutils.delayUntilCondition
import com.android.tools.idea.compose.gradle.ComposePreviewFakeUiGradleRule
import com.android.tools.idea.compose.gradle.preview.TestComposePreviewView
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.getComposePreviewManagerKeyForTests
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private const val NUMBER_OF_SAMPLES = 5

private val composeGradleTimeBenchmark = Benchmark.Builder("Compose Preview Gradle Benchmark")
  .setProject("Design Tools")
  .setDescription("Base line for Compose Preview time and memory (mean) after $NUMBER_OF_SAMPLES" +
                  " samples. The tests are configured using a FakeUi+Gradle approach to make" +
                  " them run in a context similar to production")
  .build()

class PerfgateComposeGradleTest {
  @get:Rule val projectRule = ComposePreviewFakeUiGradleRule(
    projectPath = SIMPLE_COMPOSE_PROJECT_PATH,
    previewFilePath = "app/src/main/java/google/simpleapplication/MainActivity.kt",
    testDataPath = "tools/adt/idea/designer-perf-tests/testData",
    kotlinVersion = DEFAULT_KOTLIN_VERSION,
    enableRenderQuality = false)

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture
  private val composePreviewRepresentation: ComposePreviewRepresentation
    get() = projectRule.composePreviewRepresentation
  private val psiMainFile: PsiFile
    get() = projectRule.psiMainFile
  private val previewView: TestComposePreviewView
    get() = projectRule.previewView

  @After
  fun tearDown() {
    AndroidEditorSettings.getInstance().globalState.isComposePreviewEssentialsModeEnabled = false
  }

  @Test
  fun standardMode_5Previews() = runBlocking {
    assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)
    addPreviewsAndMeasure(4, 5, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("standard_5_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("standard_5_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("standard_5_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("standard_5_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("standard_5_previews_layoutlib_memory"))))
  }

  @Test
  fun standardMode_30Previews() = runBlocking {
    assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)
    addPreviewsAndMeasure(29, 30, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("standard_30_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("standard_30_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("standard_30_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("standard_30_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("standard_30_previews_layoutlib_memory"))))
  }

  @Test
  fun essentialsMode_5Previews() = runBlocking {
    assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)
    setUpEssentialsMode()
    addPreviewsAndMeasure(4, 1, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("essentials_5_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("essentials_5_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("essentials_5_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("essentials_5_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("essentials_5_previews_layoutlib_memory"))))
  }

  @Test
  fun essentialsMode_30Previews() = runBlocking {
    assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)
    setUpEssentialsMode()
    addPreviewsAndMeasure(29, 1, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("essentials_30_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("essentials_30_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("essentials_30_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("essentials_30_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("essentials_30_previews_layoutlib_memory"))))
  }

  @Test
  fun essentialsMode_500Previews() = runBlocking {
    assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)
    setUpEssentialsMode()
    addPreviewsAndMeasure(499, 1, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("essentials_500_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("essentials_500_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("essentials_500_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("essentials_500_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("essentials_500_previews_layoutlib_memory"))))
  }

  /**
   * First, without using the [measurements], add [nPreviewsToAdd] @Previews on top of the first @Preview found in [psiMainFile], and wait
   * for a refresh to happen.
   * Then, perform a new full refresh under all [measurements] (see [measureOperation]).
   */
  private fun addPreviewsAndMeasure(nPreviewsToAdd: Int, nExpectedPreviewInstances: Int, measurements: List<MetricMeasurement<Unit>>) = runBlocking {
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = maxOf(15, nExpectedPreviewInstances).seconds) {
      runWriteActionAndWait {
        fixture.openFileInEditor(psiMainFile.virtualFile)
        fixture.moveCaret("|@Preview")
        fixture.editor.executeAndSave { fixture.editor.insertText(generatePreviewAnnotations(nPreviewsToAdd)) }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
        if(AndroidEditorSettings.getInstance().globalState.isComposePreviewEssentialsModeEnabled) {
          composePreviewRepresentation.requestRefreshForTest()
        }
      }
    }
    assertEquals(nExpectedPreviewInstances, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.size)

    composeGradleTimeBenchmark.measureOperation(measurements, samplesCount = NUMBER_OF_SAMPLES, printSamples = true) {
      runBlocking {
        projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = maxOf(15, nExpectedPreviewInstances).seconds) {
          composePreviewRepresentation.invalidate()
          composePreviewRepresentation.requestRefreshForTest()
        }
      }
    }
  }

  private fun setUpEssentialsMode() = runBlocking {
    projectRule.runAndWaitForRefresh {
      runWriteActionAndWait {
        AndroidEditorSettings.getInstance().globalState.isComposePreviewEssentialsModeEnabled = true
        composePreviewRepresentation.updateGalleryModeForTest()
      }
      delayUntilCondition(500, 5.seconds) { previewView.galleryMode != null }
      previewView.galleryMode!!.triggerTabChange(MapDataContext().also {
        it.put(getComposePreviewManagerKeyForTests(), composePreviewRepresentation)
      }, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.first())
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