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
package com.android.tools.idea.sqlite.ui

import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressViewImpl
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressViewImpl.UserCancellationException
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.ide.PooledThreadExecutor

class ExportInProgressViewTest : LightPlatformTestCase() {
  private val taskDispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()

  fun test_job_completes() {
    // Set up a job that finishes when jobDone=true
    val jobDone = AtomicBoolean(false)
    val job: Job = project.coroutineScope.launch { while (!jobDone.get()) delay(50) }
    val dialog = ExportInProgressViewImpl(project, job, taskDispatcher)

    // Set up a callback called when the dialog disappears
    val dialogClosedLatch = CountDownLatch(1)
    dialog.onClosedListener = { dialogClosedLatch.countDown() }

    // Show the dialog, release the latch, wait for dialog to disappear
    project.coroutineScope.launch { dialog.show() }
    jobDone.set(true)
    assertThat(dialogClosedLatch.await(5, SECONDS)).isTrue()
    assertThat(job.isCompleted).isTrue()
    assertThat(job.isCancelled).isFalse()
  }

  fun test_job_cancelled() {
    // Set up a job that never finishes
    val job: Job = project.coroutineScope.launch { while (true) delay(50) }
    var cancellationException: Throwable? = null
    job.invokeOnCompletion { t: Throwable? -> cancellationException = t }
    val dialog = ExportInProgressViewImpl(project, job, taskDispatcher)

    // Set up a callback called when the dialog is shown
    val dialogShownLatch = CountDownLatch(1)
    lateinit var progressIndicator: ProgressIndicator
    dialog.onShownListener = {
      progressIndicator = it
      dialogShownLatch.countDown()
    }

    // Set up a callback called when the dialog disappears
    val dialogClosedLatch = CountDownLatch(1)
    dialog.onClosedListener = { dialogClosedLatch.countDown() }

    // Show the dialog
    project.coroutineScope.launch { dialog.show() }
    assertThat(dialogShownLatch.await(5, SECONDS)).isTrue()

    // Simulate cancel click
    assertThat(job.isActive).isTrue()
    assertThat(job.isCancelled).isFalse()
    project.coroutineScope.launch { progressIndicator.cancel() }
    assertThat(dialogClosedLatch.await(5, SECONDS)).isTrue()
    assertThat(job.isCancelled).isTrue()
    assertThat(job.isActive).isFalse()
    assertThat(cancellationException!!).isInstanceOf(UserCancellationException::class.java)
  }
}
