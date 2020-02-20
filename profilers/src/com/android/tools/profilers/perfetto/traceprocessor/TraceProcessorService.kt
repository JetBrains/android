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
package com.android.tools.profilers.perfetto.traceprocessor

import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo
import java.io.File

/**
 * This service manages the lifetime and connections to a TraceProcessor Daemon,
 * which is used to parse and analyse Perfetto traces.
 */
interface TraceProcessorService {

  /**
   * Load a Perfetto Trace from {@code traceFile} and assign it the {@code traceId} id internally for future queries.
   *
   * Returns a list of available processes from the trace.
   */
  fun loadTrace(traceId: Long, traceFile: File): List<CpuThreadSliceInfo>
}