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
package com.android.tools.idea.preview.interactive

import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.preview.interactive.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.uibuilder.scene.InteractiveSceneManager
import com.android.tools.rendering.RenderService
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.time.Duration

class InteractivePreviewManager(
  private val surface: DesignSurface<*>,
  initialFpsLimit: Int,
  private val interactiveScenesProvider: () -> Collection<InteractiveSceneManager>,
  private val usageTrackerProvider: () -> InteractivePreviewUsageTracker,
  private val delegateInteractionHandler: DelegateInteractionHandler,
) : Disposable {

  private val fpsCounter = FpsCalculator { System.nanoTime() }

  private val originalInteractionHandler = delegateInteractionHandler.delegate
  private val interactiveInteractionHandler = LayoutlibInteractionHandler(surface)

  var fpsLimit = initialFpsLimit
    set(value) {
      field = value
      fpsCounter.resetAndStart()
    }

  private val ticker =
    ControllableTicker(
        {
          if (!RenderService.isBusy() && fpsCounter.getFps() <= fpsLimit) {
            fpsCounter.incrementFrameCounter()
            interactiveScenesProvider().forEach { it.executeCallbacksAndRequestRender() }
          }
        },
        Duration.ofMillis(5),
      )
      .also { Disposer.register(this, it) }

  fun start() {
    interactiveScenesProvider().forEach { it.resetInteractiveEventsCounter() }
    fpsCounter.resetAndStart()
    ticker.start()
    delegateInteractionHandler.delegate = interactiveInteractionHandler

    // While in interactive mode, display a small ripple when clicking
    surface.enableMouseClickDisplay()
  }

  fun resume() {
    fpsCounter.resetAndStart()
    interactiveScenesProvider().forEach { it.resumeSessionClock() }
    ticker.start()
  }

  fun pause() {
    ticker.stop()
    interactiveScenesProvider().forEach { it.pauseSessionClock() }
  }

  fun stop() {
    surface.disableMouseClickDisplay()
    delegateInteractionHandler.delegate = originalInteractionHandler
    ticker.stop()
    logMetrics()
  }

  private fun logMetrics() {
    val touchEvents = interactiveScenesProvider().sumOf { it.interactiveEventsCount }
    usageTrackerProvider()
      .logInteractiveSession(fpsCounter.getFps(), fpsCounter.getDurationMs(), touchEvents)
  }

  override fun dispose() {}
}
