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
package com.android.tools.idea.sqlite.ui.exportToFile

import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

interface ExportInProgressView {
  fun show()
}

/**
 * Modal dialog showing an export operation is in progress and allowing for the operation to be
 * cancelled.
 */
class ExportInProgressViewImpl(
  private val project: Project,
  private val job: Job,
  private val taskDispatcher: CoroutineDispatcher
) : ExportInProgressView {
  @VisibleForTesting var onShownListener: (ProgressIndicator) -> Unit = {}
  @VisibleForTesting var onClosedListener: () -> Unit = {}

  override fun show() {
    val progressWindow = ProgressWindow(true, false, project)
    progressWindow.title = DatabaseInspectorBundle.message("export.progress.dialog.title")
    progressWindow.isIndeterminate = true
    progressWindow.addStateDelegate(
      object : AbstractProgressIndicatorExBase() {
        override fun cancel() {
          job.cancel(UserCancellationException())
          super.cancel()
        }
      }
    )
    project.coroutineScope.launch(taskDispatcher) {
      try {
        progressWindow.start()
        progressWindow.text =
          DatabaseInspectorBundle.message(
            "export.progress.dialog.caption"
          ) // must be called after `start`
        onShownListener(progressWindow)
        job.join()
      } finally {
        withContext(NonCancellable) {
          progressWindow.stop()
          onClosedListener()
        }
      }
    }
  }

  class UserCancellationException : CancellationException()
}
