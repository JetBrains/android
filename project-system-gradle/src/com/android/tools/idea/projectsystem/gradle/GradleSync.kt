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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import org.jetbrains.annotations.Contract

@Contract(pure = true)
private fun convertReasonToTrigger(reason: AndroidProjectSystem.SyncReason): GradleSyncStats.Trigger {
  return when {
    reason === AndroidProjectSystem.SyncReason.PROJECT_LOADED -> GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED
    reason === AndroidProjectSystem.SyncReason.PROJECT_MODIFIED -> GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
    else -> GradleSyncStats.Trigger.TRIGGER_USER_REQUEST
  }
}

internal fun requestSync(project: Project, reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
  val trigger = convertReasonToTrigger(reason)
  val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

  val listener = object : GradleSyncListener.Adapter() {
    override fun syncSucceeded(project: Project) {
      syncResult.set(AndroidProjectSystem.SyncResult.SUCCESS)
    }

    override fun syncFailed(project: Project, errorMessage: String) {
      syncResult.set(AndroidProjectSystem.SyncResult.FAILURE)
    }

    override fun syncSkipped(project: Project) {
      syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
    }
  }

  val request = GradleSyncInvoker.Request().setTrigger(trigger)
      .setGenerateSourcesOnSuccess(requireSourceGeneration).setRunInBackground(true)

  if (GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
    request.setNewOrImportedProject()
  }

  try {
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener)
  }
  catch (t: Throwable) {
    syncResult.setException(t)
  }

  return syncResult
}

internal fun syncProject(project: Project, reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
  val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

  if (GradleSyncState.getInstance(project).isSyncInProgress) {
    syncResult.setException(RuntimeException("A sync was requested while one is already in progress. Use"
        + "GradleSyncState.isSyncInProgress to detect this scenario."))
  }
  else if (project.isInitialized) {
    syncResult.setFuture(requestSync(project, reason, requireSourceGeneration))

  }
  else {
    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      if (!GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
        // http://b/62543184
        // If the project was created with the "New Project" wizard, there is no need to sync again.
        syncResult.setFuture(requestSync(project, reason, requireSourceGeneration))
      }
      else {
        syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
      }
    }
  }

  return syncResult
}
