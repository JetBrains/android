/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.annotations.concurrency.GuardedBy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Class that contains a value of type [T] that is lazily initialized. This class allows for code
 * that does not support coroutines yet to interoperate with lazy loaders. Initially, this class
 * will return [beforeLoadValue] until a new value has been loaded. Usually, on a first invocation
 * to [getCachedValueOrUpdate], the method will return [beforeLoadValue] and trigger a new load. The
 * load will happen by invoking the [loader] method using the [loadingScope]. Once the [loader] has
 * completed, [onValueLoaded] will be invoked with the new value. [onValueLoaded] is guaranteed to
 * only be invoked once. [loader] can be invoked multiple times so, it's important for the method to
 * not have side effects.
 */
internal class LazyCachedValue<T>(
  private val loadingScope: CoroutineScope,
  private val loader: suspend () -> T,
  private val onValueLoaded: suspend (T) -> Unit,
  private val beforeLoadValue: T,
) {
  /** Will become true once the value has been cached. */
  private val loaded = AtomicBoolean(false)
  private val cachedValueLock = Mutex()
  @GuardedBy("cachedValueLock") private var cachedValue: T = beforeLoadValue
  private val loading = Mutex()

  private suspend fun loadAndCacheValue(): T =
    loading.withLock {
      if (loaded.get()) return@withLock beforeLoadValue
      val newValue = loader()
      if (!loaded.getAndSet(true)) {
        cachedValueLock.withLock { cachedValue = newValue }
        onValueLoaded(newValue)
      }
      return@withLock newValue
    }

  /**
   * Returns the current cached value. If there is no value loaded, this method will return
   * [beforeLoadValue] and will try to retrieve a new value by invoking [loader]. Once the value is
   * ready, [onValueLoaded] will be invoked with the newly obtained value.
   */
  fun getCachedValueOrUpdate(): T {
    if (!loaded.get()) {
      loadingScope.launch { loadAndCacheValue() }
    }
    return if (cachedValueLock.tryLock()) {
      try {
        cachedValue
      } finally {
        cachedValueLock.unlock()
      }
    } else beforeLoadValue
  }

  /**
   * Returns the value and suspend if not present yet. If the value has not been loaded yet, this
   * will trigger the load flow, wait for the value can also call [onValueLoaded].
   */
  suspend fun getValue(): T =
    if (loaded.get()) {
      cachedValueLock.withLock { cachedValue }
    } else loadAndCacheValue()
}
