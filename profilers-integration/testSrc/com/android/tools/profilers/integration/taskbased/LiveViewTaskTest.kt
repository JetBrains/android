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

class LiveViewTaskTest : ProfilersTestBase() {

  /**
   * Validate live view task workflow is working.
   *
   * Test Steps:
   *  1. Import "minApp" in the testData directory of this module.
   *  2. Deploy App and open profiler tool window, set to debuggable mode.
   *  3. Select device -> process -> task (live view)
   *  4. Start the task
   *  5. Stop the session.
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if Transport proxy is created for the device.
   *  3. Verify task start succeeded.
   *  4. Verify session stopped.
   *  5. Verify live view UI components.
   *  6. Verify if the profiler session is still viewable after stopping.
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

        // Profiler starting point for Live View task is limited to "Now". "Profiler from starting" is not available.
        // Selecting live view task.
        selectLiveViewTask(studio)

        // Live View Task is alsways
        // Starting task
        startTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
        verifyIdeaLog(".*PROFILER\\:\\s+Enter\\s+LiveStage", 120)

        Thread.sleep(4000)

        studio.waitForComponentByClass("TooltipLayeredPane", "CpuUsageView")

        stopProfilingSession(studio)

        // Verify if the UI still shows the profiling session.
        studio.waitForComponentByClass("TooltipLayeredPane", "CpuUsageView")
      }
    )
  }
}