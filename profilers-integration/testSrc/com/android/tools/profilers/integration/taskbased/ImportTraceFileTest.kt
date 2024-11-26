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

import com.android.tools.profilers.integration.ProfilersTestBase
import org.junit.Test
import kotlin.io.path.name

class ImportTraceFileTest : ProfilersTestBase() {

  /**
   * Validate Importing trace files are working along with opening a recording from recordings tab.
   *
   * Test Steps:
   *  1. Import "minApp" from the testData directory of this module.
   *  2. Open Profiler tool window and set hide new task prompt to true.
   *  3. Open a CPU recording.
   *  4. Open a memory recording.
   *  5. Open recordings tab.
   *  6. Open the last recording from the past recordings list.
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if the CPU trace is opened.
   *  3. Verify if the memory trace is opened.
   *  4. Verify if the trace can be opened from the past recordings list.
   */
  @Test
  fun testOpeningTraceFiles() {
    setupProjectWithoutEmulator(
      testFunction = { studio, project ->
        invokeProfilerToolWindow(studio)
        setHideNewTaskPromptToTrue(studio)

        // Import CPU trace
        studio.openFile(project.targetProject.name, "sampleTaskRecordings/cpu-simpleperf.trace")
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+parse\\s+succeeded\$", 300)
        studio.waitForComponentByClass("CpuAnalysisSummaryTab", "UsageInstructionsView")

        Thread.sleep(2000)
        // Import memory trace.
        studio.openFile(project.targetProject.name, "sampleTaskRecordings/memory.heapprofd")
        studio.waitForComponentByClass("TooltipLayeredPane", "CapturePanelUi")

        Thread.sleep(2000)
        // open recording tab
        openPastRecordingsTab(studio)
        selectLastRecordingFromRecordingList(studio)
        studio.invokeComponent("Open profiler task")
        // Verifying CpuAnalysisSummaryTab component, since the last recording listed should be cpu-simpleperf.trace,
        // as this was the first one opened in the test.
        studio.waitForComponentByClass("CpuAnalysisSummaryTab", "UsageInstructionsView")
      }
    )
  }
}