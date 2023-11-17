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

import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.rendering.ComposeRenderTestBase
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.perflogger.Metric
import com.android.tools.preview.SingleComposePreviewElementInstance
import org.junit.Test

class PerfgateComposeAccessibilityTest : ComposeRenderTestBase() {
  @Test
  fun testAccessibilityParsingPerformance() {
    uiCheckBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("render_time_with_accessibility")),
                        HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("render_memory_use_with_accessibility"))),
      samplesCount = NUMBER_OF_SAMPLES) {
      renderPreview(withAccessibilityParser = true)
    }
    uiCheckBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("render_time_without_accessibility")),
                        HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("render_memory_use_without_accessibility"))),
      samplesCount = NUMBER_OF_SAMPLES ) {
      renderPreview(withAccessibilityParser = false)
    }
  }

  private fun renderPreview(withAccessibilityParser: Boolean) {
    renderPreviewElementForResult(
      projectRule.androidFacet(":app"),
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.UiCheckPreviewKt.VisualLintErrorPreview"),
      customViewInfoParser = if (withAccessibilityParser) accessibilityBasedHierarchyParser else null
    )
      .get()!!
  }
}