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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SourceGenerationCallback
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ThreeState
import org.jetbrains.annotations.Contract

class GradleProjectSystemSyncManager(val project: Project) : ProjectSystemSyncManager {
  private val sourceGenerationLock = Any()
  private enum class InitialSourceGenerationStatus { IN_PROGRESS, ERROR, SOURCES_GENERATED }

  @GuardedBy("sourceGenerationLock")
  private var initialSourceGenerationStatus = InitialSourceGenerationStatus.IN_PROGRESS

  private var lastSyncResult: SyncResult = SyncResult.UNKNOWN
  private val sourceGenerationCallbacks = mutableListOf<SourceGenerationCallback>()

  init {
    // Listen for syncs to finish and update lastSyncResult accordingly.
    project.messageBus.connect(project).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : SyncResultListener {
      override fun syncEnded(result: SyncResult) {
        lastSyncResult = result

        if (!result.isSuccessful) {
          notifySourceGenerationErrorOccurred()
        }
      }
    })

    // The message bus connection we use for listening for build results only lasts until either
    // the first successful build completes or the project is disposed, whichever comes first.
    val buildListenerDisposable = Disposer.newDisposable()

    // Listen for build results. After the first successful build, sources will have been generated.
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        if (status == BuildStatus.SUCCESS || status == BuildStatus.SKIPPED) {
          notifySourcesGenerated()
          Disposer.dispose(buildListenerDisposable)
        }
        else {
          notifySourceGenerationErrorOccurred()
        }
      }
    }, buildListenerDisposable)

    Disposer.register(project, Disposable {
      if (!Disposer.isDisposed(buildListenerDisposable)) {
        Disposer.dispose(buildListenerDisposable)
      }
    })
  }

  private fun notifySourcesGenerated() {
    synchronized(sourceGenerationLock) {
      initialSourceGenerationStatus = InitialSourceGenerationStatus.SOURCES_GENERATED
    }

    sourceGenerationCallbacks.forEach { it.sourcesGenerated() }
    sourceGenerationCallbacks.clear()
  }

  private fun notifySourceGenerationErrorOccurred() {
    val toExecute = mutableListOf<SourceGenerationCallback>()

    synchronized(sourceGenerationLock) {
      if (initialSourceGenerationStatus == InitialSourceGenerationStatus.IN_PROGRESS) {
        initialSourceGenerationStatus = InitialSourceGenerationStatus.ERROR
        toExecute.addAll(sourceGenerationCallbacks)
      }
    }

    toExecute.forEach { it.sourceGenerationError() }
  }

  override fun addSourceGenerationCallback(callback: SourceGenerationCallback) {
    val status = synchronized(sourceGenerationLock) {
      if (initialSourceGenerationStatus != InitialSourceGenerationStatus.SOURCES_GENERATED) {
        sourceGenerationCallbacks.add(callback)
      }

      initialSourceGenerationStatus
    }

    // If source generation has already completed, ignore any previous errors.
    if (status == InitialSourceGenerationStatus.SOURCES_GENERATED) {
      callback.sourcesGenerated()
    }
    else if (status == InitialSourceGenerationStatus.ERROR) {
      callback.sourceGenerationError()
    }
  }

  @Contract(pure = true)
  private fun convertReasonToTrigger(reason: SyncReason): GradleSyncStats.Trigger = when {
      reason === SyncReason.PROJECT_LOADED -> GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED
      reason === SyncReason.PROJECT_MODIFIED -> GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
      else -> GradleSyncStats.Trigger.TRIGGER_USER_REQUEST
  }

  private fun requestSync(project: Project, reason: SyncReason, requireSourceGeneration: Boolean): ListenableFuture<SyncResult> {
    val trigger = convertReasonToTrigger(reason)
    val syncResult = SettableFuture.create<SyncResult>()

    val listener = object : GradleSyncListener.Adapter() {
      override fun syncSucceeded(project: Project) {
        syncResult.set(SyncResult.SUCCESS)
      }

      override fun syncFailed(project: Project, errorMessage: String) {
        syncResult.set(SyncResult.FAILURE)
      }

      override fun syncSkipped(project: Project) {
        syncResult.set(SyncResult.SKIPPED)
      }
    }

    val request = GradleSyncInvoker.Request(trigger)
    request.generateSourcesOnSuccess = requireSourceGeneration
    request.runInBackground = true

    try {
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener)
    }
    catch (t: Throwable) {
      syncResult.setException(t)
    }

    return syncResult
  }

  override fun syncProject(reason: SyncReason, requireSourceGeneration: Boolean): ListenableFuture<SyncResult> {
    val syncResult = SettableFuture.create<SyncResult>()

    when {
      GradleSyncState.getInstance(project).isSyncInProgress -> syncResult.setException(RuntimeException("A sync was requested while one is"
          + " already in progress. Use ProjectSystemSyncManager.isSyncInProgress to detect this scenario."))

      project.isInitialized -> syncResult.setFuture(requestSync(project, reason, requireSourceGeneration))

      else -> StartupManager.getInstance(project).runWhenProjectIsInitialized {
        if (!GradleProjectInfo.getInstance(project).isImportedProject) {
          // http://b/62543184
          // If the project was created with the "New Project" wizard, there is no need to sync again.
          syncResult.setFuture(requestSync(project, reason, requireSourceGeneration))
        }
        else {
          syncResult.set(SyncResult.SKIPPED)
        }
      }
    }

    return syncResult
  }

  override fun isSyncInProgress() = GradleSyncState.getInstance(project).isSyncInProgress
  override fun isSyncNeeded() = GradleSyncState.getInstance(project).isSyncNeeded != ThreeState.NO
  override fun getLastSyncResult() = lastSyncResult
}