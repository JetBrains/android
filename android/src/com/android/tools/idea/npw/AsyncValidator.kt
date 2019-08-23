/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.annotations.concurrency.GuardedBy
import com.intellij.openapi.application.Application

/**
 * Simple utility that reruns task in another thread and reports the result back.
 *
 * The goal is to run validation in a non-UI thread. Some validation results
 * may be dropped as user input may change before validation completes.
 *
 * Some threading information:
 * invalidate()           is called by client from any thread to signal that
 * user input had changed and needs to be invalidated.
 * This will mark this validator as dirty and schedule
 * a runnable in the background thread if there is none
 * pending/running.
 *
 * validate()             will be called on a background thread to obtain
 * the validation result. This call can be repeated
 * if the data is marked dirty before a call to
 * validate() completes.
 *
 * showValidationResult() will be called on the UI thread with the data
 * returned by validate() called on a background thread.
 * Note that that data may be dropped if invalidate()
 * was called at some point while the UI thread task
 * was pending.
 */
abstract class AsyncValidator<V>(private val application: Application) {
  private val resultReporter = ResultReporter()
  @GuardedBy("this")
  private var isDirty = false
  @GuardedBy("this")
  private var isScheduled = false

  /**
   * Informs the validator that data had updated and validation status needs to be recomputed.
   *
   * Can be called on any thread.
   */
  @Synchronized
  fun invalidate() {
    isDirty = true
    resultReporter.setDirty()
    if (!isScheduled) {
      isScheduled = true
      application.executeOnPooledThread { revalidateUntilClean() }
    }
  }

  /**
   * Runs validation on the background thread, repeating it if it was reported that data was updated.
   */
  private fun revalidateUntilClean() {
    var result: V
    do {
      markClean()
      result = validate()
    }
    while (!submit(result))
  }

  /**
   * Submit will be canceled if the validator was marked dirty since we cleared the flag.
   */
  @Synchronized
  private fun submit(result: V): Boolean {
    isScheduled = isDirty
    if (!isScheduled) {
      resultReporter.report(result)
    }
    return !isScheduled
  }

  /**
   * Marks validator status clean, meaning the validation result is in sync
   * with user input.
   */
  @Synchronized
  private fun markClean() {
    isDirty = false
  }

  /**
   * Invoked on UI thread to show "stable" validation result in the UI.
   */
  protected abstract fun showValidationResult(result: V)

  /**
   * Invoked on a validation thread to perform long-running operation.
   */
  protected abstract fun validate(): V

  /**
   * Sent to main thread to report result of the background operation.
   */
  private inner class ResultReporter : Runnable {
    @GuardedBy("this")
    private var result: V? = null
    @GuardedBy("this")
    private var isPending = false

    @Synchronized
    fun report(value: V) {
      result = value
      if (!isPending) {
        isPending = true
        application.invokeLater(this, application.anyModalityState)
      }
    }

    @Synchronized
    override fun run() {
      val savedResult = result
      result = null
      try {
        if (savedResult != null) {
          showValidationResult(savedResult)
        }
      }
      finally {
        isPending = false
      }
    }

    @Synchronized
    fun setDirty() {
      result = null
    }
  }
}