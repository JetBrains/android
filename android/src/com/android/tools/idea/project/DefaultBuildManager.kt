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
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildResult
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile

object DefaultBuildManager : ProjectSystemBuildManager {
  private var lastResult = BuildResult(BuildMode.UNKNOWN, BuildStatus.UNKNOWN, System.currentTimeMillis())

  override fun compileProject() {
    lastResult = BuildResult(BuildMode.COMPILE, BuildStatus.SUCCESS, System.currentTimeMillis())
  }

  override fun compileFilesAndDependencies(files: Collection<VirtualFile>) {
    lastResult = BuildResult(BuildMode.COMPILE, BuildStatus.SUCCESS, System.currentTimeMillis())
  }

  override fun getLastBuildResult(): BuildResult = lastResult


  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) {}

  @get:UiThread
  override val isBuilding = false
}