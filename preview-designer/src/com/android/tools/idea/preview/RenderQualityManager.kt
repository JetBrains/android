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
package com.android.tools.idea.preview

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.rendering.isCancellationException
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.awt.Rectangle
import java.awt.event.AdjustmentEvent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

/**
 * Base interface for render quality management. Any RenderQualityManager should fulfill the three
 * following responsibilities:
 * - Detect UI changes that might derive in a need of changing the render quality of some previews.
 * - Provide the target quality for a given preview ([getTargetQuality]).
 * - Discern whether a specific preview needs a quality change ([needsQualityChange]).
 */
interface RenderQualityManager {
  fun getTargetQuality(sceneManager: LayoutlibSceneManager): Float

  fun needsQualityChange(sceneManager: LayoutlibSceneManager): Boolean

  fun pause()

  fun resume()
}

/**
 * Base interface needed for using a [DefaultRenderQualityManager].
 *
 * This policy intends to provide a way of easily tweaking the behaviour of a
 * [DefaultRenderQualityManager] according to what each tool needs.
 */
interface RenderQualityPolicy {
  val acceptedErrorMargin: Float

  val debounceTimeMillis: Long

  fun getTargetQuality(scale: Double, isVisible: Boolean): Float
}

fun getDefaultPreviewQuality() = if (EssentialsMode.isEnabled()) 0.75f else 0.95f

/**
 * Default [RenderQualityManager] implementation, configurable by a [RenderQualityPolicy], that
 * given a [NlDesignSurface], it detects its scroll, zoom and resize changes, and delegates the
 * corresponding render quality preview updates to [onQualityChangeMightBeNeeded].
 *
 * Note that, as its name suggests, when [onQualityChangeMightBeNeeded] is executed, it may happen
 * that no quality change is actually needed, it is each tool's responsibility to check this before
 * refreshing its previews. This is because doing such check would probably be expensive and the
 * quality changes are likely to be low priority, so each tool may schedule them for later, for
 * example by using a low priority [PreviewRefreshRequest].
 */
class DefaultRenderQualityManager(
  private val mySurface: NlDesignSurface,
  private val myPolicy: RenderQualityPolicy,
  private val onQualityChangeMightBeNeeded: () -> Unit
) : RenderQualityManager {
  private val scope = AndroidCoroutineScope(mySurface)
  private var isPaused = false

  private val uiDataLock = ReentrantLock()
  @GuardedBy("uiDataLock") private var sceneViewRectangles: Map<SceneView, Rectangle?> = emptyMap()
  @GuardedBy("uiDataLock") private var scrollRectangle: Rectangle? = null
  @GuardedBy("uiDataLock") private var isUiDataUpToDate = false

  init {
    scope.launch {
      disposableCallbackFlow<Unit>(
          "RenderQualityManager flow",
          logger = null,
          parentDisposable = mySurface
        ) {
          val panZoomListener =
            object : PanZoomListener {
              override fun zoomChanged(previousScale: Double, newScale: Double) {
                trySend(Unit)
              }

              override fun panningChanged(adjustmentEvent: AdjustmentEvent?) {
                trySend(Unit)
              }
            }
          val designSurfaceListener =
            object : DesignSurfaceListener {
              override fun modelChanged(surface: DesignSurface<*>, model: NlModel?) {
                trySend(Unit)
              }
            }
          mySurface.addPanZoomListener(panZoomListener)
          mySurface.addListener(designSurfaceListener)
          Disposer.register(disposable) {
            mySurface.removePanZoomListener(panZoomListener)
            mySurface.removeListener(designSurfaceListener)
          }
        }
        .debounce(myPolicy.debounceTimeMillis)
        .collect {
          // Mark the ui data as outdated,
          // but don't refresh it now as it may not be needed until later
          uiDataLock.withLock { isUiDataUpToDate = false }
          onQualityChangeMightBeNeeded()
        }
    }
  }

  private fun isSceneManagerVisible(sceneManager: LayoutlibSceneManager): Boolean {
    return uiDataLock.withLock {
      // A null scrollRectangle should be caused by a not-scrollable surface, and in such case
      // all previews are considered to be visible
      scrollRectangle == null ||
        sceneManager.sceneViews.any {
          sceneViewRectangles.getOrDefault(it, null)?.intersects(scrollRectangle!!) == true
        }
    }
  }

  override fun getTargetQuality(sceneManager: LayoutlibSceneManager): Float {
    if (isPaused) return getDefaultPreviewQuality()
    uiDataLock.withLock {
      if (!isUiDataUpToDate) {
        sceneViewRectangles = mySurface.findSceneViewRectangles()
        scrollRectangle = mySurface.currentScrollRectangle
        isUiDataUpToDate = true
      }
    }
    return myPolicy.getTargetQuality(mySurface.scale, isSceneManagerVisible(sceneManager))
  }

  override fun needsQualityChange(sceneManager: LayoutlibSceneManager): Boolean =
    !isPaused &&
      sceneManager.let {
        // Refreshes are skipped in any of the following scenarios:
        // - Last render failed and not due to a cancellation exception
        // - The current target quality is substantially different to the one used in the last
        //   successful render or the last render was cancelled.
        it.renderResult.isCancellationException() ||
          (!it.renderResult.isErrorResult() &&
            abs(it.lastRenderQuality - getTargetQuality(it)) > myPolicy.acceptedErrorMargin)
      }

  override fun pause() {
    isPaused = true
  }

  override fun resume() {
    isPaused = false
  }
}

/**
 * A [RenderQualityManager] that doesn't detect the need of changing quality and delegates the
 * target quality calculation to a [qualityProvider].
 */
class SimpleRenderQualityManager(private val qualityProvider: () -> Float) : RenderQualityManager {
  override fun getTargetQuality(sceneManager: LayoutlibSceneManager): Float {
    return qualityProvider()
  }

  override fun needsQualityChange(sceneManager: LayoutlibSceneManager): Boolean {
    return false
  }

  override fun pause() = Unit

  override fun resume() = Unit
}
