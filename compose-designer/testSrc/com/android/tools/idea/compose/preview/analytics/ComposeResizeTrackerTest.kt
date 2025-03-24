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
import com.android.tools.idea.common.fixtures.MouseEventBuilder
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.surface.InteractionInformation
import com.android.tools.idea.common.surface.MouseReleasedEvent
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.interaction.CanvasResizeInteraction
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.RESIZE_COMPOSE_PREVIEW_EVENT
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@Ignore("b/405935324")
class ComposeResizeTrackerTest {
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
  fun testSendSizeOnCommit() {
    val view = surface.sceneViews[0]
    val resizeInteraction =
      CanvasResizeInteraction(surface, view as ScreenView, view.sceneManager.model.configuration)
    val x = 100
    val y = 200
    resizeInteraction.commit(
      MouseReleasedEvent(MouseEventBuilder(x, y).build(), InteractionInformation(x, y, 0))
    )
    val event =
      usageTrackerRule.usages
        .find { it.studioEvent.kind == RESIZE_COMPOSE_PREVIEW_EVENT }!!
        .studioEvent
        .resizeComposePreviewEvent
    assertThat(event.eventType).isEqualTo(ResizeComposePreviewEvent.EventType.RESIZE_STOPPED)
    // TODO("b/405935324"): update
    assertThat(event.resizeMode).isEqualTo(ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE)
  }
}
