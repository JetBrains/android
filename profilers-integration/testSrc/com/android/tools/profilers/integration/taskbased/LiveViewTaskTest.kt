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
import com.android.tools.profilers.integration.ProfilersTaskTestBase

import org.junit.Test

class LiveViewTaskTest : ProfilersTaskTestBase() {

  override fun selectTask(studio: AndroidStudio) {
    selectLiveViewTask(studio)
  }

  override fun verifyTaskStarted(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
    verifyIdeaLog(".*PROFILER\\:\\s+Enter\\s+LiveStage", 120)
  }

  override fun verifyTaskStopped(studio: AndroidStudio) {
    studio.waitForComponentByClass("TooltipLayeredPane", "CpuUsageView")
  }

  override fun verifyUIComponents(studio: AndroidStudio) {
    studio.waitForComponentByClass("TooltipLayeredPane", "CpuUsageView")
  }

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
  fun test() = testTask()
}