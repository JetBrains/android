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
package com.android.tools.idea.device.explorer.files.fs

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread

/**
 * Progress indicator for downloading multiple entries from a device.
 */
interface DownloadProgress {
  @AnyThread
  fun isCancelled(): Boolean

  @UiThread
  fun onStarting(entryFullPath: String)

  @UiThread
  fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long)

  @UiThread
  fun onCompleted(entryFullPath: String)
}
