/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.profilers.integration.ProfilersTestBase
import org.junit.Test

class ProfileWithCompleteDataTest : ProfilersTestBase() {

  /**
   * Validate “Profile ‘app’ with complete data” is  working.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 0ed907de-b0d9-414e-9677-41949626a434
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import minapp in the testData directory of this module.
   *   2. Start profile 'app' with complete data.
   *   Verify:
   *   1. Verify if the complete data task started.
   *   2. Verify in the logs that the running debuggable process is found.
   *   </pre>
   * <p>
   */
  @Test
  fun testProfileAppWithComplete() {
    taskBasedProfiling(
      deployApp=false,
      testFunction = { studio, adb ->
        profileWithCompleteData(studio, adb)

        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
        verifyIdeaLog(".*StudioMonitorStage.*PROFILER\\:\\s+Enter\\s+StudioMonitorStage\$", 120)

        verifyIdeaLog("Found running project process: \\d+, Debuggable", 120)
      }
    )
  }
}