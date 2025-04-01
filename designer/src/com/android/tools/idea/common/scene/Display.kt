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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.UIUtil
import java.awt.Graphics2D
import java.awt.Rectangle
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.jetbrains.annotations.TestOnly

/** [Display] that runs the layout and the creation of the display list in a background thread. */
private class AsyncDisplay(disposable: Disposable, private val captureRepaints: Boolean) : Display {
  private data class CachedState(
    val displayListVersion: Long,
    val displayList: DisplayList,
    val scale: Double,
  )

  private val rebuildTriggerFlow =
    MutableSharedFlow<SceneView>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val cachedStateFlow = MutableStateFlow(CachedState(0, initialEmptyList, 0.0))

  init {
    AndroidCoroutineScope(disposable).launch(Dispatchers.Default) {
      rebuildTriggerFlow.collectLatest { sceneView ->
        val scene = sceneView.scene
        val sceneContext = sceneView.context
        val newDisplayList = DisplayList()
        // Scene is not coroutine aware. Both layout and buildDisplayList can potentially have
        // blocking calls that could block the coroutine. We dispatch those calls into a pooled
        // thread and continue afterward.
        runInterruptible {
          var needsAnotherRepaint: Boolean
          do {
            val time = System.currentTimeMillis()
            needsAnotherRepaint = scene.layout(time, sceneContext)
            sceneContext.time = time
            val sceneVersion = scene.displayListVersion
            scene.buildDisplayList(newDisplayList, time, sceneContext)
            cachedStateFlow.value = CachedState(sceneVersion, newDisplayList, sceneContext.scale)
            // Request the surface to be repainted so the new display list is painted
            UIUtil.invokeAndWaitIfNeeded { scene.repaint() }
          } while (needsAnotherRepaint)
        }
      }
    }
  }

  private val pendingRepaintsLock = ReentrantLock()

  /** List of scenes waiting for a display list rebuilt and repaint. */
  private val pendingRepaints: MutableSet<Scene> = Collections.newSetFromMap(WeakHashMap())

  override fun relayout() {
    cachedStateFlow.value = cachedStateFlow.value.copy(displayListVersion = 0)
  }

  @TestOnly
  override fun hasPendingPaints() = pendingRepaintsLock.withLock { !pendingRepaints.isEmpty() }

  @UiThread
  override fun draw(sceneView: SceneView, g: Graphics2D) {
    val scene = sceneView.scene
    val sceneContext = sceneView.context
    val state = cachedStateFlow.value
    val needsRebuild =
      scene.displayListVersion > state.displayListVersion ||
        sceneContext.scale != state.scale ||
        state.displayList == initialEmptyList

    if (needsRebuild) {
      rebuildTriggerFlow.tryEmit(sceneView)
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
  @UiThread fun draw(sceneView: SceneView, g: Graphics2D)

  fun relayout()

  @TestOnly fun hasPendingPaints(): Boolean

  companion object {
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
    fun create(disposable: Disposable): Display =
      (if (StudioFlags.NELE_BACKGROUND_DISPLAY_LIST.get())
          AsyncDisplay(disposable, captureRepaints.get())
        else SyncDisplay())
        .also {
          if (captureRepaints.get()) {
            displays.add(it)
          }
        }
  }
}
