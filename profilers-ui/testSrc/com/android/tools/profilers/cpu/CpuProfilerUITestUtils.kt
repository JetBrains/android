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

object CpuProfilerUITestUtils {
  private const val VALID_TRACE_FILE = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"

  // We use a different path than in "profilers" modules, because resources from "profilers" modules is not accessible from "profilers-ui".
  fun validCapture() = CpuProfilerTestUtils.getCapture(VALID_TRACE_FILE)
}
