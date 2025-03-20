/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import com.android.annotations.concurrency.Slow
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Used in conjunction with [ScreenshotViewer].
 */
interface ScreenshotProvider : Disposable {
  /**
   * Captures and returns a new screenshot.
   *
   * @throws RuntimeException if an error occurred
   * @throws CancellationException if the operation was cancelled
   */
  @Slow
  @Throws(RuntimeException::class, CancellationException::class)
  fun captureScreenshot(): ScreenshotImage
}