/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.performance

import com.android.tools.idea.profilers.perfetto.traceprocessor.TraceProcessorServiceImpl
import com.android.tools.profilers.cpu.CpuProfilerTestUtils.getTraceFile
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Perfgate test for measuring the memory overhead of loading / parsing a perfetto capture with TraceProcessorService backend.
 * Note: This test is in its own class due to weak/soft reference leaks if the test runner runs other performance test these references
 * impact the memory results of this test. Without any way to force the GC this is the only reliable way to get a stable memory record.
 */
class CpuProfilerPerfettoCaptureTest : CpuProfilerMemoryLoadTestBase() {
  lateinit var traceProcessorService: TraceProcessorServiceImpl

  @Before
  fun setUp() {
    // Use a real instance of TPD Service instead of the fake one.
    traceProcessorService = TraceProcessorServiceImpl()
    myIdeServices.traceProcessorService = traceProcessorService
  }

  @After
  fun tearDown() {
    // Dispose it to make sure we shutdown the daemon so we don't leave dangling processes.
    Disposer.dispose(traceProcessorService)
  }

  @Test
  fun measureMemoryOfImportPerfettoWithTPD() {
    myIdeServices.enableUseTraceProcessor(true)
    loadCaptureAndReport("Perfetto-TPD-10-sec", getTraceFile("performance/perfetto_10s_tanks.trace"))
  }
}

