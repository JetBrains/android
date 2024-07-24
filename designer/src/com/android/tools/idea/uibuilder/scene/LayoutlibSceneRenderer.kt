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
package com.android.tools.idea.uibuilder.scene

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// TODO(b/335424569): this class is meant to be used for extracting the rendering responsibilities
//   out of LayoutlibSceneManager. Add proper class description later.
internal class LayoutlibSceneRenderer(private val model: NlModel) {
  private val updateHierarchyLock = ReentrantLock()
  private val renderTaskLock = ReentrantLock()
  private val renderResultLock = ReentrantLock()

  /** If true, when a new RenderResult is an error it will retain the last successful image. */
  var cacheSuccessfulRenderImage = false

  // TODO(b/335424569): make this field private, or at least its setter
  @GuardedBy("renderTaskLock")
  var renderTask: RenderTask? = null
    get() = renderTaskLock.withLock { field }
    set(newTask) {
      val oldTask: RenderTask?
      renderTaskLock.withLock {
        oldTask = field
        // TODO(b/168445543): move session clock to RenderTask
        sessionClock = RealTimeSessionClock()
        field = newTask
      }
      try {
        oldTask?.dispose()
      } catch (t: Throwable) {
        Logger.getInstance(LayoutlibSceneManager::class.java).warn(t)
      }
    }

  // TODO(b/335424569): make this field private
  @GuardedBy("renderTaskLock")
  var sessionClock: SessionClock = RealTimeSessionClock()
    get() = renderTaskLock.withLock { field }
    private set

  // TODO(b/335424569): make this field private, or at least its setter
  var renderResult: RenderResult? = null
    get() = renderResultLock.withLock { field }
    set(newResult) {
      val oldResult: RenderResult?
      renderResultLock.withLock {
        if (field === newResult) return
        oldResult = field
        field =
          if (
            cacheSuccessfulRenderImage &&
              newResult.isErrorResult() &&
              oldResult.containsValidImage()
          ) {
            // newResult can not be null if isErrorResult is true
            // oldResult can not be null if containsValidImage is true
            newResult!!.copyWithNewImageAndRootViewDimensions(
              StudioRenderService.Companion.getInstance(newResult.project)
                .sharedImagePool
                .copyOf(oldResult!!.getRenderedImage().copy),
              oldResult.rootViewDimensions,
            )
          } else newResult
      }
      oldResult?.dispose()
    }

  private fun RenderResult?.containsValidImage() =
    this?.renderedImage?.let { it.width > 1 && it.height > 1 } ?: false

  // TODO(b/335424569): make this method private
  fun updateHierarchy(result: RenderResult?): Boolean {
    var reverseUpdate = false
    try {
      updateHierarchyLock.withLock {
        reverseUpdate =
          if (result == null || !result.renderResult.isSuccess) {
            NlModelHierarchyUpdater.updateHierarchy(emptyList<ViewInfo>(), model)
          } else {
            NlModelHierarchyUpdater.updateHierarchy(result, model)
          }
      }
    } catch (ignored: InterruptedException) {}
    return reverseUpdate
  }
}
