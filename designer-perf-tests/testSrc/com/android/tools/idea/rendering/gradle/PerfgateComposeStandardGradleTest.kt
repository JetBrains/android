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

import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.perflogger.Metric
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class PerfgateComposeStandardGradleTest: PerfgateComposeGradleTestBase() {
  @Test
  fun standardMode_5Previews() = runBlocking {
    Assert.assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)
    addPreviewsAndMeasure(
      4, 5, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("standard_5_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("standard_5_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("standard_5_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("standard_5_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("standard_5_previews_layoutlib_memory"))
    )
    )
  }

  @Test
  fun standardMode_30Previews() = runBlocking {
    Assert.assertEquals(1, composePreviewRepresentation.filteredPreviewElementsInstancesFlowForTest().value.asCollection().size)
    addPreviewsAndMeasure(
      29, 30, listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("standard_30_previews_refresh_time")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", null, Metric("standard_30_previews_total_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "rendering", Metric("standard_30_previews_rendering_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutEditor", Metric("standard_30_previews_layoutEditor_memory")),
      HeapSnapshotMemoryUseMeasurement("android:designTools", "layoutlib", Metric("standard_30_previews_layoutlib_memory"))
    )
    )
  }
}