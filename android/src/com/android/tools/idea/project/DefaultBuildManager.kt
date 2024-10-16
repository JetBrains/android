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
package com.android.tools.idea.project

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildResult
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

object DefaultBuildManager : ProjectSystemBuildManager {
  private val listeners = mutableListOf<ProjectSystemBuildManager.BuildListener>()
  private var lastResult = BuildResult(BuildMode.UNKNOWN, BuildStatus.UNKNOWN)

  private fun buildStarted(mode: BuildMode) {
    isBuilding = true
    // use a copy to avoid concurrent modification
    val listeners = listeners.toList()
    listeners.forEach { it.buildStarted(mode) }
  }

  private fun buildCompleted(mode: BuildMode, status: ProjectSystemBuildManager.BuildStatus) {
    lastResult = BuildResult(BuildMode.COMPILE_OR_ASSEMBLE, BuildStatus.SUCCESS)
    // use a copy to avoid concurrent modification
    val listeners = listeners.toList()
    listeners.forEach { it.beforeBuildCompleted(lastResult) }
    isBuilding = false
    listeners.forEach { it.buildCompleted(lastResult) }
  }

  override fun compileProject() {
    buildStarted(BuildMode.COMPILE_OR_ASSEMBLE)
    buildCompleted(BuildMode.COMPILE_OR_ASSEMBLE, BuildStatus.SUCCESS)
  }

  override fun getLastBuildResult(): BuildResult = lastResult

  override fun addBuildListener(
    parentDisposable: Disposable,
    buildListener: ProjectSystemBuildManager.BuildListener,
  ) {
    listeners.add(buildListener)
    Disposer.register(parentDisposable) { listeners.remove(buildListener) }
  }

  @get:UiThread override var isBuilding = false
}
