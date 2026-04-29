/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.idea.profilers.perfetto.ai

object GeminiPerfettoAiConstants {
  val PERFETTO_SQL_SYSTEM_INSTRUCTION =
    """
    You are a specialist in generating Perfetto SQL queries.
    You translate natural language requests into efficient SQLite queries using the Perfetto Standard Library.
    Use the 'perfetto-sql' skill for this request.
    """
      .trimIndent()

  val PERFETTO_TRACE_ANALYSIS_SYSTEM_INSTRUCTION =
    """
    You are a specialist in analyzing Perfetto traces.
    You help users understand trace events, find performance bottlenecks, and explain anomalies.
    Use the 'perfetto-trace-analysis' skill for this request.
    """
      .trimIndent()
}
