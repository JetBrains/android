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
package com.android.tools.idea.rendering.imagepool

import com.google.common.collect.ConcurrentHashMultiset
import com.google.common.collect.Multiset
import com.intellij.util.containers.CollectionFactory
import java.util.Collections
import java.util.function.Consumer

object ImagePoolImageDisposer {
  private val disposerLock = Any()
  private val lockedDisposeImage: Multiset<DisposableImage> = ConcurrentHashMultiset.create()
  private val pendingDispose: MutableSet<DisposableImage> = Collections.newSetFromMap(CollectionFactory.createConcurrentWeakMap())

  /**
   * Runs the given block of code avoiding the image to be disposed while the block is running.
   * If the image is disposed 1 or more times within the execution of the block or in a separate block, the image
   * will be disposed after the last dispose lock is released.
   */
  fun ImagePool.Image.runWithDisposeLock(block: ImagePool.Image.() -> Unit) {
    if (this !is DisposableImage || !isValid) {
      block(this)
      return
    }
    synchronized(disposerLock) {
      lockedDisposeImage.add(this)
    }
    try {
      block(this)
    }
    finally {
      val wasLastLock = synchronized(disposerLock) { lockedDisposeImage.remove(this, 1) == 1 }
      if (wasLastLock) {
        // This was the last lock, if the image was waiting to be released, now it's the time.
        if (pendingDispose.remove(this)) {
          dispose()
        }
      }
    }
  }

  /**
   * Runs the given block of code avoiding the image to be disposed while the block is running.
   * If the image is disposed 1 or more times within the execution of the block or in a separate block, the image
   * will be disposed after the last dispose lock is related.
   *
   * This is the same as [runWithDisposeLock] but more convenient for Java users.
   */
  @JvmStatic
  fun runWithDisposeLock(image: ImagePool.Image, block: Consumer<ImagePool.Image>) {
    image.runWithDisposeLock {
      block.accept(this)
    }
  }

  /**
   * Requests manually disposing the current image, this might happen at some point in the future
   * or not at all if the image is not [DisposableImage].
   */
  @JvmStatic
  fun disposeImage(image: ImagePool.Image) {
    if (image is DisposableImage) {
      var deferredDispose = false
      synchronized(disposerLock) {
        if (lockedDisposeImage.contains(image)) {
          deferredDispose = true
          // The image can not be disposed at the moment, add for later disposal.
          pendingDispose.add(image)
        }
      }
      if (!deferredDispose) {
        // Dispose immediately
        (image as DisposableImage).dispose()
      }
    }
  }
}