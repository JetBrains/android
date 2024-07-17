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
import org.junit.Ignore
import org.junit.Test

class JavaKotlinMethodRecordingTaskTest : ProfilersTestBase() {

  /**
   * Validate live Java/Kotlin method recording workflow is working.
   *
   * Test Steps:
   *  1. Import "minApp" in the testData directory of this module.
   *  2. Deploy App and open profiler tool window, set to debuggable mode.
   *  3. Select device -> process -> task (java/kotlin method recording) -> Set recording to Sample
   *  4. Start the task
   *  5. Stop the task.
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if Transport proxy is created for the device.
   *  3. Verify task start succeeded.
   *  4. Verify session stopped.
   *  5. Verify live view UI components.
   *  6. Verify if the profiler session is still viewable after stopping.
   */
  @Ignore("b/355647718")
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

        setProfilingStartingPointToNow(studio)

        // Selecting Java/Kotlin Method recording task.
        selectJavaKotlinMethodRecordingTask(studio)

        // Set recording type to sampling
        setRecordingTypeToSampling(studio)

        // Starting task
        startTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+succeeded\$", 120)
        Thread.sleep(4000)

        stopTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)

        // Verify if the cpu capture is parsed.
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+parse\\s+succeeded\$", 300)
        studio.waitForComponentByClass("CpuAnalysisSummaryTab", "FullTraceSummaryDetailsView")
      }
    )
  }
}