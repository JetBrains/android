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
package com.android.tools.idea.testing

import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerHook
import com.intellij.openapi.application.ApplicationManager
import javax.swing.SwingUtilities

class ThreadingCheckerHookTestImpl : ThreadingCheckerHook {
  var hasPerformedThreadingChecks = false
    private set

  var hasThreadingViolation = false
    private set

  var errorMessage = ""
    private set

  override fun verifyOnUiThread() {
    hasPerformedThreadingChecks = true
    if (!SwingUtilities.isEventDispatchThread()) {
      hasThreadingViolation = true
      errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called on EventDispatchThread."
    }
  }

  override fun verifyOnWorkerThread() {
    hasPerformedThreadingChecks = true
    if (SwingUtilities.isEventDispatchThread()) {
      hasThreadingViolation = true
      errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called on a worker thread."
    }
  }

  override fun verifyReadLock() {
    hasPerformedThreadingChecks = true
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      hasThreadingViolation = true
      errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called with a read lock"
    }
  }

  override fun verifyWriteLock() {
    hasPerformedThreadingChecks = true
    if (!ApplicationManager.getApplication().isWriteAccessAllowed) {
      hasThreadingViolation = true
      errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called with a write lock"
    }
  }

  override fun verifyNoReadLock() {
    hasPerformedThreadingChecks = true
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      hasThreadingViolation = true
      errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called without a read lock"
    }
  }

  private fun getInstrumentedMethodName(): String {
    // Instrumented method (method annotated with @WorkerThread/@UiThread) is located 4 frames up the stack trace
    return Thread.currentThread().stackTrace[4].toString()
  }
}
