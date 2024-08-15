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

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * The methods block execution while coroutines in the corresponding job are not done.
 * Usually it is required to get the proper result if your refactoring starts a coroutine outside the general execution e.g. adding imports
 */
@RequiresEdt
fun waitCoroutinesBlocking(job: Job) {
  runBlockingMaybeCancellable {
    while (true) {
      UIUtil.dispatchAllInvocationEvents()
      yield()
      delay(1) //prevent too frequent polling, otherwise may load cpu with billions of context switches

      if (job.isCompleted || job.isCancelled) break
    }
  }
}