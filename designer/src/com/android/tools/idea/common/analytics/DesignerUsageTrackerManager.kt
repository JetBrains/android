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
package com.android.tools.idea.common.analytics

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.common.surface.DesignSurface
import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class DesignerUsageTrackerManager<T, K : Disposable>(
  private val factory: (Executor, K?, Consumer<AndroidStudioEvent.Builder>) -> T,
  private val nopTracker: T,
) {

  private val sTrackersCache =
    CacheBuilder.newBuilder().weakKeys().expireAfterAccess(5, TimeUnit.MINUTES).build<K, T>()
  private val ourExecutorService =
    ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))

  /** Returns an UsageTracker for the given surface or a no-op tracker if the surface is null */
  @VisibleForTesting
  fun getInstanceInner(key: K?, createIfNotExists: Boolean): T {
    if (key == null || Disposer.isDisposed(key)) {
      return nopTracker
    }

    return sTrackersCache.get(key) {
      if (createIfNotExists) {
        val newTracker = factory(ourExecutorService, key, Consumer { UsageTracker.log(it) })
        Disposer.register(key, Disposable { sTrackersCache.invalidate(key) })
        newTracker
      } else {
        nopTracker
      }
    }
  }

  /**
   * Returns an usage tracker for the given surface or a no-op tracker if the surface is null or
   * stats tracking is disabled. The stats are also disabled during unit testing.
   */
  fun getInstance(key: K?): T {
    // If we are in unit testing mode, do not allow creating new instances.
    // Test instances should be used.
    return if (AnalyticsSettings.optedIn)
      getInstanceInner(key, !ApplicationManager.getApplication().isUnitTestMode)
    else nopTracker
  }

  /** Sets the corresponding usage tracker for a [DesignSurface] in tests. */
  @TestOnly
  fun setInstanceForTest(key: K, tracker: T) {
    sTrackersCache.put(key, tracker)
  }

  /** Clears the cached instances to clean state in tests. */
  @TestOnly
  fun cleanAfterTesting(key: K) {
    // The previous tracker may be a mock with recorded data that may show up as leaks.
    // Replace the tracker first since invalidation may be delayed.
    sTrackersCache.put(key, nopTracker)
    sTrackersCache.invalidate(key)
  }
}
