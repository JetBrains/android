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
package com.android.tools.idea.resourceExplorer

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ListenableFuture
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.pow

private val MAXIMUM_CACHE_WEIGHT_BYTES = (200 * 1024.0.pow(2)).toLong() // 200 MB

/**
 * Helper class that caches the result of a computation of an [Image].
 *
 * The keys of the cache are strong references to let
 * @param cacheExpirationTime Time after which the cache expire. (Defaults to 5 minutes.)
 * @see Cache
 * @see CacheBuilder.softValues
 */
class ImageCache(cacheExpirationTime: Long = 5,
                 timeUnit: TimeUnit = TimeUnit.MINUTES) {

  private val objectToImage: Cache<DesignAsset, Image> = CacheBuilder.newBuilder()
    .expireAfterAccess(cacheExpirationTime, timeUnit)
    .weigher<DesignAsset, Image> { _, image -> imageWeigher(image) }
    .maximumWeight(MAXIMUM_CACHE_WEIGHT_BYTES)
    .build<DesignAsset, Image>()

  private fun imageWeigher(image: Image) = image.getWidth(null) * image.getHeight(null)

  /**
   * Return the value identified by [key] in the cache if it exists, otherwise returns the [placeholder] image
   * and gets the image from the [CompletableFuture] returned by [computationFutureProvider].
   *
   * If [forceComputation] is true, the [CompletableFuture] will be ran even if a value is present in the cache.
   *
   * Note that if a value is present in the cache and [forceComputation] is true, the returned [Image] will be the value from
   * the cache.
   */
  fun computeAndGet(key: DesignAsset,
                    placeholder: Image,
                    forceComputation: Boolean,
                    computationFutureProvider: (() -> CompletableFuture<out Image?>))
    : Image {

    val cachedImage = objectToImage.getIfPresent(key)
    if (cachedImage == null) {
      // We cache the placeholder to avoid starting another computation while the initial one is running
      objectToImage.put(key, placeholder)
    }
    if (cachedImage == null || forceComputation) {
      computationFutureProvider().whenComplete { image: Image?, _ ->
        image?.also { objectToImage.put(key, it) }
      }
    }
    return cachedImage ?: placeholder
  }
}

internal fun <T> ListenableFuture<T>.toCompletableFuture(): CompletableFuture<T> = CompletableFuture.supplyAsync { this.get() }
