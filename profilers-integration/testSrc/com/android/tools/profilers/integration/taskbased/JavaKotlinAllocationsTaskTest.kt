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
package com.android.tools.profilers.integration.taskbased

import com.android.tools.asdriver.tests.Emulator
import com.android.tools.profilers.integration.ProfilersTestBase
import org.junit.Test

class JavaKotlinAllocationsTaskTest : ProfilersTestBase() {

  /**
   * Validate java/kotlin allocations task workflow (for O+ devices) is working.
   *
   * Test Steps:
   *  1. Import "minApp" in the testData directory of this module.
   *  2. Deploy App and open profiler tool window, set to debuggable mode.
   *  3. Select device -> process -> task-> starting point.
   *  4. Start the task
   *  5. Stop the task
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if Transport proxy is created for the device.
   *  3. Verify task start succeeded.
   *  4. Verify task stop succeeded.
   *  5. Verify if the capture is parsed successfully.
   *  6. Verify UI components after capture is parsed.
   */
  @Test
  fun test() {
    taskBasedProfiling(
      systemImage = Emulator.SystemImage.API_33,
      deployApp = true,
      testFunction = { studio, _ ->
        // Selecting the device.
        selectDevice(studio)

        // Selecting the process id which consists of `minapp`
        selectProcess(studio)

        // Selecting native allocations task.
        selectJavaKotlinAllocationsTask(studio)

        // Set profiling starting point
        setProfilingStartingPointToNow(studio)

        // Starting Task
        startTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 180)
        verifyIdeaLog(".*PROFILER\\:\\s+Enter\\s+AllocationStage", 120)

        // Collecting the session data with Allocation tracking set to "Full" (which is by default)
        // Verifying if the task started.
        verifyIdeaLog(".*PROFILER\\:\\s+Java\\/Kotlin\\s+Allocations\\s+capture\\s+start\\s+succeeded", 120)
        studio.waitForComponentByClass("profilers.memory.AllocationTimelineComponent")

        Thread.sleep(4000) //For the session to run a few seconds.

        // Since the java/kotlin allocations for O+ devices, stopTracking method is being invoked using stopJavaKotlinAllocationsTask.
        // For Pre-O devices, stopTask method should be invoked
        stopJavaKotlinAllocationsTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Java\\/Kotlin\\s+Allocations\\s+capture\\s+stop\\s+succeeded", 300)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+stopped.*support\\s+level\\s+\\=DEBUGGABLE\$", 300)

        // Verify if the session is displayed after session ended.
        studio.waitForComponentByClass("profilers.memory.AllocationTimelineComponent")
      }
    )
  }
}