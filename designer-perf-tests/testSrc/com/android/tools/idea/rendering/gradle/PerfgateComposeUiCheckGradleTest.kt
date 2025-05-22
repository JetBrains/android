/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.perflogger.Metric
import com.intellij.testFramework.assertInstanceOf
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class PerfgateComposeUiCheckGradleTest : PerfgateComposeGradleTestBase() {
  @Test
  fun testUiCheckMode() =
    projectRule.runWithRenderQualityEnabled {
      assertEquals(
        1,
        composePreviewRepresentation
          .renderedPreviewElementsInstancesFlowForTest()
          .value
          .asCollection()
          .size,
      )
      assertInstanceOf<UiCheckModeFilter.Disabled<PsiComposePreviewElementInstance>>(
        composePreviewRepresentation.uiCheckFilterFlow.value
      )
      val uiCheckElement =
        composePreviewRepresentation
          .renderedPreviewElementsInstancesFlowForTest()
          .value
          .asCollection()
          .single()

      // Start UI Check mode
      projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 60.seconds) {
        composePreviewRepresentation.setMode(
          PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false))
        )
      }
      assertInstanceOf<UiCheckModeFilter.Enabled<PsiComposePreviewElementInstance>>(
        composePreviewRepresentation.uiCheckFilterFlow.value
      )

      // Now do measure time and memory usage of full refreshes when ui check is enabled.
      addPreviewsAndMeasure(
        nPreviewsToAdd = 0,
        nExpectedPreviewInstances =
          composePreviewRepresentation
            .renderedPreviewElementsInstancesFlowForTest()
            .value
            .asCollection()
            .size,
        listOf(
          // Measures the full rendering time, including ModuleClassLoader instantiation, inflation
          // and render.
          ElapsedTimeMeasurement(Metric("uiCheckMode_refresh_time")),
          HeapSnapshotMemoryUseMeasurement(
            "android:designTools",
            null,
            Metric("uiCheckMode_total_memory"),
          ),
          HeapSnapshotMemoryUseMeasurement(
            "android:designTools",
            "rendering",
            Metric("uiCheckMode_rendering_memory"),
          ),
          HeapSnapshotMemoryUseMeasurement(
            "android:designTools",
            "layoutEditor",
            Metric("uiCheckMode_layoutEditor_memory"),
          ),
          HeapSnapshotMemoryUseMeasurement(
            "android:designTools",
            "layoutlib",
            Metric("uiCheckMode_layoutlib_memory"),
          ),
        ),
        nSamples = 1, // run it only once as this test takes a long time
      )
    }
}
