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

import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.profilers.integration.ProfilersStartupTaskTestBase
import org.junit.Test

class StartupSystemTraceTaskTest : ProfilersStartupTaskTestBase() {

  override fun selectTask(studio: AndroidStudio) {
    selectSystemTraceTask(studio)
  }

  override fun verifyTaskStarted(studio: AndroidStudio) {
    verifyIdeaLog(
      ".*Attempting\\sto\\sstart\\sthe\\s\\'System Trace\\'\\stask\\sfrom\\sprocess\\sstart\\s\\(startup\\)\\.\$",
      300)
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 300)
  }

  override fun verifyTaskStopped(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)
    verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+contains\\s+system\\s+trace\\s+data\$", 600)
  }

  override fun verifyUIComponents(studio: AndroidStudio) {
    studio.waitForComponentByClass("CpuAnalysisSummaryTab", "FullTraceSummaryDetailsView")
  }

  /**
   * Validate system trace task workflow is working.
   *
   * Test Steps:
   *  1. Import "minApp" in the testData directory of this module.
   *  2. Open Profiler tool window
   *  3. Select device -> process -> task-> starting point.
   *  4. Start the task
   *  5. Stop the task
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if the app is launched successfully
   *  3. Verify task start succeeded.
   *  4. Verify task stop succeeded.
   *  5. Verify if the capture is parsed successfully.
   *  6. Verify UI components after capture is parsed.
   */
  @Test
  fun test() = testStartUpTask()
}
