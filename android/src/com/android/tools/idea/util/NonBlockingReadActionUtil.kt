/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.util

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Waits for this future to become `isDone` and returns the value of the future (or re-throws the exception). Wait is automatically
 * interrupted by pending write actions, in which case a [ProcessCanceledException] is thrown.
 *
 * This method is supposed to be called from a non-blocking read action which retries execution of its body on [ProcessCanceledException]'s.
 */
@Throws(InterruptedException::class, ExecutionException::class)
fun <T> CompletableFuture<T>.waitInterruptibly(): T {
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
    } finally {
      Disposer.dispose(disposable)
    }
  }

  this.handle { _, _ ->
    lock.withLock {
      condVar.signal()
    }
  }

  try {
    withListener {
      lock.withLock {
        while (!this.isDone) {
          ProgressManager.checkCanceled() // Give priority to write actions as we are holding the read lock.
          // For unknown reasons the statement above does not always throw `ProcessCanceledException` when a write action is pending even
          // though it is running via `NonBlockingReadAction`. See b/171914220.
          if (application.isWriteActionPending) {
            // The indicator needs to be cancelled to avoid ProcessCancelledException being treated as an error.
            ProgressManager.getGlobalProgressIndicator()?.cancel()
              ?: error("waitInterruptibly() is supposed to be called under a progress indicator")
            ProgressManager.checkCanceled()
            throw ProcessCanceledException() // We don't know why we received reports of `ProgressManager.checkCanceled()` not interrupting
                                             // execution when a write action is pending. Make sure if it happened again we still throw,
                                             // and it will break our test.
          }
          condVar.await(50, TimeUnit.MILLISECONDS) // In case of any misbehaving code still check for cancellation and done status.
        }
      }
    }
    return get()
  } catch (cancelled: ProcessCanceledException) {
    cancel(true)
    throw cancelled
  }
}

