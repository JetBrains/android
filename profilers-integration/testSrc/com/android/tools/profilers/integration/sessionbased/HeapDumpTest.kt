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

class HeapDumpTest  : ProfilersTestBase() {

  /**
   * Test heap dump recording.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ed26bf9d-2101-4673-8162-ffe1e9d26134
   * TT ID: 5908263e-9225-453e-a188-a87512104492
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import minapp in the testData directory of this module.
   *   2. Start profile 'app' with complete data.
   *   3. Start heap dump capture.
   *   4. Stop profile session.
   *   Verify:
   *   1. Verify if the complete data session started.
   *   2. Verify if heap dump is started.
   *   3. Verify UI Components in profiler tool window.
   *   4. Verify if the session is stopped.
   *   </pre>
   * <p>
   */
  @Test
  fun testRecordHeapDump() {
    sessionBasedProfiling(
      systemImage = Emulator.SystemImage.API_33_PlayStore, // Provides more stability than API 29
      testFunction = { studio, adb ->
        Thread.sleep(20000)

        profileWithCompleteData(studio, adb)

        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 300)
        verifyIdeaLog(".*StudioMonitorStage.*PROFILER\\:\\s+Enter\\s+StudioMonitorStage\$", 120)

        studio.waitForComponentByClass("TooltipLayeredPane", "TimelineScrollbar")

        startHeapDump(studio)

        verifyIdeaLog(".*PROFILER\\:\\s+Heap\\s+dump\\s+capture\\s+start\\s+succeeded", 120)
        // Heap dump capture might take a few seconds to minutes, additional timeout is required
        verifyIdeaLog(".*PROFILER\\:\\s+Heap\\s+dump\\s+capture\\s+has\\s+finished", 450)

        studio.waitForComponentByClass("TooltipLayeredPane", "CapturePanelUi")

        stopProfilingSession(studio)
      }
    )
  }
}