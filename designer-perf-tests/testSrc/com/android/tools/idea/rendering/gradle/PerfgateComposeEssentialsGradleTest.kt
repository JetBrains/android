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

import com.android.testutils.delayUntilCondition
import com.android.tools.idea.compose.preview.getComposePreviewManagerKeyForTests
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.perflogger.Metric
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.testFramework.MapDataContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.After
import org.junit.Assert
import org.junit.Test

class PerfgateComposeEssentialsGradleTest : PerfgateComposeGradleTestBase() {

  @After
  fun tearDown() {
    AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled = false
  }

  @Test
  fun essentialsMode_5Previews() = runBlocking {
    Assert.assertEquals(
      1,
      composePreviewRepresentation
        .filteredPreviewElementsInstancesFlowForTest()
        .value
        .asCollection()
        .size,
    )
    setUpEssentialsMode()
    addPreviewsAndMeasure(
      4,
      1,
      listOf(
        // Measures the full rendering time, including ModuleClassLoader instantiation, inflation
        // and render.
        ElapsedTimeMeasurement(Metric("essentials_5_previews_refresh_time")),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          null,
          Metric("essentials_5_previews_total_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "rendering",
          Metric("essentials_5_previews_rendering_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutEditor",
          Metric("essentials_5_previews_layoutEditor_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutlib",
          Metric("essentials_5_previews_layoutlib_memory"),
        ),
      ),
    )
  }

  @Test
  fun essentialsMode_30Previews() = runBlocking {
    Assert.assertEquals(
      1,
      composePreviewRepresentation
        .filteredPreviewElementsInstancesFlowForTest()
        .value
        .asCollection()
        .size,
    )
    setUpEssentialsMode()
    addPreviewsAndMeasure(
      29,
      1,
      listOf(
        // Measures the full rendering time, including ModuleClassLoader instantiation, inflation
        // and render.
        ElapsedTimeMeasurement(Metric("essentials_30_previews_refresh_time")),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          null,
          Metric("essentials_30_previews_total_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "rendering",
          Metric("essentials_30_previews_rendering_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutEditor",
          Metric("essentials_30_previews_layoutEditor_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutlib",
          Metric("essentials_30_previews_layoutlib_memory"),
        ),
      ),
    )
  }

  @Test
  fun essentialsMode_500Previews() = runBlocking {
    Assert.assertEquals(
      1,
      composePreviewRepresentation
        .filteredPreviewElementsInstancesFlowForTest()
        .value
        .asCollection()
        .size,
    )
    setUpEssentialsMode()
    addPreviewsAndMeasure(
      499,
      1,
      listOf(
        // Measures the full rendering time, including ModuleClassLoader instantiation, inflation
        // and render.
        ElapsedTimeMeasurement(Metric("essentials_500_previews_refresh_time")),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          null,
          Metric("essentials_500_previews_total_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "rendering",
          Metric("essentials_500_previews_rendering_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutEditor",
          Metric("essentials_500_previews_layoutEditor_memory"),
        ),
        HeapSnapshotMemoryUseMeasurement(
          "android:designTools",
          "layoutlib",
          Metric("essentials_500_previews_layoutlib_memory"),
        ),
      ),
    )
  }

  private fun setUpEssentialsMode() = runBlocking {
    projectRule.runAndWaitForRefresh {
      runWriteActionAndWait {
        AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled = true
        ApplicationManager.getApplication()
          .messageBus
          .syncPublisher(NlOptionsConfigurable.Listener.TOPIC)
          .onOptionsChanged()
      }
      delayUntilCondition(500, 5.seconds) { previewView.galleryMode != null }
      previewView.galleryMode!!.triggerTabChange(
        MapDataContext().also {
          it.put(getComposePreviewManagerKeyForTests(), composePreviewRepresentation)
        },
        composePreviewRepresentation
          .filteredPreviewElementsInstancesFlowForTest()
          .value
          .asCollection()
          .first(),
      )
    }
  }
}
