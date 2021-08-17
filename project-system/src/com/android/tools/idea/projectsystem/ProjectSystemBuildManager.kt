/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("ProjectSystemSyncUtil")

package com.android.tools.idea.projectsystem

import com.android.annotations.concurrency.AnyThread
import com.intellij.openapi.Disposable

/**
 * Provides a build-system-agnostic interface for triggering, responding to, and gathering information about project builds.
 */
interface ProjectSystemBuildManager {
  /**
   * Initiates an incremental compilation of the entire project. Blocks the caller until it finishes.
   */
  fun compileProject()

  fun getLastBuildResult(): BuildResult

  /**
   * Adds a new [BuildListener]. The listener will stop being notified when [parentDisposable] is disposed.
   */
  fun addBuildListener(parentDisposable: Disposable, buildListener: BuildListener)

  enum class BuildStatus {
    UNKNOWN, SUCCESS, FAILED, CANCELLED
  }

  enum class BuildMode {
    UNKNOWN,

    /**
     * Assemble will generate the artifacts needed to execute the code on device, like APKs. It's a superset of [COMPILE].
     */
    ASSEMBLE,

    /**
     * When a build in this mode is run, all the sources are compiled into the class outputs.
     */
    COMPILE,

    /**
     * Clean will remove all the compiled artifacts from the project. It will remove class files and other artifacts generated
     * by any of the other modes.
     */
    CLEAN
  }

  data class BuildResult(val mode: BuildMode, val status: BuildStatus, val timestampMillis: Long) {
    companion object {
      @JvmStatic
      fun createUnknownBuildResult(): BuildResult =
        BuildResult(BuildMode.UNKNOWN, BuildStatus.UNKNOWN, System.currentTimeMillis())
    }
  }

  /** Listener which provides a callback for when build complete */
  interface BuildListener {
    /** Listener called when a build is started. */
    @AnyThread
    fun buildStarted(mode: BuildMode) {}

    /**
     * Method called after a build finishes successfully but before the [buildCompleted] listeners. This method can be used to clear
     * any caches before [buildCompleted] has all the results available.
     */
    @AnyThread
    fun beforeBuildCompleted(result: BuildResult) {}

    /** Method called after a build finishes. */
    @AnyThread
    fun buildCompleted(result: BuildResult) {}
  }
}