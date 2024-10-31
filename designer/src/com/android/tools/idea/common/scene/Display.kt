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
package com.android.tools.idea.common.scene

import com.android.annotations.TestOnly
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.UIUtil
import java.awt.Graphics2D
import java.awt.Rectangle
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/** [Display] that runs the layout and the creation of the display list in a background thread. */
private class AsyncDisplay(
  private val backgroundDispatcher: CoroutineDispatcher,
  private val captureRepaints: Boolean,
) : Display {
  private data class CachedState(
    val displayListVersion: Long,
    val displayList: DisplayList,
    val scale: Double,
  )

  /**
   * Limits the amount of parallel displays list being built to one. This dispatcher is used to run
   * the long operations in [Scene.buildDisplayList].
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private val listBuildingDispatcher = backgroundDispatcher.limitedParallelism(1)

  /** Cached state to decide a new display list is needed. */
  private var cachedState = CachedState(0, initialEmptyList, 0.0)

  /** Job for the current rebuilding task. It will be cancelled if a new rebuild is needed. */
  private val currentBuildDisplayListJob = AtomicReference<Job>(null)
  private val pendingRepaintsLock = ReentrantLock()

  /** List of scenes waiting for a display list rebuilt and repaint. */
  private val pendingRepaints: MutableSet<Scene> = Collections.newSetFromMap(WeakHashMap())

  /** Builds the [DisplayList] for the given [sceneContext] and [scene]. */
  private fun buildDisplayListAsync(sceneContext: SceneContext, scene: Scene): Job {
    if (captureRepaints) {
      pendingRepaintsLock.withLock { pendingRepaints.add(scene) }
    }
    return AndroidCoroutineScope(scene).launch(backgroundDispatcher) {
      val newDisplayList = DisplayList()
      // Scene is not coroutine aware. Both layout and buildDisplayList can potentially have
      // blocking calls that could block the coroutine. We dispatch those calls into a pooled
      // thread and continue afterward.
      runInterruptible(listBuildingDispatcher) {
        var needsAnotherRepaint: Boolean
        do {
          val time = System.currentTimeMillis()
          needsAnotherRepaint = scene.layout(time, sceneContext)
          sceneContext.time = time
          val sceneVersion = scene.displayListVersion
          scene.buildDisplayList(newDisplayList, time, sceneContext)
          cachedState = CachedState(sceneVersion, newDisplayList, sceneContext.scale)
          // Request the surface to be repainted so the new display list is painted
          UIUtil.invokeAndWaitIfNeeded { scene.repaint() }
        } while (needsAnotherRepaint)
      }
    }
  }

  override fun relayout() {
    cachedState = cachedState.copy(displayListVersion = 0)
  }

  @TestOnly
  override fun hasPendingPaints() = pendingRepaintsLock.withLock { !pendingRepaints.isEmpty() }

  @UiThread
  override fun draw(sceneContext: SceneContext, g: Graphics2D, scene: Scene) {
    val state = cachedState
    val needsRebuild =
      scene.displayListVersion > state.displayListVersion ||
        sceneContext.scale != state.scale ||
        state.displayList == initialEmptyList

    if (needsRebuild) {
      // Start a new rebuild and cancel the previous running one
      currentBuildDisplayListJob.getAndSet(buildDisplayListAsync(sceneContext, scene))?.cancel()
    } else {
      if (captureRepaints) {
        pendingRepaintsLock.withLock { pendingRepaints.remove(scene) }
      }
    }
    state.displayList.paint(g, sceneContext)
  }

  companion object {
    /**
     * The initial [DisplayList] used to check when the list has been built at least once.
     *
     * This list can not be modified in any way and will throw [IllegalStateException].
     */
    private val initialEmptyList =
      object : DisplayList() {
        override fun add(cmd: DrawCommand?) = throw IllegalStateException()

        override fun clear() = throw IllegalStateException()

        override fun pushClip(context: SceneContext, r: Rectangle?) = throw IllegalStateException()

        override fun popClip(): Boolean = throw IllegalStateException()
      }
  }
}

interface Display {
  @UiThread fun draw(sceneContext: SceneContext, g: Graphics2D, scene: Scene)

  fun relayout()

  @TestOnly fun hasPendingPaints(): Boolean

  companion object {
    private val asyncBackgroundDispatcher: CoroutineDispatcher =
      AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

    private var captureRepaints = AtomicBoolean(false)
    private val displays = WeakList<Display>()

    /**
     * Enables a mode for testing where [Display.hasPendingPaints] returns if there are any pending
     * repaints to be done. The method returns the previous value.
     */
    @TestOnly
    fun setCaptureDisplayRepaints(enable: Boolean): Boolean = captureRepaints.getAndSet(enable)

    /** Returns if there are any pending [Display] repaints. */
    @TestOnly
    fun hasPendingPaints(): Boolean {
      assert(captureRepaints.get()) { "setCaptureDisplayRepaints is not enabled" }
      return displays.any { it.hasPendingPaints() }
    }

    @JvmStatic
    fun create(): Display =
      (if (StudioFlags.NELE_BACKGROUND_DISPLAY_LIST.get())
          AsyncDisplay(asyncBackgroundDispatcher, captureRepaints.get())
        else SyncDisplay())
        .also {
          if (captureRepaints.get()) {
            displays.add(it)
          }
        }
  }
}
