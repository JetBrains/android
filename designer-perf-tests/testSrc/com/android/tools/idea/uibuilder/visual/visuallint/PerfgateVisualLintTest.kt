/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.testutils.TestUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.WearMarginAnalyzerInspection
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal const val NUMBER_OF_SAMPLES = 5

val visualLintingBenchmark = Benchmark.Builder("Visual Linting Benchmark")
  .setProject("Design Tools")
  .setDescription("Benchmark for measuring performance of Visual Linting, using $NUMBER_OF_SAMPLES samples.")
  .build()

class PerfgateVisualLintTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer-perf-tests/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
    DesignerTypeRegistrar.register(LayoutFileType)
    val visualLintInspections = arrayOf(
      BoundsAnalyzerInspection(), BottomNavAnalyzerInspection(), BottomAppBarAnalyzerInspection(),
      TextFieldSizeAnalyzerInspection(), OverlapAnalyzerInspection(), LongTextAnalyzerInspection(),
      ButtonSizeAnalyzerInspection(), WearMarginAnalyzerInspection()
    )
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
    StudioRenderService.setForTesting(projectRule.project, null)
  }

  @Test
  fun backgroundLintingTimeForPhone() {
    projectRule.load("projects/visualLintApplication")
    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val visualLintIssueProvider = ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable)

    val dashboardLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_dashboard.xml")!!
    val nlModel = SyncNlModel.create(
      projectRule.fixture.testRootDisposable,
      NlComponentRegistrar,
      AndroidBuildTargetReference.gradleOnly(facet),
      dashboardLayout
    )
    val visualLintExecutorService = MoreExecutors.newDirectExecutorService()
    visualLintingBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("phone_background_linting_time")),
                        // TODO(b/352075517): re-enable memory measurement once performance issue is fixed
                        // HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("phone_background_linting_memory_use"))
      ),
      samplesCount = NUMBER_OF_SAMPLES) {
      VisualLintService.getInstance(projectRule.project)
        .runVisualLintAnalysis(projectRule.fixture.testRootDisposable, visualLintIssueProvider, listOf(nlModel), emptyMap(), visualLintExecutorService)
      // Wait for visual lint tasks to complete
      visualLintExecutorService.waitForTasksToComplete()
    }
  }

  @Test
  fun backgroundLintingTimeForWear() {
    projectRule.load("projects/visualLintApplication")
    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val visualLintIssueProvider = ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable)

    val wearLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/wear_layout.xml")!!
    val wearConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_small_round")
    val wearModel = SyncNlModel.create(
      projectRule.fixture.testRootDisposable,
      NlComponentRegistrar,
      AndroidBuildTargetReference.gradleOnly(facet),
      wearLayout,
      wearConfiguration
    )
    val visualLintExecutorService = MoreExecutors.newDirectExecutorService()
    visualLintingBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("wear_background_linting_time")),
                        // TODO(b/352075517): re-enable memory measurement once performance issue is fixed
                        // HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("wear_background_linting_memory_use"))
      ),
      samplesCount = NUMBER_OF_SAMPLES) {
      VisualLintService.getInstance(projectRule.project)
        .runVisualLintAnalysis(projectRule.fixture.testRootDisposable, visualLintIssueProvider, listOf(wearModel), emptyMap(), visualLintExecutorService)
      // Wait for visual lint tasks to complete
      visualLintExecutorService.waitForTasksToComplete()
    }
  }
}

private fun ExecutorService.waitForTasksToComplete() {
  submit {}?.get(30, TimeUnit.SECONDS)
}