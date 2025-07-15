/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import kotlinx.coroutines.CompletableDeferred

// Coroutine-friendly variants of EmulatorController methods.

/** Retrieves a screenshot of an Emulator display. */
suspend fun EmulatorController.getScreenshot(imageFormat: ImageFormat): Image {
  val observer = SuspendingStreamObserver<Image>()
  getScreenshot(imageFormat, observer)
  return observer.getResult()
}

private class SuspendingStreamObserver<T> : EmptyStreamObserver<T>() {

  private val deferredResult = CompletableDeferred<T>()

  suspend fun getResult(): T =
    deferredResult.await()

  override fun onNext(message: T) {
    deferredResult.complete(message)
  }

  override fun onError(t: Throwable) {
    deferredResult.completeExceptionally(t)
  }
}
