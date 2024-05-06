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
package com.android.tools.idea.uicheck

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.rendering.ComposeRenderTestBase
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.WearMarginAnalyzerInspection
import com.android.tools.perflogger.Metric
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.rendering.RenderResult
import org.junit.Before
import org.junit.Test

class PerfgateComposeVisualLintAnalyzerTest : ComposeRenderTestBase() {
  @Before
  override fun setUp() {
    super.setUp()
    val visualLintInspections = arrayOf(BoundsAnalyzerInspection(), BottomNavAnalyzerInspection(), BottomAppBarAnalyzerInspection(),
                                        TextFieldSizeAnalyzerInspection(), OverlapAnalyzerInspection(), LongTextAnalyzerInspection(),
                                        ButtonSizeAnalyzerInspection(), WearMarginAnalyzerInspection())
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @Test
  fun boundsAnalyzerRun() {
    visualLintAnalyzerRun(BoundsAnalyzer)
  }

  @Test
  fun buttonSizeAnalyzerRun() {
    visualLintAnalyzerRun(ButtonSizeAnalyzer)
  }

  @Test
  fun longTextAnalyzerRun() {
    visualLintAnalyzerRun(LongTextAnalyzer)
  }

  @Test
  fun overlapAnalyzerRun() {
    visualLintAnalyzerRun(OverlapAnalyzer)
  }

  @Test
  fun textFieldSizeAnalyzerRun() {
    visualLintAnalyzerRun(TextFieldSizeAnalyzer)
  }

  private fun visualLintAnalyzerRun(analyzer: VisualLintAnalyzer) {
    val facet = projectRule.androidFacet(":app")
    val resultToModelMap = mutableMapOf<RenderResult, NlModel>()
    UiCheckConfigurations.forEach { config ->
      val renderResult =
        renderPreviewElementForResult(
          facet,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.UiCheckPreviewKt.VisualLintErrorPreview",
            configuration = config.configuration,
            showDecorations = config.showDecorations
          ),
          customViewInfoParser = accessibilityBasedHierarchyParser
        )
          .get()!!
      val file = renderResult.sourceFile.virtualFile
      val nlModel =
        SyncNlModel.create(
          projectRule.fixture.testRootDisposable,
          NlComponentRegistrar,
          null,
          facet,
          file
        )
      resultToModelMap[renderResult] = nlModel
    }
    uiCheckBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("${analyzer.type}_run_time")),
                        HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("${analyzer.type}_memory_use"))),
      samplesCount = NUMBER_OF_SAMPLES) {
      resultToModelMap.forEach { (renderResult, nlModel) -> analyzer.findIssues(renderResult, nlModel) }
    }
  }
}