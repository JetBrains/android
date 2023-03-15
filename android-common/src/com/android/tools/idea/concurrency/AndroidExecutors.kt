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
package com.android.tools.idea.concurrency

import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.idea.concurrency.AndroidExecutors.Companion.getInstance
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

/**
 * Set of [ExecutorService] instances used by code in the Android plugin.
 *
 * Instances are provided by PicoContainer from calls to [getInstance].
 */
@Service
class AndroidExecutors @NonInjectable constructor(
  /** Used to schedule work on the UI thread, following IntelliJ threading rules. */
  val uiThreadExecutor: (ModalityState, Runnable) -> Unit,

  /** Used to schedule background work that should not block the UI thread and is not IO intensive. */
  val workerThreadExecutor: Executor,

  /**
   * Used to schedule IO intensive computation, limiting the number of concurrent IO requests.
   *
   * @see AndroidIoManager
   */
  val diskIoThreadExecutor: Executor
) {
  constructor() : this(
    uiThreadExecutor = { modalityState, code -> ApplicationManager.getApplication().invokeLater(code, modalityState) },
    workerThreadExecutor = AppExecutorUtil.getAppExecutorService(),
    diskIoThreadExecutor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
  )

  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(AndroidExecutors::class.java)!!
  }
}
