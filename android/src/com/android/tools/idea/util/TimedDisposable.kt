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
package com.android.tools.idea.util

import com.android.annotations.concurrency.GuardedBy
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

/**
 * A wrapper class around a given object that **must** implement the [Disposable] interface.
 * This object will be disposed after the given [timeout] elapsed or the parentDisposable is disposed
 *
 * The timer starts as soon as the this instance is created and is reset after each call
 * to [get].
 *
 * Once the timer as expired, the [Disposable] will be disposed and subsequent call to [get] will return null.
 *
 * @param parentDisposable The parent [Disposable] that will trigger the disposal of the given object it is disposed
 * before the end of the timeout.
 */
class TimedDisposable<out T : Disposable>(disposable: T,
                                          parentDisposable: Disposable,
                                          private val timeout: Long,
                                          private val timeUnit: TimeUnit
) : Disposable {

  @GuardedBy("lock")
  private var isDisposed = false

  @GuardedBy("lock")
  private var delegatedDisposable: T? = disposable

  @GuardedBy("lock")
  private var timer: Timer? = Timer()

  @GuardedBy("lock")
  private var disposeTask: TimerTask? = null

  private val lock = Any()

  init {
      Disposer.register(parentDisposable, this)
      Disposer.register(this, delegatedDisposable!!)
      scheduleDispose()
  }

  /**
   * Returns the disposable object given in the constructor or null if it has already been disposed.
   */
  fun get(): T? {
    synchronized(lock) {
      if (!isDisposed) {
        scheduleDispose()
      }
    }
    @Suppress("UNCHECKED_CAST")
    return delegatedDisposable
  }

  /**
   * Cancel any pending scheduled disposal and reschedule a new one to be executed
   * after the given time.
   */
  @GuardedBy("lock")
  private fun scheduleDispose() {
    disposeTask?.cancel()
    timer!!.purge()
    disposeTask = timer!!.schedule(timeUnit.toMillis(timeout)) { Disposer.dispose(this@TimedDisposable) }
  }

  override fun dispose() {
    synchronized(lock) {
      if (isDisposed) {
        assert(delegatedDisposable == null)
        assert(disposeTask == null)
      }
      else {
        isDisposed = true
        delegatedDisposable = null
        disposeTask = null
        timer!!.cancel()
        timer!!.purge()
        timer = null
      }
    }
  }
}