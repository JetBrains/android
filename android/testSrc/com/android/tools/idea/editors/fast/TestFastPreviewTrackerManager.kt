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
package com.android.tools.idea.editors.fast

class TestFastPreviewTrackerManager : FastPreviewTrackerManager {
  private val outputLog = StringBuilder()

  override fun userEnabled() {
    outputLog.appendLine("userEnabled")
  }

  override fun userDisabled() {
    outputLog.appendLine("userDisabled")
  }

  override fun autoDisabled() {
    outputLog.appendLine("autoDisabled")
  }

  override fun daemonStartFailed() {
    outputLog.appendLine("daemonStartFailed")
  }

  override fun trackRequest(): FastPreviewTrackerManager.Request = object: FastPreviewTrackerManager.Request {
    override fun daemonStartFailed() {
      this@TestFastPreviewTrackerManager.daemonStartFailed()
    }

    override fun compilationFailed(compilationDurationMs: Long, compiledFiles: Int) {
      assert(compilationDurationMs != -1L && compiledFiles != -1) { "compilationFailed can not be called without information" }
      outputLog.appendLine("compilationFailed: files=$compiledFiles")
    }

    override fun compilationSucceeded(compilationDurationMs: Long, compiledFiles: Int, refreshTimeMs: Long) {
      assert(compilationDurationMs != -1L && compiledFiles != -1) { "compilationSucceeded can not be called without information" }
      outputLog.appendLine(
        "compilationSucceeded: files=$compiledFiles")
    }
  }

  fun logOutput(): String = outputLog.toString().trimEnd()
}