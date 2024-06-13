/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.profilers.StudioProfilers

object CpuProfilerUITestUtils {
  const val VALID_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"
  const val EMPTY_SIMPLEPERF_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/simpleperf_empty.trace"
  const val ATRACE_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"
  const val ATRACE_PID1_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/atrace_processid_1.ctrace"

  // We use a different path than in "profilers" modules, because resources from "profilers" modules is not accessible from "profilers-ui".
  fun validCapture(profilers: StudioProfilers) = CpuProfilerTestUtils.getCapture(profilers, VALID_TRACE_PATH)
  fun getTraceContents(path: String) = CpuProfilerTestUtils.traceFileToByteString(resolveWorkspacePath(path).toFile())
}
