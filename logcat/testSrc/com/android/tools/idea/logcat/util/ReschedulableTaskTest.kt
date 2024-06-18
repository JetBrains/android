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
package com.android.tools.idea.logcat.util

import com.android.testutils.MockitoKt.mock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/** Tests for [ReschedulableTask] */
@Suppress("OPT_IN_USAGE") // runTest is experimental
class ReschedulableTaskTest {

  @Test
  fun runsDelayed() =
    runTest(timeout = 5.seconds) {
      val reschedulableTask = ReschedulableTask(this)
      val mockTask = mock<Runnable>()

      val task = mockTask::run
      reschedulableTask.reschedule(1000, task)

      verify(mockTask, never()).run()
      testScheduler.advanceTimeBy(1010)
      testScheduler.runCurrent()
      verify(mockTask).run()
    }

  @Test
  fun rescheduled_runsOnce() =
    runTest(timeout = 5.seconds) {
      val reschedulableTask = ReschedulableTask(this)
      val mockTask = mock<Runnable>()

      reschedulableTask.reschedule(1000, mockTask::run)
      testScheduler.advanceTimeBy(500)
      testScheduler.runCurrent()
      reschedulableTask.reschedule(1000, mockTask::run)

      testScheduler.advanceTimeBy(1010)
      testScheduler.runCurrent()
      verify(mockTask).run()
    }
}
