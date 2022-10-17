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

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ThreeState

class GradleProjectSystemSyncManager(val project: Project) : ProjectSystemSyncManager {
  private fun requestSync(project: Project, reason: SyncReason): ListenableFuture<SyncResult> {
    val trigger = reason.forStats
    val syncResult = SettableFuture.create<SyncResult>()

    // Listen for the next sync result.
    val connection = project.messageBus.connect().apply {
      subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : SyncResultListener {
        override fun syncEnded(result: SyncResult) {
          disconnect()
          syncResult.set(result)
        }
      })
    }

    try {
      GradleSyncInvoker.getInstance().requestProjectSync(project, trigger)
    } catch (t: Throwable) {
      if (!Disposer.isDisposed(connection)) {
        connection.disconnect()
      }

      syncResult.setException(t)
    }

    return syncResult
  }

  override fun syncProject(reason: SyncReason): ListenableFuture<SyncResult> {
    val syncResult = SettableFuture.create<SyncResult>()

    when {
      isSyncInProgress() -> syncResult.setException(RuntimeException("A sync was requested while one is"
          + " already in progress. Use ProjectSystemSyncManager.isSyncInProgress to detect this scenario."))

      project.isInitialized -> syncResult.setFuture(requestSync(project, reason))
      else -> StartupManager.getInstance(project).runWhenProjectIsInitialized {
          syncResult.setFuture(requestSync(project, reason))
      }
    }

    return syncResult
  }

  override fun isSyncInProgress() = GradleSyncState.getInstance(project).isSyncInProgress
  override fun isSyncNeeded() = GradleSyncState.getInstance(project).isSyncNeeded() != ThreeState.NO
  override fun getLastSyncResult() = GradleSyncStateHolder.getInstance(project).syncResult
}