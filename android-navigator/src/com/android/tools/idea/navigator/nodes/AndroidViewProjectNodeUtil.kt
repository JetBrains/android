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
@file:JvmName("AndroidViewProjectNodeUtil")
package com.android.tools.idea.navigator.nodes

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun ProjectSystemSyncManager.maybeWaitForAnySyncOutcomeInterruptibly() {
  if (getLastSyncResult() == ProjectSystemSyncManager.SyncResult.UNKNOWN) {
    waitForAnySyncOutcomeInterruptibly()
  }
}

private fun ProjectSystemSyncManager.waitForAnySyncOutcomeInterruptibly() {
  val lock = ReentrantLock()
  val condVar = lock.newCondition()
  val application = ApplicationManagerEx.getApplicationEx()

  val listener = object : ApplicationListener {
    override fun beforeWriteActionStart(action: Any) {
      lock.withLock {
        condVar.signal() // Interrupt waiting to give priority to write actions as we are holding the read lock.
      }
    }
  }

  fun withListener(action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    ApplicationManager.getApplication().addApplicationListener(listener, disposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  withListener {
    lock.withLock {
      while (getLastSyncResult() == ProjectSystemSyncManager.SyncResult.UNKNOWN) {
        ProgressManager.checkCanceled() // Give priority to write actions as we are holding the read lock.
        // For unknown reasons the statement above does not always throw `ProcessCanceledException` when a write action is pending even
        // though it is running via `NonBlockingReadAction`. See b/171914220.
        if (application.isWriteActionPending) throw ProcessCanceledException()
        condVar.await(50, TimeUnit.MILLISECONDS) // We are *polling* sync status.
      }
    }
  }
}

