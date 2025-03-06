/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.project.coroutines

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.Computable
import kotlinx.coroutines.delay

/**
 * Smart mode doesn't wait for UnindexedFilesScannerExecutorImpl queued tasks to finish
 * so this was added to explicitly wait for them until IDEA-356331 gets fixed.
 */
suspend fun <T> Project.runReadActionInSmartModeWithIndexes(body: Computable<T>): T {
  var result: T? = null
  while (true) {
    val done = smartReadAction(this) {
      if (UnindexedFilesScannerExecutor.getInstance(this).hasQueuedTasks
          || UnindexedFilesScannerExecutor.getInstance(this).isRunning.value) {
        return@smartReadAction false
      }
      else {
        result = body.compute()
        return@smartReadAction true
      }
    }
    if (done) return result!!
    delay(1000)
  }
}