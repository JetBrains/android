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
package com.android.tools.idea.ui.screenrecording

import com.intellij.openapi.Disposable
import kotlinx.coroutines.Deferred
import java.nio.file.Path

/**
 * Provides screen recording functionality.
 */
interface RecordingProvider : Disposable {
  val fileExtension: String

  /**
   * Starts recording and returns. The recording continues until it is stopped, cancelled,
   * or exceeds the maximum supported duration. The returned [Deferred] is completed when
   * the recording finishes. Normal completion indicates that recording was successful.
   * An exceptional completion indicates either recording error or cancellation.
   */
  suspend fun startRecording(): Deferred<Unit>

  /** Stops recording asynchronously. */
  fun stopRecording()

  /** Cancels recording asynchronously. */
  fun cancelRecording()

  suspend fun doesRecordingExist(): Boolean

  suspend fun pullRecording(target: Path)
}