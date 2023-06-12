/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Implementation of [AdbLoggerFactory] that integrates with the IntelliJ/Android Studio platform [Logger].
 */
internal class AndroidAdbLoggerFactory : AdbLoggerFactory {
  /**
   * We cache `class` loggers in memory because
   *
   * * there are only a few dozens created in practice, and
   * * the use of the cache decreases GC allocation pressure, as adblib code tends
   *   to use non-static fields for [AdbLogger] instances.
   */
  private val classLoggerCache = LoggerCache<Class<*>> { AndroidAdbLogger(Logger.getInstance(it)) }

  /**
   * We cache `category` loggers in memory, see [classLoggerCache] for justification.
   */
  private val categoryLoggerCache = LoggerCache<String> { AndroidAdbLogger(Logger.getInstance(it)) }

  override val logger: AdbLogger = createLogger("adblib")

  override fun createLogger(cls: Class<*>): AdbLogger {
    return classLoggerCache.computeIfAbsent(cls)
  }

  override fun createLogger(category: String): AdbLogger {
    return categoryLoggerCache.computeIfAbsent(category)
  }

  private class LoggerCache<TKey>(
    /**
     * Note: We use a [Function] lambda (as opposed to a `Kotlin` lambda) to ensure no allocation is
     * performed when calling [ConcurrentHashMap.computeIfAbsent]
     */
    private val mappingFunction: Function<TKey, AdbLogger>
  ) {
    /**
     * Note: We use a [ConcurrentHashMap] so that we get a lock-free lookup.
     */
    private val loggers = ConcurrentHashMap<TKey, AdbLogger>()

    fun computeIfAbsent(key: TKey): AdbLogger {
      return loggers.computeIfAbsent(key, mappingFunction)
    }
  }
}
