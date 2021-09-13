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
package com.android.tools.idea.logcat

import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.waitForCondition
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ConcurrencyUtil.awaitQuiescence
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.BoundedTaskExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * Waits for [LogcatDocumentPrinter] to idle and execute some code.
 *
 * 1. Waits until all entries sent to the channel are received.
 * 2. Waits for the worker threads that received the channel entries to complete (launch work on UI thread)
 * 3. Posts code to be executed on the UI thread and wait.
 *
 * Note that this cannot work on the UI Thread itself because [runInEdtAndWait] would return immediately.
 */
@ExperimentalCoroutinesApi
internal fun LogcatDocumentPrinter.onIdle(run: () -> Any) {
  assert(!SwingUtilities.isEventDispatchThread())
  waitForCondition(5, TimeUnit.SECONDS, this::isChannelEmpty)

  // This call depends on AndroidExecutors.workerThreadExecutor being replaced by a ThreadPoolExecutor that allows operations that are
  // prohibited by the one provided by the framework (BackendThreadPoolExecutor). See AndroidExecutorsRule for how to inject a compliant
  // executor.
  awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
  runInEdtAndWait {
    run()
  }
}
