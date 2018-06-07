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
package com.android.tools.idea.gradle.project.sync.projectsystem

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.project.Project

/**
 * A [SyncWithSourceGenerationListener] is a sync listener which only considers syncs with source generation to be finished after
 * the source generation step is complete. Implementing classes are responsible for ensuring that instances
 * of [SyncWithSourceGenerationListener] are correctly subscribed to the appropriate sync and build listening channels.
 */
abstract class SyncWithSourceGenerationListener : GradleSyncListener, GradleBuildListener.Adapter() {
  /**
   * This field keeps track of whether source generation was requested for the last sync and whether or not that source generation
   * has already completed.
   *
   * This gives us a way of connecting the "source generation requested" attribute of a sync request with the source generation
   * that is obtained from a successful build, and allows us to ignore builds that are unrelated to the last sync.
   */
  var sourceGenerationExpected = false
    private set

  abstract fun syncFinished(sourceGenerationRequested: Boolean, result: ProjectSystemSyncManager.SyncResult)

  override fun syncStarted(project: Project, skipped: Boolean, sourceGenerationRequested: Boolean) {
    sourceGenerationExpected = sourceGenerationRequested
  }

  override fun setupStarted(project: Project) { }

  override fun syncSucceeded(project: Project) {
    if (!sourceGenerationExpected) {
      syncFinished(false, ProjectSystemSyncManager.SyncResult.SUCCESS)
    }
  }

  override fun syncFailed(project: Project, errorMessage: String) {
    val sourceGenerationRequested = sourceGenerationExpected

    sourceGenerationExpected = false
    syncFinished(sourceGenerationRequested, ProjectSystemSyncManager.SyncResult.FAILURE)
  }

  override fun syncSkipped(project: Project) {
    if (!sourceGenerationExpected) {
      syncFinished(false, ProjectSystemSyncManager.SyncResult.SKIPPED)
    }
  }

  override fun buildFinished(status: BuildStatus, context: BuildContext?) {
    if (sourceGenerationExpected) {
      sourceGenerationExpected = false

      val result = when(status) {
        BuildStatus.CANCELED -> ProjectSystemSyncManager.SyncResult.CANCELLED
        BuildStatus.FAILED   -> ProjectSystemSyncManager.SyncResult.SOURCE_GENERATION_FAILURE
        BuildStatus.SKIPPED  -> ProjectSystemSyncManager.SyncResult.SKIPPED
        BuildStatus.SUCCESS  -> ProjectSystemSyncManager.SyncResult.SUCCESS
      }

      syncFinished(true, result)
    }
  }
}