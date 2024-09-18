/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.animation

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.wear.preview.WearTilePreviewElement
import com.android.tools.idea.wear.preview.animation.analytics.WearTileAnimationTracker
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A specialized [AnimationPreview] for Wear Tile animations, providing controls and visualization
 * for animating Wear Tile elements within the Android Studio preview.
 *
 * @param project The project associated with this preview.
 * @param surface The design surface containing the Wear Tile preview.
 * @param wearPreviewElement The specific Wear Tile preview element to inspect.
 * @param tracker The tracker used to log events.
 */
class WearTileAnimationPreview(
  val project: Project,
  private val surface: NlDesignSurface,
  private val wearPreviewElement: WearTilePreviewElement<*>,
  private val tracker: WearTileAnimationTracker,
) :
  AnimationPreview<SupportedWearTileAnimationManager>(
    project,
    sceneManagerProvider = getter@{
        val modelForElement =
          surface.models.find {
            it.dataContext.getData(PREVIEW_ELEMENT_INSTANCE) == wearPreviewElement
          } ?: return@getter null
        surface.getSceneManager(modelForElement)
      },
    rootComponent = surface as JComponent,
    tracker,
  ) {

  override suspend fun setClockTime(newValue: Int, longTimeout: Boolean) {
    executeInRenderSession(longTimeout) {
      animations.forEach {
        if (!it.frozenState.value.isFrozen) {
          it.animation.setTime(newValue.toLong())
        } else {
          it.animation.setTime(it.frozenState.value.frozenAt.toLong())
        }
      }
    }
  }

  override suspend fun updateMaxDuration(longTimeout: Boolean) {
    animations
      .maxOfOrNull { it.timelineMaximumMs }
      ?.let { maxDurationPerIteration.value = it.toLong() }
  }

  private suspend fun createAnimationManager(
    animation: ProtoAnimation
  ): SupportedWearTileAnimationManager {
    var title = ""
    executeInRenderSession { title = animation.name }
    return SupportedWearTileAnimationManager(
      animation,
      tracker,
      getCurrentTime = { clockControl.currentValue() },
      ::executeInRenderSession,
      tabbedPane,
      surface,
      title,
      playbackControls,
      {
        updateTimelineElements()
        renderAnimation()
      },
      scope,
    )
  }

  private suspend fun updateAllAnimations(protoAnimations: List<ProtoAnimation>) {
    invalidatePanel().join()
    protoAnimations
      .filter { it.isTerminal }
      .forEach {
        val tab = withContext(AndroidDispatchers.uiThread) { createAnimationManager(it) }
        tab.setup()
        withContext(AndroidDispatchers.uiThread) { addAnimationManager(tab) }
      }
    updateMaxDuration(true)
    updateTimelineElements()
  }

  init {
    scope.launch {
      wearPreviewElement.tileServiceViewAdapter.collectLatest {
        updateAllAnimations(it?.getAnimations() ?: emptyList())
      }
    }
  }
}
