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
package com.android.tools.idea.welcome.wizard

import com.android.annotations.concurrency.AnyThread
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator

/**
 * This interface facilitates migration from the deprecated {@link
 * com.android.tools.idea.welcome.wizard.deprecated.ProgressStep} step to the new {@link
 * com.android.tools.idea.welcome.wizard.ProgressStep} step. It can be removed once the migration is
 * complete and the deprecated class has been removed.
 */
interface ProgressStep {
  /** Returns true if the operation associated with this progress step has been cancelled. */
  @AnyThread fun isCanceled(): Boolean

  /**
   * Output text to the console pane.
   *
   * @param s The text to print
   * @param contentType Attributes of the text to output
   */
  @AnyThread fun print(s: String, contentType: ConsoleViewContentType)

  /**
   * Executes a runnable under a progress indicator, allocating a specific portion of the overall
   * progress to this runnable.
   *
   * @param runnable The code to execute.
   * @param progressPortion The fraction of the overall progress bar to allocate to this runnable
   *   (between 0.0 and 1.0).
   */
  @AnyThread fun run(runnable: Runnable, progressPortion: Double)

  /**
   * Will output process standard in and out to the console view.
   *
   * @param processHandler The process to track
   */
  @AnyThread fun attachToProcess(processHandler: ProcessHandler)

  /** Returns the progress indicator that will report the progress to this wizard step. */
  @AnyThread fun getProgressIndicator(): ProgressIndicator
}
