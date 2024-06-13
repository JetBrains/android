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

class NativeAllocationsTest : ProfilersTestBase() {

  /**
   * Test to record Native allocations capture.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d5fb6ced-def7-43f7-838a-cfd5010af9ba
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import `minapp` from the testData directory of this module.
   *   2. Start profile 'app' with complete data.
   *   3. Start Native Allocations capture.
   *   4. Stop Native Allocations
   *   4. Stop profile session.
   *   Verify:
   *   1. Verify if the complete data session started.
   *   2. Verify if Native Allocations capture is started.
   *   3. Verify if Native Allocations capture is stopped.
   *   4. Verify UI Components in profiler tool window.
   *   5. Verify if the session is stopped.
   *   </pre>
   * <p>
   */
  @Test
  fun testRecordNativeAllocation() {
    sessionBasedProfiling(
      systemImage = Emulator.SystemImage.API_31, // Provides more stability than API 29
      testFunction = { studio, adb ->
        profileWithCompleteData(studio, adb)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 300)
        verifyIdeaLog(".*StudioMonitorStage.*PROFILER\\:\\s+Enter\\s+StudioMonitorStage\$", 120)

        studio.waitForComponentByClass("TooltipLayeredPane", "TimelineScrollbar")

        startNativeAllocations(studio)

        verifyIdeaLog(".*PROFILER\\:\\s+Native\\s+allocations\\s+capture\\s+start\\s+succeeded", 300)

        studio.waitForComponentByClass("TooltipLayeredPane", "MemoryTimelineComponent")

        stopNativeAllocations(studio)

        verifyIdeaLog(".*PROFILER\\:\\s+Native\\s+allocations\\s+capture\\s+stop\\s+succeeded", 300)

        studio.waitForComponentByClass("TooltipLayeredPane", "CapturePanelUi")

        stopProfilingSession(studio)
      }
    )
  }
}