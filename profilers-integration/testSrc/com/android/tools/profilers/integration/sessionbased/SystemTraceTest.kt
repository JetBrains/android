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
package com.android.tools.profilers.integration.sessionbased

import com.android.tools.asdriver.tests.Emulator
import com.android.tools.profilers.integration.ProfilersTestBase
import org.junit.Test

class SystemTraceTest : ProfilersTestBase() {

  /**
   * Validate system trace is working.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: a70d00da-9126-4b4e-8803-cab86d010007
   * TT ID: e111a323-ae0c-4439-9eb5-8097fb4f0df9
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import minapp in the testData directory of this module.
   *   2. Start profile 'app' with complete data (using API 33 Play Tiramisu System image).
   *   3. Record system Trace.
   *   4. Stop system trace.
   *   5. Stop profile session.
   *   Verify:
   *   1. Verify logs for starting complete data.
   *   2. Verify logs for starting and stopping system trace.
   *   3. Verify logs for parsing system trace, and system trace containing data.
   *   4. Verify UI components while recording system trace.
   *   5. Verify UI components after system trace is parsed.
   *   5. Verify if the session is stopped.
   *   </pre>
   * <p>
   */
  @Test
  fun testRecordSystemTrace() {
    sessionBasedProfiling(
      systemImage = Emulator.SystemImage.API_33_PlayStore, // Provides more stability than API 29
      testFunction = { studio, adb ->
        // TODO(b/260867011): Remove the wait, once there is a definitive way to tell that the emulator is ready to deploy the app.
        println("Waiting for 20 seconds before running the app so that the emulator is ready")
        Thread.sleep(20000)

        profileWithCompleteData(studio, adb)

        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 300)
        verifyIdeaLog(".*StudioMonitorStage.*PROFILER\\:\\s+Enter\\s+StudioMonitorStage\$", 120)

        studio.waitForComponentByClass("TooltipLayeredPane", "TimelineScrollbar")

        startSystemTrace(studio)

        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+attempted\$", 120)

        studio.waitForComponentByClass("TooltipLayeredPane", "RecordingOptionsView", "FlexibleGrid", "ProfilerCombobox")

        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+succeeded\$", 120)

        stopCpuCapture(studio)

        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+attempted\$", 120)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)

        // Verify if the cpu capture is parsed.
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+parse\\s+succeeded\$", 600)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+contains\\s+system\\s+trace\\s+data\$", 600)

        studio.waitForComponentByClass("TooltipLayeredPane", "CpuAnalysisSummaryTab", "UsageInstructionsView")

        stopProfilingSession(studio)
      }
    )
  }
}