/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.perfetto

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.systemtrace.PerfettoProducer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerfettoProducerTest {

  @Test
  fun validateDataSliceIsSystraceFormat() {
    val parser = PerfettoProducer()
    assertThat(parser.parseFile(CpuProfilerTestUtils.getTraceFile("perfetto.trace"))).isTrue()

    // First line should be a comment line.
    var slice = parser.next()
    assertThat(slice.toString()).startsWith("#")
    // Second line should be nop string.
    slice = parser.next()
    assertThat(slice.toString()).isEqualTo("# tracer: nop\n")
    // Following lines should have a prefix that matches the regex.
    slice = parser.next()
    assertThat(slice.toString()).containsMatch(".*-\\d*     \\(.*\\d*\\) \\[.*\\d\\] d..3 \\d*.\\d{6}: ")
  }

  @Test
  fun validateParsedTraceMarkerEvents() {
    val parser = PerfettoProducer()
    assertThat(parser.parseFile(CpuProfilerTestUtils.getTraceFile("perfetto.trace"))).isTrue()
    var slice = parser.next()
    while (!slice.toString().contains("tracing_mark_write: B")) {
      slice = parser.next()
    }
    assertThat(slice.toString()).containsMatch(".*?tracing_mark_write: .\\|\\d*\\|.*")
  }

  @Test
  fun validateParsedSchedSwitchEvents() {
    val parser = PerfettoProducer()
    assertThat(parser.parseFile(CpuProfilerTestUtils.getTraceFile("perfetto.trace"))).isTrue()
    var slice = parser.next()
    while (!slice.toString().contains("sched_switch: ")) {
      slice = parser.next()
    }
    assertThat(slice.toString()).containsMatch("prev_comm=(.*) prev_pid=(\\d+) prev_prio=(\\d+) prev_state=([^\\s]+) ==> next_comm=(.*) next_pid=(\\d+) next_prio=(\\d+)")
  }

  @Test
  fun validateSchedSwitchMapping() {
    val producer = PerfettoProducer()
    assertThat(producer.mapStateToString(0)).isEqualTo("R")
    assertThat(producer.mapStateToString(1)).isEqualTo("S")
    assertThat(producer.mapStateToString(2)).isEqualTo("D")
    assertThat(producer.mapStateToString(4)).isEqualTo("T")
    assertThat(producer.mapStateToString(8)).isEqualTo("t")
    assertThat(producer.mapStateToString(16)).isEqualTo("Z")
    assertThat(producer.mapStateToString(32)).isEqualTo("X")
    assertThat(producer.mapStateToString(64)).isEqualTo("x")
    assertThat(producer.mapStateToString(128)).isEqualTo("K")
    assertThat(producer.mapStateToString(256)).isEqualTo("W")
    assertThat(producer.mapStateToString(512)).isEqualTo("P")
    assertThat(producer.mapStateToString(1024)).isEqualTo("N")
    assertThat(producer.mapStateToString(17)).isEqualTo("SZ")
    assertThat(producer.mapStateToString(2048)).isEqualTo("R+")
    assertThat(producer.mapStateToString(2049)).isEqualTo("S+")
  }

  @Test
  fun validateClockSyncMarkers() {
    val parser = PerfettoProducer()
    assertThat(parser.parseFile(CpuProfilerTestUtils.getTraceFile("perfetto.trace"))).isTrue()
    // Find first non comment line.
    var slice = parser.next();
    while (slice.toString().startsWith('#')) {
      slice = parser.next();
    }

    // First line should be our parent timestamp.
    // tracing_mark_write: trace_event_clock_sync: parent_ts=%.6f"
    assertThat(slice.toString()).containsMatch(".*: tracing_mark_write: trace_event_clock_sync: parent_ts=\\d+")

    // Second line should be our real timestamp.
    slice = parser.next()
    //"tracing_mark_write: trace_event_clock_sync: realtime_ts=" +
    assertThat(slice.toString()).containsMatch(".*: tracing_mark_write: trace_event_clock_sync: realtime_ts=\\d+")
  }
}
