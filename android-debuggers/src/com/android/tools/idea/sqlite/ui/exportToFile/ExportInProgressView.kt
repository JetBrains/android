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

import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting

interface ExportInProgressView {
  fun show()
}

/** Modal dialog showing an export operation is in progress and allowing for the operation to be cancelled. */
class ExportInProgressViewImpl(
  private val project: Project,
  private val job: Job,
  private val taskDispatcher: CoroutineDispatcher
) : ExportInProgressView {
  @VisibleForTesting var onShowListener: (ProgressIndicator) -> Unit = {}
  @VisibleForTesting var onCloseListener: () -> Unit = {}

  override fun show() {
    object : Task.Modal(project, DatabaseInspectorBundle.message("export.progress.dialog.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        onShowListener(indicator)
        indicator.isIndeterminate = true // no progress [in %] tracking
        indicator.text = DatabaseInspectorBundle.message("export.progress.dialog.caption")

        when (indicator) {
          is ProgressIndicatorEx -> { // most likely the case, but no API guarantee
            indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() { // Cancel button logic
              override fun cancel() {
                job.cancel()
                super.cancel()
              }
            })
            runBlocking(taskDispatcher) { job.join() }
            onCloseListener()
          }
          else -> { // falling back to polling
            runBlocking(taskDispatcher) {
              while (job.isActive) {
                if (indicator.isCanceled) job.cancel() else delay(30)
              }
            }
            onCloseListener()
          }
        }
      }
    }.queue()
  }
}