/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.scene

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.isInStaticAndNonAnimationMode
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.VisibleForTesting

/**
 * Sets the `hasAnimations` property of the [previewElement] to true if the given root component
 * contains animations, which is checked by calling the homonym method from ComposeViewAdapter via
 * reflection. This will determine the visibility of the animation inspector icon in the scene
 * toolbar.
 */
@VisibleForTesting
fun updateAnimationInspectorToolbarIcon(
  viewObj: Any,
  previewManager: ComposePreviewManager,
  previewElement: ComposePreviewElementInstance,
  animationToolingUsageTrackerFactory: () -> AnimationToolingUsageTracker
) {
  if (!previewManager.isInStaticAndNonAnimationMode) return
  try {
    val hasAnimationsMethod =
      viewObj::class
        .java
        .declaredMethods
        .single { it.name == "hasAnimations" }
        .also { it.isAccessible = true }
    val previewHasAnimations = hasAnimationsMethod.invoke(viewObj) as Boolean
    if (!previewElement.hasAnimations && previewHasAnimations) {
      animationToolingUsageTrackerFactory()
        .logEvent(
          AnimationToolingEvent(
            ComposeAnimationToolingEvent.ComposeAnimationToolingEventType
              .ANIMATION_INSPECTOR_AVAILABLE
          )
        )
    }
    previewElement.hasAnimations = previewHasAnimations
  } catch (e: Throwable) {
    Logger.getInstance(ComposeSceneUpdateListener::class.java)
      .debug("Could not check if the Composable has animations.", e)
  }
}

/**
 * [SceneManager.SceneUpdateListener] for Compose Preview. It provides the ability to update the
 * Compose Preview toolbar based on the root Composable.
 */
class ComposeSceneUpdateListener : SceneManager.SceneUpdateListener {
  override fun onUpdate(component: NlComponent, designSurface: DesignSurface<*>) {
    val previewManager = component.model.dataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val previewElementInstance =
      (component.model.dataContext.getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE)
        as? ComposePreviewElementInstance)
        ?: return
    val viewObj = component.viewInfo?.viewObject ?: return
    updateAnimationInspectorToolbarIcon(viewObj, previewManager, previewElementInstance) {
      AnimationToolingUsageTracker.getInstance(designSurface)
    }
  }
}
