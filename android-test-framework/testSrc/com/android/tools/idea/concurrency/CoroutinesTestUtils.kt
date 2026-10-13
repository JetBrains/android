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
package com.android.tools.idea.concurrency

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Job

/**
 * The methods block execution while coroutines in the corresponding job are not done. Usually it is required to get the proper result if
 * your refactoring starts a coroutine outside the general execution e.g. adding imports
 */
@RequiresEdt
fun waitCoroutinesBlocking(job: Job) {
  val timeoutInSeconds = 120
  val isDone = { job.isCompleted || job.isCancelled }
  val err = "Timed out waiting for coroutine job to finish: $job"
  PlatformTestUtil.waitWithEventsDispatching(err, isDone, timeoutInSeconds)
}
