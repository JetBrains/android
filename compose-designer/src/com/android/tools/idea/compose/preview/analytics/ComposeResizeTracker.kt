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

import com.android.annotations.TestOnly
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.uibuilder.analytics.ResizeTracker
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent.EventType
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent.ResizeMode
import com.intellij.openapi.application.ApplicationManager

/** Implementation of [ResizeTracker] for the Compose Preview. */
class ComposeResizeTracker : ResizeTracker {
  override fun isApplicable(sceneManager: SceneManager): Boolean {
    return sceneManager.model.dataProvider?.previewElement() != null
  }

  override fun reportResizeStopped(
    sceneManager: SceneManager,
    stoppedDeviceWidthDp: Int,
    stoppedDeviceHeightDp: Int,
  ) {
    val layoutlibSceneManager = sceneManager as? LayoutlibSceneManager ?: return
    val showDecorations = layoutlibSceneManager.sceneRenderConfiguration.showDecorations
    val mode = if (showDecorations) ResizeMode.DEVICE_RESIZE else ResizeMode.COMPOSABLE_RESIZE
    ComposeResizeToolingUsageTracker.logResizeStopped(
      sceneManager.designSurface,
      mode,
      stoppedDeviceWidthDp,
      stoppedDeviceHeightDp,
    )
  }
}

/** Usage tracker for the Compose Resize tooling. */
object ComposeResizeToolingUsageTracker {
  @TestOnly var forceEnableForUnitTests = false

  fun logResizeStopped(
    surface: DesignSurface<*>?,
    mode: ResizeMode,
    deviceWidthDp: Int,
    deviceHeightDp: Int,
  ) {
    logEvent(surface) {
      eventType = EventType.RESIZE_STOPPED
      resizeMode = mode
      stoppedDeviceWidth = deviceWidthDp
      stoppedDeviceHeight = deviceHeightDp
    }
  }

  fun logResizeSaved(
    surface: DesignSurface<*>?,
    mode: ResizeMode,
    deviceWidthDp: Int,
    deviceHeightDp: Int,
  ) {
    logEvent(surface) {
      eventType = EventType.RESIZE_SAVED
      resizeMode = mode
      savedDeviceWidth = deviceWidthDp
      savedDeviceHeight = deviceHeightDp
    }
  }

  fun logResizeReverted(surface: DesignSurface<*>?, mode: ResizeMode) {
    logEvent(surface) {
      eventType = EventType.RESIZE_REVERTED
      resizeMode = mode
    }
  }

  private fun logEvent(
    surface: DesignSurface<*>?,
    eventBuilder: ResizeComposePreviewEvent.Builder.() -> Unit,
  ) {
    if (!ApplicationManager.getApplication().isUnitTestMode || forceEnableForUnitTests) {
      UsageTracker.log(createAndroidStudioEvent(eventBuilder).setApplicationId(surface))
    }
  }

  /** Creates and returns an [AndroidStudioEvent.Builder] with an [ResizeComposePreviewEvent]. */
  private fun createAndroidStudioEvent(
    eventBuilder: ResizeComposePreviewEvent.Builder.() -> Unit
  ): AndroidStudioEvent.Builder {
    val resizeComposePreviewEvent =
      ResizeComposePreviewEvent.newBuilder().apply(eventBuilder).build()
    return AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.RESIZE_COMPOSE_PREVIEW_EVENT)
      .setResizeComposePreviewEvent(resizeComposePreviewEvent)
  }
}
