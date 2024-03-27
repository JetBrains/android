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

class TaskBasedProfilingWithApkTest : ProfilersTestBase() {

  /**
   * Validate Profile with APK.
   *
   * Test Steps:
   *  1. Import minApp apk
   *  2. Run profiler action and wait for the tool window to be activated.
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
    profileAppUsingApk(
      systemImage = Emulator.SystemImage.API_33,
      enableTaskBasedProfiling = true,
      testFunction = { studio, adb ->
        // Invoking profiler tool window using ProfileAction
        // Which follows the code path of invoking the profiler button.
        profileAction(studio)
        waitForAppToBeDeployed(adb, ".*Hello Minimal World!.*")
        waitForProfilerTaskBasedToolWindowToBeActivated(studio)
        waitForProfilerDeviceConnection()

        Thread.sleep(2000)

        // Selecting the device.
        selectDevice(studio)

        // Selecting the process id which consists of `minapp`
        selectProcess(studio)

        // Selecting callstack sample task.
        selectSystemTraceTask(studio)

        // Select Process start to "Now"
        setProfilingStartingPointToNow(studio)

        startTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 240)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+attempted\$", 120)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+succeeded\$", 120)
        Thread.sleep(4000) // This sleep is to let the profiler session run for a few seconds before stopping it.

        stopTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+attempted\$", 120)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)

        // Verify if the cpu capture is parsed.
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+parse\\s+succeeded\$", 600)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+contains\\s+system\\s+trace\\s+data\$", 600)
        // Verify if UI panel is displayed.
        studio.waitForComponentByClass("CpuAnalysisSummaryTab", "FullTraceSummaryDetailsView")
      }
    )
  }
}