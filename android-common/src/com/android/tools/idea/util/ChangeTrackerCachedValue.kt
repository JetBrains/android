/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.annotations.TestOnly
import com.android.annotations.concurrency.GuardedBy
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class StrongReference<T>(val value: T) : WeakReference<T>(value)

/**
 * Equivalent to the IntelliJ `ModificationTracker` but for [ChangeTrackerCachedValue].
 */
fun interface ChangeTracker {
  /**
   * Returns the modification count.
   */
  fun count(): Long

  companion object {
    val NEVER_CHANGE: ChangeTracker = ChangeTracker { 0L }
    val EVER_CHANGING = object : ChangeTracker {
      private val counter = AtomicLong(0L)

      override fun count(): Long = counter.getAndIncrement()
    }
  }
}

/**
 * Constructs a ChangeTracker that aggregates multiple [ChangeTracker]s.
 */
fun ChangeTracker(trackers: Collection<ChangeTracker>): ChangeTracker =
  ChangeTracker { trackers.sumOf { it.count() } }

/**
 * Constructs a ChangeTracker that aggregates multiple [ChangeTracker]s.
 */
fun ChangeTracker(vararg trackers: ChangeTracker): ChangeTracker =
  ChangeTracker { trackers.sumOf { it.count() } }

/**
 * Class that caches a value until one of the dependencies changes, or it's collected by the garage collection.
 * This class provides similar capabilities to the `CachedValuesManager` but avoids depending on any IntelliJ platform. For cases
 * where the value only depends on trackers, this is a more lightweight solution that does not hold references to the project.
 */
class ChangeTrackerCachedValue<T> private constructor(
  val referenceFactory: (T) -> Reference<T>,
) {
  private val nullReference: Reference<T> = WeakReference(null)

  private val cachedLock = ReentrantReadWriteLock()

  @GuardedBy("cachedLock")
  private var cachedReferenceCount: Long = -1

  @GuardedBy("cachedLock")
  private var cachedReference: Reference<T> = nullReference

  /**
   * Obtains and updates the cached value using the [provider]
   */
  private suspend fun updateAndGetCachedValue(provider: suspend () -> T, dependency: ChangeTracker): T {
    val value = provider()
    val dependencyValue = dependency.count()
    cachedLock.write {
      cachedReference = referenceFactory(value)
      cachedReferenceCount = dependencyValue
    }
    return value
  }

  /**
   * Returns the value referenced by this [ChangeTrackerCachedValue]. This call might call the [provider]
   * if the current value is out-of-date or has never been obtained before.
   */
  private suspend fun get(provider: suspend () -> T, dependency: ChangeTracker): T {
    cachedLock.read {
      val dependencyValue = cachedReferenceCount
      val cachedValue = cachedReference.get()

      if (dependency.count() == dependencyValue && cachedValue != null) {
        // Everything is up-to-date
        return cachedValue
      }
    }

    return updateAndGetCachedValue(provider, dependency)
  }

  companion object {
    /**
     * Returns a [ChangeTrackerCachedValue] that holds a cached value via a [SoftReference].
     */
    fun <T> softReference(): ChangeTrackerCachedValue<T> = ChangeTrackerCachedValue {
      SoftReference(it)
    }

    /**
     * Returns a [ChangeTrackerCachedValue] that holds a cached value via a [WeakReference].
     */
    fun <T> weakReference(): ChangeTrackerCachedValue<T> = ChangeTrackerCachedValue {
      WeakReference(it)
    }

    /**
     * Returns a [ChangeTrackerCachedValue] that holds a cached value via a [StrongReference].
     */
    @TestOnly
    fun <T> strongReference(): ChangeTrackerCachedValue<T> = ChangeTrackerCachedValue {
      StrongReference(it)
    }

    suspend fun <T> get(cached: ChangeTrackerCachedValue<T>, provider: suspend () -> T, dependency: ChangeTracker): T =
      cached.get(provider, dependency)
  }
}