/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.progress

import com.android.annotations.concurrency.AnyThread
import com.android.repository.api.NullProgressIndicator
import com.android.repository.api.ProgressRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * [ProgressRunner] implementation that sets up IntelliJ's progress reporting and adapts it to the
 * sdklib progress reporting interface.
 */
class StudioProgressRunner
@JvmOverloads
constructor(
  private val cancellable: Boolean,
  private val title: String,
  private val project: Project?,
  private val coroutineScope: CoroutineScope =
    StudioProgressRunnerService.getInstance().coroutineScope + Dispatchers.Default,
) : ProgressRunner {
  /** Runs the task on [coroutineScope] with a background progress indicator. */
  @AnyThread
  override fun runAsyncWithProgress(r: ProgressRunner.ProgressRunnable) {
    coroutineScope.launch {
      if (project == null) {
        // withBackgroundProgress requires a project to own the progress indicator. If we don't have
        // one, just run the task directly and pass an empty progress indicator.
        r.run(NullProgressIndicator, this@StudioProgressRunner)
      } else {
        withBackgroundProgress(project, title, cancellable) {
          reportRawProgress { reporter ->
            r.run(RawProgressReporterAdapter(reporter), this@StudioProgressRunner)
          }
        }
      }
    }
  }

  /**
   * Runs a modal progress reporting dialog on the EDT, which invokes the task on a background
   * worker thread.
   */
  @AnyThread
  override fun runSyncWithProgress(progressRunnable: ProgressRunner.ProgressRunnable) {
    // runWithModalProgressBlocking requires the EDT
    ApplicationManager.getApplication()
      .invokeAndWait(
        {
          runWithModalProgressBlocking(
            owner =
              if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
            title = title,
            cancellation =
              if (cancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable(),
          ) {
            reportRawProgress { reporter ->
              progressRunnable.run(RawProgressReporterAdapter(reporter), this@StudioProgressRunner)
            }
          }
        },
        ModalityState.any(),
      )
  }
}

/** Service for providing a CoroutineScope to StudioProgressRunner. */
@Service(Service.Level.APP)
class StudioProgressRunnerService(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): StudioProgressRunnerService = service<StudioProgressRunnerService>()
  }
}
