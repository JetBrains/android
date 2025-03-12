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
package com.android.tools.idea.compose.preview.analytics

import com.android.SdkConstants
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.RESIZE_COMPOSE_PREVIEW_EVENT
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class ComposeResizeToolingUsageTrackerTest {
  @get:Rule val usageTrackerRule = UsageTrackerRule()
  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel()

  lateinit var surface: NlDesignSurface

  @Before
  fun setUp() {

    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER),
        )
        .build()
    }
    model.dataProvider =
      object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
        override fun getData(dataId: String): Any? =
          if (dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name) {
            mock<PsiComposePreviewElementInstance>()
          } else {
            null
          }
      }
    surface = NlSurfaceBuilder.builder(projectRule.project, projectRule.testRootDisposable).build()
    surface.addModelWithoutRender(model).join()

    ComposeResizeToolingUsageTracker.forceEnableForUnitTests = true
  }

  @Test
  fun test_saveSize() {
    ComposeResizeToolingUsageTracker.logResizeSaved(
      surface,
      ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE,
      100,
      200,
    )

    val event =
      usageTrackerRule.usages
        .find { it.studioEvent.kind == RESIZE_COMPOSE_PREVIEW_EVENT }!!
        .studioEvent
        .resizeComposePreviewEvent
    Truth.assertThat(event.eventType).isEqualTo(ResizeComposePreviewEvent.EventType.RESIZE_SAVED)
    Truth.assertThat(event.savedDeviceHeight).isEqualTo(200)
    Truth.assertThat(event.savedDeviceWidth).isEqualTo(100)
    Truth.assertThat(event.resizeMode)
      .isEqualTo(ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE)
  }

  @Test
  fun test_revertSize() {
    ComposeResizeToolingUsageTracker.logResizeReverted(
      surface,
      ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE,
    )

    val event =
      usageTrackerRule.usages
        .find { it.studioEvent.kind == RESIZE_COMPOSE_PREVIEW_EVENT }!!
        .studioEvent
        .resizeComposePreviewEvent
    Truth.assertThat(event.eventType).isEqualTo(ResizeComposePreviewEvent.EventType.RESIZE_REVERTED)
    Truth.assertThat(event.resizeMode).isEqualTo(ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE)
  }

  @Test
  fun test_stopSize() {
    ComposeResizeToolingUsageTracker.logResizeStopped(
      surface,
      ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE,
      100,
      200,
    )

    val event =
      usageTrackerRule.usages
        .find { it.studioEvent.kind == RESIZE_COMPOSE_PREVIEW_EVENT }!!
        .studioEvent
        .resizeComposePreviewEvent
    Truth.assertThat(event.eventType).isEqualTo(ResizeComposePreviewEvent.EventType.RESIZE_STOPPED)
    Truth.assertThat(event.stoppedDeviceHeight).isEqualTo(200)
    Truth.assertThat(event.stoppedDeviceWidth).isEqualTo(100)
    Truth.assertThat(event.resizeMode).isEqualTo(ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE)
  }
}
