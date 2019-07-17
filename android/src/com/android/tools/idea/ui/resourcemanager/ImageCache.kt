/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.Async
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
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
                 timeUnit: TimeUnit = TimeUnit.MINUTES,
                 maximumCapacity: Long = MAXIMUM_CACHE_WEIGHT_BYTES,
                 mergingUpdateQueue: MergingUpdateQueue? = null
) : Disposable {


  private val pendingFutures = HashMap<DesignAsset, CompletableFuture<*>?>()

  private val updateQueue = mergingUpdateQueue ?: MergingUpdateQueue("queue", 3000, true, MergingUpdateQueue.ANY_COMPONENT, this, null,
                                                                     false)

  @Async.Schedule
  private fun runOrQueue(asset: DesignAsset,
                         executeImmediately: Boolean = false,
                         runnable: () -> Unit) {
    // We map to null to mark that the computation for asset has started and avoid any new computation.
    // It will then be replaced by the computation future once it is created.
    pendingFutures[asset] = null
    if (executeImmediately) {
      runnable()
    }
    else {
      val update = Update.create(asset.name, runnable)
      updateQueue.queue(update)
    }
  }

  override fun dispose() {
    synchronized(pendingFutures) {
      pendingFutures.values.forEach { it?.cancel(true) }
    }
  }

  private val objectToImage: Cache<DesignAsset, Image> = CacheBuilder.newBuilder()
    .expireAfterAccess(cacheExpirationTime, timeUnit)
    .softValues()
    .weigher<DesignAsset, Image> { _, image -> imageWeigher(image) }
    .maximumWeight(maximumCapacity)
    .build<DesignAsset, Image>()

  private fun imageWeigher(image: Image): Int {
    if (image is BufferedImage) {
      return image.raster.dataBuffer.size * Integer.BYTES
    }
    return image.getWidth(null) * image.getHeight(null) * Integer.BYTES
  }

  fun clear() {
    objectToImage.invalidateAll()
  }

  /**
   * Return the value identified by [key] in the cache if it exists, otherwise returns the [placeholder] image
   * and gets the image from the [CompletableFuture] returned by [computationFutureProvider].
   *
   * If [forceComputation] is true, the [CompletableFuture] will be ran even if a value is present in the cache.
   *
   * Note that if a value is present in the cache and [forceComputation] is true, the returned [Image] will be the value from
   * the cache.
   *
   * Once the image is cached, [onImageCached] is invoked on [executor] (or the EDT if none is provided)
   */
  fun computeAndGet(@Async.Schedule key: DesignAsset,
                    placeholder: Image,
                    forceComputation: Boolean,
                    onImageCached: () -> Unit = {},
                    executor: Executor = EdtExecutorService.getInstance(),
                    computationFutureProvider: (() -> CompletableFuture<out Image?>))
    : Image {
    val cachedImage = objectToImage.getIfPresent(key)
    if ((cachedImage == null || forceComputation) && !pendingFutures.containsKey(key)) {
      val executeImmediately = cachedImage == null // If we don't have any image, no need to wait.
      runOrQueue(key, executeImmediately) {
        startComputation(computationFutureProvider, key, onImageCached, executor)
      }
    }
    return cachedImage ?: placeholder
  }


  private fun startComputation(computationFutureProvider: () -> CompletableFuture<out Image?>,
                               @Async.Execute key: DesignAsset,
                               onImageCached: () -> Unit,
                               executor: Executor) {
    val future = computationFutureProvider()
      .thenAccept { image: Image? ->
        synchronized(pendingFutures) {
          pendingFutures.remove(key)
        }
        if (image != null) {
          objectToImage.put(key, image)
          executor.execute(onImageCached)
        }
      }
    synchronized(pendingFutures) {
      if (!future.isDone) {
        pendingFutures[key] = future
      }
    }
  }
}
