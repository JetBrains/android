/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TargetProvider
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.model.getViewHandler
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.future.await

/**
 * Returns whether the [SceneView] has failed to render or has rendered with errors.
 *
 * Note that cancellations are not considered to be an error.
 */
fun SceneView.hasRenderErrors(): Boolean =
  (sceneManager as? LayoutlibSceneManager).hasRenderErrors()

/** Returns whether the [SceneView] has a valid image. */
fun SceneView.hasValidImage(): Boolean = (sceneManager as? LayoutlibSceneManager).hasValidImage()

fun LayoutlibSceneManager?.hasValidImage(): Boolean =
  this?.renderResult?.renderedImage?.isValid ?: false

fun LayoutlibSceneManager?.hasRenderErrors(): Boolean = this?.renderResult.isErrorResult()

suspend fun LayoutlibSceneManager.executeInRenderSession(
  useLongTimeout: Boolean = false,
  callback: () -> Unit,
) {
  this.executeInRenderSessionAsync({ callback() }, if (useLongTimeout) 5L else 0L, TimeUnit.SECONDS)
    .await()
}

/** Update the [TargetProvider] of the given [component] and its subcomponents. */
internal fun updateTargetProviders(component: SceneComponent, whenUpdated: Runnable) {
  val handler = component.nlComponent.getViewHandler(whenUpdated)
  component.setTargetProvider(handler)

  for (child in component.children) {
    updateTargetProviders(child, whenUpdated)
  }
}

/** Find the corresponding trigger for tracking metrics according to the [changeType]. */
internal fun getTriggerFromChangeType(changeType: ChangeType?): LayoutEditorRenderResult.Trigger? =
  when (changeType) {
    ChangeType.RESOURCE_EDIT,
    ChangeType.RESOURCE_CHANGED -> LayoutEditorRenderResult.Trigger.RESOURCE_CHANGE
    ChangeType.EDIT,
    ChangeType.ADD_COMPONENTS,
    ChangeType.DELETE,
    ChangeType.DND_COMMIT,
    ChangeType.DND_END,
    ChangeType.DROP,
    ChangeType.RESIZE_END,
    ChangeType.RESIZE_COMMIT -> LayoutEditorRenderResult.Trigger.EDIT
    ChangeType.BUILD -> LayoutEditorRenderResult.Trigger.BUILD
    else -> null
  }
