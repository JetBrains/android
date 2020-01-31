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
import com.intellij.openapi.util.Disposer
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

private val SMALL_MAXIMUM_CACHE_WEIGHT_BYTES = (10 * 1024.0.pow(2)).toLong() // 10 MB
private val LARGE_MAXIMUM_CACHE_WEIGHT_BYTES = (100 * 1024.0.pow(2)).toLong() // 100 MB

/**
 * Helper class that caches the result of a computation of an [Image].
 *
 * The keys of the cache are strong references to let
 * @see Cache
 * @see CacheBuilder.softValues
 */
class ImageCache private constructor(mergingUpdateQueue: MergingUpdateQueue?,
                                     private val objectToImage: Cache<DesignAsset, Image>
) : Disposable {
  companion object {
    private val largeObjectToImage by lazy { createObjectToImageCache(5, LARGE_MAXIMUM_CACHE_WEIGHT_BYTES) }
    private val smallObjectToImage by lazy { createObjectToImageCache(1, SMALL_MAXIMUM_CACHE_WEIGHT_BYTES) }

    /**
     * Returns an ImageCache that uses an image pool of size [LARGE_MAXIMUM_CACHE_WEIGHT_BYTES] to store previews for a given [DesignAsset]
     *
     * @param parentDisposable Used to dispose of the returned [ImageCache], used as the parent disposable for the default
     * [MergingUpdateQueue] when the [mergingUpdateQueue] parameter is null.
     */
    fun createLargeImageCache(
      parentDisposable: Disposable,
      mergingUpdateQueue: MergingUpdateQueue? = null
    ) = ImageCache(mergingUpdateQueue, largeObjectToImage).apply { Disposer.register(parentDisposable, this) }

    /**
     * Returns an ImageCache that uses an image pool of size [SMALL_MAXIMUM_CACHE_WEIGHT_BYTES] to store previews for a given [DesignAsset]
     *
     * @param parentDisposable Used to dispose of the returned [ImageCache], used as the parent disposable for the default
     * [MergingUpdateQueue] when the [mergingUpdateQueue] parameter is null.
     */
    fun createSmallImageCache(
      parentDisposable: Disposable,
      mergingUpdateQueue: MergingUpdateQueue? = null
    ) = ImageCache(mergingUpdateQueue, smallObjectToImage).apply { Disposer.register(parentDisposable, this) }
  }

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

private fun createObjectToImageCache(duration: Long, size: Long): Cache<DesignAsset, Image> =
  CacheBuilder.newBuilder()
    .expireAfterAccess(duration, TimeUnit.MINUTES)
    .softValues()
    .weigher<DesignAsset, Image> { _, image -> imageWeigher(image) }
    .maximumWeight(size)
    .build<DesignAsset, Image>()

private fun imageWeigher(image: Image): Int {
  if (image is BufferedImage) {
    return image.raster.dataBuffer.size * Integer.BYTES
  }
  return image.getWidth(null) * image.getHeight(null) * Integer.BYTES
}
