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
package com.android.tools.concurrency

import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ExecutorService

/**
 * Interface for service responsible for running Disk IO operations.
 */
interface AndroidIoManager {
  companion object {
    @JvmStatic
    fun getInstance(): AndroidIoManager = ApplicationManager.getApplication().getService(AndroidIoManager::class.java)!!
  }

  /**
   * Returns the [ExecutorService] to use for doing parallel disk IO. The returned [ExecutorService] will limit the total number of threads
   * dedicated to Android-related disk IO across the entire application. Since the platform doesn't provide a similar API, the bound is
   * only applicable to Android-related code.
   */
  fun getBackgroundDiskIoExecutor(): ExecutorService
}