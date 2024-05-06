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

import com.android.annotations.concurrency.GuardedBy
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.jetbrains.annotations.TestOnly

import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Class to handle listeners and calls. This class is thread-safe and handles the case where listeners add or remove
 * listeners during their execution.
 *
 * Please note that you may consider to use a more efficient ContainerUtil.createLockFreeCopyOnWriteList instead of
 * this class.
 *
 * @param T the listener type
 * @param myExecutor the executor to use when calling listeners in this collection
 */
class ListenerCollection<T> private constructor(private val myExecutor: Executor) {
  /** Lock that guards the access to the instance state  */
  private val myLock = ReentrantReadWriteLock()

  @GuardedBy("myLock")
  private val myListenersSet = HashSet<T>()
  /** Cached immutable copy of myListenersSet used to avoid extra copies when the listeners do not change  */
  @GuardedBy("myLock")
  private var myListenerSetCopy: ImmutableSet<T>? = null

  /**
   * Adds a listener from the handler. If this method returns false, the listener was already in the handler
   */
  fun add(listener: T): Boolean = myLock.write {
    myListenerSetCopy = null
    myListenersSet.add(listener)
  }

  /**
   * Removes a listener from the handler and returns whether the passed listener existed or not.
   */
  fun remove(listener: T): Boolean = myLock.write {
    myListenerSetCopy = null
    myListenersSet.remove(listener)
  }

  /**
   * Removes all the listeners from the handler
   */
  fun clear() = myLock.write {
    myListenerSetCopy = ImmutableSet.of()
    myListenersSet.clear()
  }

  /**
   * Returns the size of the collection.
   */
  @TestOnly
  fun size(): Int = myLock.read {
    return getUpToDateListeners().size
  }

  /**
   * Iterates over all the listeners in the given [Executor]. This method returns a [ListenableFuture] to know when
   * the processing has finished.
   */
  fun forEach(runOnListener: Consumer<T>): ListenableFuture<Void> {
    val listeners: Set<T> = getUpToDateListeners()

    if (listeners.isEmpty()) {
      return Futures.immediateFuture(null)
    }

    val future = SettableFuture.create<Void>()
    myExecutor.execute {
      listeners.forEach(runOnListener)
      future.set(null)
    }

    return future
  }

  private fun getUpToDateListeners(): Set<T> = myLock.read {
    if (myListenerSetCopy == null) {
      // The cache was out-of-date. Rebuild it. First upgrade the lock to write
      myLock.write {
        if (myListenerSetCopy == null) {
          // Cache current list of listeners
          myListenerSetCopy = ImmutableSet.copyOf(myListenersSet)
        }
      }
    }
    myListenerSetCopy!!
  }

  companion object {
    /**
     * Creates a ListenerCollection that will call listeners in the [.forEach] caller thread
     */
    @JvmStatic
    fun <T> createWithDirectExecutor(): ListenerCollection<T> {
      return createWithExecutor(MoreExecutors.directExecutor())
    }

    /**
     * Creates a ListenerCollection that will call listeners in the given [Executor]
     */
    @JvmStatic
    fun <T> createWithExecutor(executor: Executor): ListenerCollection<T> {
      return ListenerCollection(executor)
    }
  }
}