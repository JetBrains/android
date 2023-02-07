/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.concurrency

import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.UnindexedFilesUpdater
import java.util.concurrent.ExecutorService

private const val NAME = "Android IO tasks"

class StudioIoManager : AndroidIoManager {
  /**
   * Maximum number of threads performing IO in parallel.
   *
   * First approximation based on what indexing is using.
   */
  private val threadCount = UnindexedFilesUpdater.getNumberOfIndexingThreads()

  private val boundedExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(NAME, threadCount)

  override fun getBackgroundDiskIoExecutor(): ExecutorService = boundedExecutor
}