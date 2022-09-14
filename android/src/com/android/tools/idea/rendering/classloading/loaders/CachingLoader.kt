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
package com.android.tools.idea.rendering.classloading.loaders

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.CacheStats
import com.google.common.cache.LoadingCache
import java.util.concurrent.TimeUnit

/**
 * A [DelegatingClassLoader.Loader] that caches the loaded data. This class accepts a pre-configured [CacheBuilder] to tweak
 * the caching behaviour.
 */
class CachingLoader constructor(
  private val delegate: DelegatingClassLoader.Loader,
  private val cacheBuilder: CacheBuilder<String, ByteArray>) : DelegatingClassLoader.Loader {

  constructor(delegate: DelegatingClassLoader.Loader,
              expireAfterWriteMs: Long = DEFAULT_EXPIRE_AFTER_WRITE_MS,
              maxSizeInBytes: Long = DEFAULT_MAX_SIZE_BYTES) :
    this(delegate, createCacheBuilder(expireAfterWriteMs, maxSizeInBytes))

  private val cacheLoader: CacheLoader<String, ByteArray> = CacheLoader.from { fqcn ->
    return@from delegate.loadClass(fqcn ?: return@from NULL_VALUE) ?: NULL_VALUE
  }
  private val cache: LoadingCache<String, ByteArray> = cacheBuilder.build(cacheLoader)

  @Suppress("ReplaceArrayEqualityOpWithArraysEquals") // it == NULL_VALUE, we want to compare reference, not content.
  override fun loadClass(fqcn: String): ByteArray? =
    cache.get(fqcn)?.takeUnless { it == NULL_VALUE }

  /**
   * Invalidates the whole cache.
   */
  fun invalidateAll() = cache.invalidateAll()

  /**
   * Invalidates a single cache entry if present.
   */
  fun invalidate(fqcn: String) = cache.invalidate(fqcn)

  /**
   * Returns the internal cache [CacheStats].
   */
  fun stats(): CacheStats = cache.stats()

  companion object {
    private val DEFAULT_EXPIRE_AFTER_WRITE_MS = TimeUnit.MINUTES.toMillis(5)
    private const val DEFAULT_MAX_SIZE_BYTES = 50_000_000L
    private val NULL_VALUE = ByteArray(0)
    private fun createCacheBuilder(
      expireAfterWriteMs: Long,
      maxSizeInBytes: Long) = CacheBuilder.newBuilder()
      .expireAfterAccess(expireAfterWriteMs, TimeUnit.MILLISECONDS)
      .weigher { _: String, value: ByteArray -> value.size }
      .maximumWeight(maxSizeInBytes)
  }
}