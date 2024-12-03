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

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator

/**
 * This interface facilitates migration from the deprecated {@link com.android.tools.idea.welcome.wizard.deprecated.ProgressStep}
 * step to the new {@link com.android.tools.idea.welcome.wizard.ProgressStep} step. It can be removed once the migration is complete
 * and the deprecated class has been removed.
 */
interface ProgressStep {
  fun isCanceled(): Boolean
  fun print(s: String, contentType: ConsoleViewContentType)
  fun run(runnable: Runnable, progressPortion: Double)
  fun attachToProcess(processHandler: ProcessHandler)
  fun getProgressIndicator(): ProgressIndicator
}