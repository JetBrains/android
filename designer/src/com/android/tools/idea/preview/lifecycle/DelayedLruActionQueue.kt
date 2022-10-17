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
package com.android.tools.idea.preview.lifecycle

import com.android.ide.common.util.Cancelable
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.ArrayDeque
import java.util.WeakHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An actions queue that will execute them once they get out of the LRU [maxLruPlaces] or after the given [delay].
 */
class DelayedLruActionQueue(private val maxLruPlaces: Int,
                            private val delay: Duration,
                            private val scheduledExecutorService: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService(
                              "DelayedLruActionQueue", 1)) {
  private val queueLock = ReentrantLock()
  private val lruQueue = ArrayDeque<() -> Unit>(maxLruPlaces)
  private val actionToDisposable = WeakHashMap<() -> Unit, Disposable>()
  private val actionToScheduledFuture = WeakHashMap<() -> Unit, ScheduledFuture<*>>()


  @TestOnly
  fun queueSize(): Int {
    queueLock.withLock {
      val queueSize = lruQueue.size
      assert(queueSize == actionToDisposable.size) {
        "actionToDisposable size must always match the size of the lruQueue"
      }
      return queueSize
    }
  }

  private fun addActionToQueue(action: () -> Unit, actionDisposable: Disposable, scheduledFuture: ScheduledFuture<*>) {
    queueLock.withLock {
      if (actionToDisposable.contains(action)) {
        // Do not schedule the same action twice, just put at the back of the queue.
        lruQueue.add(lruQueue.remove())
        actionToScheduledFuture[action]?.cancel(false)
        actionToScheduledFuture[action] = scheduledFuture
        return
      }

      if (lruQueue.size == maxLruPlaces) {
        val removedAction = lruQueue.remove()
        actionToDisposable.remove(removedAction)
        actionToScheduledFuture.remove(removedAction)
        removedAction()
      }

      lruQueue.add(action)
      actionToDisposable[action] = actionDisposable
      actionToScheduledFuture[action] = scheduledFuture
    }
  }

  private fun removeActionFromQueue(action: (() -> Unit)?): Boolean {
    if (action == null) return false
    queueLock.withLock {
      val actionWasStillInTheQueue = lruQueue.remove(action)
      actionToScheduledFuture.remove(action)?.cancel(false)
      actionToDisposable.remove(action)?.let {
        Disposer.dispose(it)
      }
      return actionWasStillInTheQueue
    }
  }

  /**
   * Adds the given [action] to the queue. It will execute automatically after [delay] has passed or if the number of actions in the
   * queue exceeds [maxLruPlaces].
   * The action will be executed out of the UI thread.
   *
   * This method returns a [Cancelable]. If cancelled, the action will not be executed.
   */
  fun addDelayedAction(parentDisposable: Disposable, action: () -> Unit) {
    // Put the action into a WeakReference to avoid the lambdas holding the reference
    val weakActionRef = WeakReference(action)
    val disposable = Disposable {
      val actionToRemove = weakActionRef.get() ?: return@Disposable
      removeActionFromQueue(actionToRemove)
    }

    Disposer.register(parentDisposable, disposable)
    val scheduledFuture = scheduledExecutorService.schedule({
                                                              val actionToRemove = weakActionRef.get() ?: return@schedule
                                                              if (removeActionFromQueue(actionToRemove)) {
                                                                actionToRemove()
                                                              }
                                                            }, delay.toMillis(), TimeUnit.MILLISECONDS)
    addActionToQueue(action, disposable, scheduledFuture)
  }
}