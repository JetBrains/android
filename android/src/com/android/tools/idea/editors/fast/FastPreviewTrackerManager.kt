/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.fast

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.FastPreviewEvent
import com.intellij.openapi.project.Project

/**
 * FastPreview usage tracker.
 */
interface FastPreviewTrackerManager {
  companion object {
    fun getInstance(project: Project): FastPreviewTrackerManager =
      project.getService(FastPreviewTrackerManager::class.java)
  }

  /**
   * Tracks the lifetime of a single compilation request.
   */
  interface Request {
    fun daemonStartFailed()

    /**
     * Called when the compilation has failed. The failed compilation took [compilationDurationMs].
     */
    fun compilationFailed(compilationDurationMs: Long, compiledFiles: Int)

    /**
     * Called when the files were compiled successfully. The compilation took [compilationDurationMs]. Optionally, if [refreshTimeMs] is
     * not -1, it will be logged as the time that took to refresh the previews after compilation.
     */
    fun compilationSucceeded(compilationDurationMs: Long, compiledFiles: Int, refreshTimeMs: Long = -1)

    /**
     * Called when the fast preview refresh was cancelled, for example due to a new change in the code
     * that happened while a refresh was being done, or because the user manually cancelled it.
     * [compilationCompleted] indicates whether the cancellation happened during compilation or rendering.
     */
    fun refreshCancelled(compilationCompleted: Boolean)
  }

  /**
   * Called when the user has manually enabled the Fast Preview.
   */
  fun userEnabled()

  /**
   * Called when the user has manually disabled the Fast Preview.
   */
  fun userDisabled()

  /**
   * Called when Fast Preview has been automatically disabled because of an error.
   */
  fun autoDisabled()

  /**
   * Called when the daemon has failed to start.
   */
  fun daemonStartFailed()

  /**
   * Called to start a new tracking request. One of the three methods of [Request] must be called to log the request.
   */
  fun trackRequest(): Request
}

internal class FastPreviewTrackerManagerImpl(private val project: Project) : FastPreviewTrackerManager {
  private fun newStudioEvent(): AndroidStudioEvent.Builder =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.FAST_PREVIEW_EVENT)
      .withProjectId(project)


  override fun userEnabled() {
    UsageTracker.log(
      newStudioEvent()
        .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                               .setType(FastPreviewEvent.Type.USER_ENABLED))
    )
  }

  override fun userDisabled() {
    UsageTracker.log(
      newStudioEvent()
        .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                               .setType(FastPreviewEvent.Type.USER_DISABLED))
    )
  }

  override fun autoDisabled() {
    UsageTracker.log(
      newStudioEvent()
        .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                               .setType(FastPreviewEvent.Type.AUTO_DISABLED))
    )
  }

  override fun daemonStartFailed() {
    UsageTracker.log(
      newStudioEvent()
        .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                               .setType(FastPreviewEvent.Type.COMPILE)
                               .setCompilationResult(FastPreviewEvent.CompilationResult.newBuilder()
                                                       .setStatus(FastPreviewEvent.CompilationResult.Status.DAEMON_START_ERROR)))
    )
  }

  override fun trackRequest(): FastPreviewTrackerManager.Request = object : FastPreviewTrackerManager.Request {
    override fun daemonStartFailed() {
      this@FastPreviewTrackerManagerImpl.daemonStartFailed()
    }

    override fun compilationFailed(compilationDurationMs: Long, compiledFiles: Int) {
      UsageTracker.log(
        newStudioEvent()
          .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                                 .setType(FastPreviewEvent.Type.COMPILE)
                                 .setCompilationResult(FastPreviewEvent.CompilationResult.newBuilder()
                                                         .setStatus(FastPreviewEvent.CompilationResult.Status.FAILED)
                                                         .setCompileDurationMs(compilationDurationMs)
                                                         .setCompiledFiles(compiledFiles.toLong())))
      )
    }

    override fun compilationSucceeded(compilationDurationMs: Long, compiledFiles: Int, refreshTimeMs: Long) {
      UsageTracker.log(
        newStudioEvent()
          .setFastPreviewEvent(FastPreviewEvent.newBuilder()
                                 .setType(FastPreviewEvent.Type.COMPILE)
                                 .setCompilationResult(FastPreviewEvent.CompilationResult.newBuilder()
                                                         .setStatus(FastPreviewEvent.CompilationResult.Status.SUCCESS)
                                                         .setCompileDurationMs(compilationDurationMs)
                                                         .setCompiledFiles(compiledFiles.toLong()).also {
                                                           if (refreshTimeMs != -1L) it.refreshDurationMs = refreshTimeMs
                                                         })))
    }

    override fun refreshCancelled(compilationCompleted: Boolean) {
      // Do nothing. As this could happen quite frequently, we don't want to log it here.
    }
  }
}