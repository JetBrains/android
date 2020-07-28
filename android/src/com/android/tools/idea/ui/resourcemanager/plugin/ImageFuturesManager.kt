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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.annotations.concurrency.GuardedBy
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.awt.image.BufferedImage
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance(ImageFuturesManager::class.java)

/**
 * Handles incoming [CompletableFuture]s that return [BufferedImage]s.
 *
 * Will associate a given [CompletableFuture] to an object of the given type [T] to avoid task repetition.
 */
class ImageFuturesManager<T> : Disposable {
  @GuardedBy("disposalLock")
  private val myPendingFutures = HashMap<T, CompletableFuture<BufferedImage?>>()

  @GuardedBy("disposalLock")
  private var myDisposed: Boolean = false

  private val disposalLock = Any()

  override fun dispose() {
    lateinit var futures: Array<CompletableFuture<BufferedImage?>>
    synchronized(disposalLock) {
      myDisposed = true
      myPendingFutures.values.forEach { it.cancel(false) } // Cancel pending futures
      futures = myPendingFutures.values.filter { !it.isCancelled }.toTypedArray()
      myPendingFutures.clear()
    }
    try {
      CompletableFuture.allOf(*futures).get(5, TimeUnit.SECONDS) // Wait for remaining futures to complete
    }
    catch (e: Exception) {
      // We do not care about these exceptions since we are disposing anyway
    }
  }

  /**
   * Registers the [CompletableFuture] from the [imageFutureCallback] to the given [key].
   *
   * If there's already a running task for the same [key], it will return that [CompletableFuture] and will not invoke
   * [imageFutureCallback].
   */
  fun registerAndGet(key: T, imageFutureCallback: () -> CompletableFuture<BufferedImage?>): CompletableFuture<BufferedImage?> {
    synchronized(disposalLock) {
      if (myDisposed) {
        LOG.warn("Disposed, can't complete task for ${key}.")
        return CompletableFuture.completedFuture(null)
      }
      val inProgress = myPendingFutures[key]
      if (inProgress != null) {
        return inProgress
      }
    }
    val imageFuture = imageFutureCallback()
    synchronized(disposalLock) {
      myPendingFutures.put(key, imageFuture)
    }

    imageFuture.whenComplete { _, _ ->
      synchronized(disposalLock) {
        myPendingFutures.remove(key)
      }
    }
    return imageFuture
  }
}