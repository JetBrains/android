/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.tools.idea.concurrency.createCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList
import java.util.AbstractQueue
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * A thread-safe queue of [Perishable] items. The elements in the queue are ordered by ascending expiration time. Elements with the same
 * expiration time maintain FIFO order. An iterator returned by the [iterator] method iterates over a snapshot of the queue at the time the
 * iterator was created. It will not reflect changes made after the iterator was created.
 */
class PerishableItemQueue<T : Perishable> : AbstractQueue<T>(), Disposable {

  override val size: Int
    get() = synchronized(items) { items.size }

  private val items = ArrayDeque<T>()
  private val scope = createCoroutineScope()
  private var janitor: Job? = null
  private val listeners = DisposableWrapperList<ExpirationListener<T>>()

  override fun add(item: T): Boolean {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val newItemExpiration = item.expirationTime
    if (newItemExpiration <= now) {
      // Already expired.
      notifyListeners(item)
      return false
    }

    synchronized(items) {
      var insertionIndex = items.binarySearch { it.expirationTime.compareTo(newItemExpiration) }
      if (insertionIndex < 0) {
        insertionIndex = insertionIndex.inv()
      } else {
        while (insertionIndex < items.size && items[insertionIndex].expirationTime <= newItemExpiration) {
          insertionIndex++
        }
      }
      items.add(insertionIndex, item)
      if (insertionIndex == 0) {
        cancelJanitor()
      }
      val nextExpiration = items.first().expirationTime
      if (janitor == null) {
        janitor = scope.launch { cleanupAfterDelay(nextExpiration - now) }
      }
    }
    return true
  }

  override fun offer(item: T): Boolean = add(item)

  override fun poll(): T? {
    synchronized(items) {
      val removed = items.removeFirstOrNull()
      if (items.isEmpty()) {
        cancelJanitor()
      }
      return removed
    }
  }

  override fun peek(): T? = synchronized(items) { items.firstOrNull() }

  override fun remove(item: T): Boolean {
    synchronized(items) {
      val removed = items.remove(item)
      if (items.isEmpty()) {
        cancelJanitor()
      }
      return removed
    }
  }

  override fun iterator(): MutableIterator<T> = SnapshotIterator()

  fun addExpirationListener(listener: ExpirationListener<T>) {
    if (listener is Disposable) {
      listeners.add(listener, listener)
    } else {
      listeners.add(listener)
    }
  }

  fun removeExpirationListener(listener: ExpirationListener<T>) {
    listeners.remove(listener)
  }

  override fun dispose() {}

  private fun removeExpiredItems(): List<T> {
    val expired: List<T>
    synchronized(items) {
      val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
      var index = items.indexOfFirst { it.expirationTime > now }
      if (index < 0) {
        index = items.size
      }
      if (index > 0) {
        expired = ArrayList(index)
        for (i in 0 until index) {
          expired.add(items.removeFirst())
        }
      } else {
        expired = emptyList()
      }
      return expired
    }
  }

  private fun notifyListeners(item: T) {
    for (listener in listeners) {
      listener.itemExpired(item)
    }
  }

  private suspend fun cleanupAfterDelay(delay: Duration) {
    delay(delay)
    val expired: List<T>
    synchronized(items) {
      expired = removeExpiredItems()
      janitor = null
      val firstItem = items.firstOrNull()
      if (firstItem != null) {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val nextExpiration = firstItem.expirationTime
        janitor = scope.launch { cleanupAfterDelay(nextExpiration - now) }
      }
    }
    for (item in expired) {
      notifyListeners(item)
    }
  }

  /** Cancels janitor job if it exists and sets the reference to it to null. Has to be called inside a `synchronized(items)` block. */
  private fun cancelJanitor() {
    janitor?.cancel()
    janitor = null
  }

  private inner class SnapshotIterator : MutableIterator<T> {
    private val delegate = synchronized(items) { items.toList() }.iterator()
    private var lastItem: T? = null

    override fun hasNext(): Boolean = delegate.hasNext()

    override fun next(): T {
      val next = delegate.next()
      lastItem = next
      return next
    }

    override fun remove() {
      val toRemove = lastItem ?: throw IllegalStateException()
      this@PerishableItemQueue.remove(toRemove)
      lastItem = null
    }
  }

  interface ExpirationListener<T> {
    fun itemExpired(item: T)
  }
}
