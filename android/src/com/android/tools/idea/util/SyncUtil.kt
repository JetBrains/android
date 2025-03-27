/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("SyncUtil")

package com.android.tools.idea.util

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.getSyncManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.util.function.Consumer

private val LOG: Logger get() = Logger.getInstance("SyncUtil.kt")

/**
 * Registers [listener] to be notified of any sync result broadcast on [PROJECT_SYSTEM_SYNC_TOPIC] on [project]'s message bus
 * until the next sync completes. The [listener] maintains its subscription to [PROJECT_SYSTEM_SYNC_TOPIC] until either
 *
 * 1) a sync completes and [listener] is notified, or
 * 2) [parentDisposable] is disposed
 */
@JvmOverloads
fun Project.listenUntilNextSync(parentDisposable: Disposable = this, listener: SyncResultListener) {
  messageBus.connect(parentDisposable).apply {
    subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : SyncResultListener {
      override fun syncEnded(result: SyncResult) {
        disconnect()
        listener.syncEnded(result)
      }
    })
  }
}

/**
 * Runs the given [callback] when the project is smart and synced.
 * @param parentDisposable [Disposable] used to track the current request. If this parent [Disposable] is disposed
 *  then the callback will never be called.
 * @param callback callback that receives the result of the sync operation and will only run once we are in Smart Mode
 *  and the sync has completed.
 * @param runOnEdt indicates whether the callback must run on the UI thread
 * @param syncManager optional [ProjectSystemSyncManager] for testing
 */
@JvmOverloads
fun Project.runWhenSmartAndSynced(parentDisposable: Disposable = this,
                                  callback: Consumer<SyncResult>,
                                  runOnEdt: Boolean = false,
                                  syncManager: ProjectSystemSyncManager = this.getSyncManager()) {

  // Because this might run at some point in the future, we need to check if the parent disposable was already disposed to avoid
  // causing leaks and exceptions.
  if (Disposer.isDisposed(parentDisposable)) {
    LOG.warn("parentDisposable was already disposed, callback will not be called.")
    return
  }

  val dumbService = DumbService.getInstance(this)
  LOG.debug { "runWhenSmartAndSynced isDumb=${dumbService.isDumb} runOnEdt=${runOnEdt} callback=${callback}" }
  if (dumbService.isDumb) {
    if (runOnEdt) {
      dumbService.smartInvokeLater { runWhenSmartAndSynced(parentDisposable, callback, runOnEdt, syncManager) }
    }
    else {
      dumbService.runWhenSmart { runWhenSmartAndSynced(parentDisposable, callback, runOnEdt, syncManager) }
    }
    return
  }


  if (syncManager.isSyncInProgress()) {
    LOG.debug { "runWhenSmartAndSynced waiting for sync callback=${callback}" }
    listenUntilNextSync(parentDisposable) { runWhenSmartAndSynced(parentDisposable, callback, runOnEdt, syncManager) }
    return
  }

  val lastSyncResult = syncManager.getLastSyncResult()
  // This is a workaround for b/299881938:
  // This change will listen until the next sync if the last sync result state is of type UNKNOWN.
  // When opening a project the sync status could be of UNKNOWN type and the code/split/design panes aren't shown.
  // The UNKNOWN state is the very first status when a project is opened. When on this status, the sync of the project has not started yet.
  // After this method is called with UNKNOWN, it's triggered again by other sync listeners while the status will be SKIPPED,
  // i.e. the project state will be loaded from a cache.
  // If the callback is accepted with the SKIPPED status, the code/split/design panes will be shown as expected.
  if (lastSyncResult == SyncResult.UNKNOWN) {
    LOG.debug { "last sync result is in unknown state=${callback}" }
    listenUntilNextSync(parentDisposable) { runWhenSmartAndSynced(parentDisposable, callback, runOnEdt, syncManager) }
    return
  }

  if (runOnEdt && !ApplicationManager.getApplication().isDispatchThread) {
    LOG.debug { "runWhenSmartAndSynced needs EDT callback=${callback}" }
    UIUtil.invokeLaterIfNeeded { runWhenSmartAndSynced(parentDisposable, callback, runOnEdt, syncManager) }
    return
  }

  if (Disposer.isDisposed(parentDisposable)) {
    LOG.warn("parentDisposable was already disposed, callback will not be called.")
    return
  }
  LOG.debug { "runWhenSmartAndSynced all conditions met callback=${callback}" }
  callback.accept(lastSyncResult)
}

/**
 * Runs the given [callback] on the EDT when the project is smart and synced.
 * @param parentDisposable [Disposable] used to track the current request. If this parent [Disposable] is disposed
 *  then the callback will never be called.
 * @param callback callback that receives the result of the sync operation and will only run once we are in Smart Mode
 *  and the sync has completed.
 * @param syncManager optional [ProjectSystemSyncManager] for testing.
 */
@JvmOverloads
fun Project.runWhenSmartAndSyncedOnEdt(parentDisposable: Disposable = this,
                                       callback: Consumer<SyncResult>,
                                       syncManager: ProjectSystemSyncManager = this.getSyncManager()) {
  runWhenSmartAndSynced(parentDisposable, callback, true, syncManager)
}