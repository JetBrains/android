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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

/**
 * A [SyncWithSourceGenerationListener] is a sync listener which only considers syncs with source generation to be finished after
 * the source generation step is complete. Implementing classes are responsible for ensuring that instances
 * of [SyncWithSourceGenerationListener] are correctly subscribed to the appropriate sync and build listening channels.
 */
abstract class SyncWithSourceGenerationListener : GradleSyncListener, GradleBuildListener.Adapter() {
  /**
   * This field keeps track of whether source generation was requested for the last sync and whether or not that source generation
   * has already completed.
   * It is null if source generation was not requested, and if source generation was requested, sourceGenerationFuture.isDone indicates
   * whether or not the source generation task has completed.
   *
   * This gives us a way of connecting the "source generation requested" attribute of a sync request with the source generation
   * that is obtained from a successful build, and allows us to ignore builds that are unrelated to the last sync.
   */
  private var sourceGenerationFuture: CompletableFuture<ProjectSystemSyncManager.SyncResult>? = null

  abstract fun syncFinished(sourceGenerationRequested: Boolean, result: ProjectSystemSyncManager.SyncResult)

  override fun syncTaskCreated(project: Project, request: GradleSyncInvoker.Request) {
  }

  override fun syncStarted(project: Project, sourceGenerationRequested: Boolean) {
    sourceGenerationFuture = if (sourceGenerationRequested) CompletableFuture() else null
  }

  override fun setupStarted(project: Project) {}

  override fun syncSucceeded(project: Project) = syncFinished(project, ProjectSystemSyncManager.SyncResult.SUCCESS)

  override fun syncFailed(project: Project, errorMessage: String) = syncFinished(project, ProjectSystemSyncManager.SyncResult.FAILURE)

  override fun syncSkipped(project: Project) = syncFinished(project, ProjectSystemSyncManager.SyncResult.SKIPPED)

  private fun syncFinished(project: Project, syncResult: ProjectSystemSyncManager.SyncResult) {
    val sourceGenerationRequested = sourceGenerationFuture != null
    if (!sourceGenerationRequested || !syncResult.isSuccessful) {
      if (!project.isDisposed) syncFinished(sourceGenerationRequested, syncResult)
    }
    else {
      sourceGenerationFuture?.whenComplete { result, _ -> if (!project.isDisposed) syncFinished(sourceGenerationRequested, result) }
    }
  }

  override fun sourceGenerationFinished(project: Project) {
    // With compound sync, the source generation result is always success, otherwise syncFailed would be called.
    sourceGenerationFuture?.complete(ProjectSystemSyncManager.SyncResult.SUCCESS)
  }

  override fun buildFinished(status: BuildStatus, context: BuildContext?) {
    val result = when (status) {
      BuildStatus.CANCELED -> ProjectSystemSyncManager.SyncResult.CANCELLED
      BuildStatus.FAILED   -> ProjectSystemSyncManager.SyncResult.SOURCE_GENERATION_FAILURE
      BuildStatus.SKIPPED  -> ProjectSystemSyncManager.SyncResult.SKIPPED
      BuildStatus.SUCCESS  -> ProjectSystemSyncManager.SyncResult.SUCCESS
    }
    sourceGenerationFuture?.complete(result)
  }

  /**
   * @return true if source generation is requested and not completed yet.
   */
  fun isSourceGenerationInProgress(): Boolean = sourceGenerationFuture?.isDone == false
}