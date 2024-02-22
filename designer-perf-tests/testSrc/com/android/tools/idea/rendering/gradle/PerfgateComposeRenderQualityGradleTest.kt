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

import com.android.tools.idea.compose.preview.ComposePreviewRefreshType
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.perflogger.Metric
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class PerfgateComposeRenderQualityGradleTest: PerfgateComposeGradleTestBase() {
  @Test
  fun renderQualityEnabled_5Previews() = projectRule.runWithRenderQualityEnabled {
    Assert.assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)
    addPreviewsAndMeasure(4, 5, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("renderQualityEnabled_5_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("renderQualityEnabled_5_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("renderQualityEnabled_5_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("renderQualityEnabled_5_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("renderQualityEnabled_5_previews_layoutlib_memory"))
    ), measuredRunnable = ::qualityRefresh)
  }

  @Test
  fun renderQualityEnabled_30Previews() = projectRule.runWithRenderQualityEnabled {
    Assert.assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)
    addPreviewsAndMeasure(29, 30, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("renderQualityEnabled_30_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("renderQualityEnabled_30_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("renderQualityEnabled_30_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("renderQualityEnabled_30_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("renderQualityEnabled_30_previews_layoutlib_memory"))
    ), measuredRunnable = ::qualityRefresh)
  }

  @Test
  fun renderQualityEnabled_200Previews() = projectRule.runWithRenderQualityEnabled {
    Assert.assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)
    addPreviewsAndMeasure(199, 200, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("renderQualityEnabled_200_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("renderQualityEnabled_200_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("renderQualityEnabled_200_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("renderQualityEnabled_200_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("renderQualityEnabled_200_previews_layoutlib_memory"))
    ), measuredRunnable = ::qualityRefresh)
  }

  private suspend fun qualityRefresh() {
    val rng = Random(seed = System.currentTimeMillis())
    projectRule.runAndWaitForRefresh(expectedRefreshType = ComposePreviewRefreshType.QUALITY) {
      previewView.mainSurface.zoomController.setScale(
        rng.nextDouble(from = 0.01, until = 1.5)
      )
      // The zoom change above should usually trigger a quality refresh
      // automatically, but here a quality refresh is manually requested
      // to avoid the error where the refresh doesn't happen in the
      // unfortunate case of the new scale being equal to the old scale
      composePreviewRepresentation.requestRefreshForTest(type = ComposePreviewRefreshType.QUALITY)
    }
  }
}