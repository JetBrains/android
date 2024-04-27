/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.integration

import com.android.tools.asdriver.tests.Emulator
import org.junit.Test

class ProfileWithLowOverheadTest: ProfilersTestBase() {

  /**
   * Validate “Profile ‘app’ with LowOverhead” is  working.
   * <p>
   *  This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   *  TT ID: e79493c3-9b2b-4e61-b629-93421a3b2fb9
   * <p>
   *  <pre>
   *   Test Steps:
   *   1. Import minapp in the testData directory of this module.
   *   2. Start profile 'app' with lowOverhead.
   *   3. Stop profile session.
   *   Verify:
   *   1. Verify if the low overhead session started.
   *   2. Verify UI Components in profiler tool window.
   *   3. Verify if the session is stopped.
   *  </pre>
   * <p>
   */
  @Test
  fun testLowOverheadSession() {
    profileApp(
      systemImage = Emulator.SystemImage.API_33_PlayStore,
      testFunction = { studio, _ ->
        // Since there is no definitive way to tell that the emulator is ready
        // TODO(b/260867011): Remove the wait, once there is a definitive way to tell that the emulator is ready to deploy the app.
        println("Waiting for 20 seconds before running the app so that the emulator is ready")
        Thread.sleep(20000)

        profileWithLowOverhead(studio)
        // TODO(b/260296636): Reduce the time-out to 180, once the performance issue b/260296636 is fixed.
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=PROFILEABLE\$", 480)
        verifyIdeaLog(".*StudioMonitorStage.*PROFILER\\:\\s+Enter\\s+StudioMonitorStage\$", 300)

        studio.waitForComponentByClass("TooltipLayeredPane", "TimelineScrollbar")

        stopProfilingSession(studio)
      }
    )
  }
}