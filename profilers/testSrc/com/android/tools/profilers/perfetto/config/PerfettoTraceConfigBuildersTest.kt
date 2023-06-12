/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.profilers.perfetto.config

import com.google.common.truth.Truth
import org.junit.Test
import perfetto.protos.PerfettoConfig

class PerfettoTraceConfigBuildersTest {

  @Test
  fun cpuTraceConfigConstructedCorrectly() {
    val config = PerfettoTraceConfigBuilders.getCpuTraceConfig(1234)

    Truth.assertThat(config.buffersCount).isEqualTo(3)
    Truth.assertThat(config.getBuffers(0).sizeKb).isEqualTo(1234 * 1024)
    // 256 Kb is the hardcoded value used for the secondary buffer
    Truth.assertThat(config.getBuffers(1).sizeKb).isEqualTo(256)
    Truth.assertThat(config.dataSourcesCount).isEqualTo(8)
    val actualDataSources = config.dataSourcesList

    // Verify first data source (Ftrace Data Source) is built correctly.
    Truth.assertThat(actualDataSources[0].config.name).isEqualTo("linux.ftrace")
    Truth.assertThat(actualDataSources[0].config.targetBuffer).isEqualTo(0)
    Truth.assertThat(actualDataSources[0].config.ftraceConfig.drainPeriodMs).isEqualTo(170)
    Truth.assertThat(actualDataSources[0].config.ftraceConfig.compactSched.enabled).isTrue()
    Truth.assertThat(actualDataSources[0].config.ftraceConfig.ftraceEventsList).containsExactly("thermal/thermal_temperature",
                                                                                                "perf_trace_counters/perf_trace_user",
                                                                                                "fence/signaled",
                                                                                                "fence/fence_wait_start",
                                                                                                "power/cpu_frequency",
                                                                                                "power/cpu_idle",
                                                                                                "task/task_rename",
                                                                                                "task/task_newtask"
    )
    Truth.assertThat(actualDataSources[0].config.ftraceConfig.atraceCategoriesList).containsExactly(
      "gfx", "input", "view", "wm", "am", "sm", "camera", "hal", "res", "pm", "ss", "power", "database", "dalvik", "audio",
      "binder_driver", "binder_lock", "sched", "freq"
    )
    Truth.assertThat(actualDataSources[0].config.perfEventConfig.allCpus).isTrue()
    Truth.assertThat(actualDataSources[0].config.ftraceConfig.atraceAppsCount).isEqualTo(1)

    // Verify second data source (First Process and Thread Names) is built correctly.
    Truth.assertThat(actualDataSources[1].config.name).isEqualTo("linux.process_stats")
    Truth.assertThat(actualDataSources[1].config.targetBuffer).isEqualTo(1)
    Truth.assertThat(actualDataSources[1].config.processStatsConfig.scanAllProcessesOnStart).isTrue()
    Truth.assertThat(actualDataSources[1].config.processStatsConfig.recordThreadNames).isTrue()

    // Verify second data source (Second Process and Thread Names) is built correctly.
    Truth.assertThat(actualDataSources[2].config.name).isEqualTo("linux.process_stats")
    Truth.assertThat(actualDataSources[2].config.targetBuffer).isEqualTo(0)
    Truth.assertThat(actualDataSources[2].config.processStatsConfig.procStatsPollMs).isEqualTo(1000)
    Truth.assertThat(actualDataSources[2].config.processStatsConfig.quirksList).containsExactly(
      PerfettoConfig.ProcessStatsConfig.Quirks.DISABLE_ON_DEMAND)

    // Verify second data source (CPU Info) is built correctly.
    Truth.assertThat(actualDataSources[3].config.name).isEqualTo("linux.system_info")

    // Verify second data source (Lifecycle Data) is built correctly.
    Truth.assertThat(actualDataSources[4].config.name).isEqualTo("android.surfaceflinger.frame")

    // Verify second data source (Frame Timeline Data) is built correctly.
    Truth.assertThat(actualDataSources[5].config.name).isEqualTo("android.surfaceflinger.frametimeline")

    // Verify second data source (TrackEvent API Data) is built correctly.
    Truth.assertThat(actualDataSources[6].config.name).isEqualTo("track_event")

    // Verify second data source (Power Data) is built correctly.
    Truth.assertThat(actualDataSources[7].config.name).isEqualTo("android.power")
    Truth.assertThat(actualDataSources[7].config.targetBuffer).isEqualTo(2)
    Truth.assertThat(actualDataSources[7].config.androidPowerConfig.collectPowerRails).isTrue()
    Truth.assertThat(actualDataSources[7].config.androidPowerConfig.batteryCountersList).containsExactly(
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CAPACITY_PERCENT,
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CHARGE,
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CURRENT
    )
  }

  @Test
  fun memoryTraceConfigConstructedCorrectly() {
    val config = PerfettoTraceConfigBuilders.getMemoryTraceConfig("foo", 1234L)

    Truth.assertThat(config.buffersCount).isEqualTo(1)
    Truth.assertThat(config.getBuffers(0).sizeKb).isEqualTo(1024 * 128)
    Truth.assertThat(config.dataSourcesCount).isEqualTo(1)
    val actualDataSources = config.dataSourcesList

    // Verify first and only data source (heap profd Data Source) is built correctly.
    Truth.assertThat(actualDataSources[0].config.name).isEqualTo("android.heapprofd")
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.samplingIntervalBytes).isEqualTo(1234L)
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.processCmdlineList).containsExactly("foo")
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.shmemSizeBytes).isEqualTo(64 * 1024 * 1024L)
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.allHeaps).isEqualTo(true)
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.blockClient).isEqualTo(true)
    Truth.assertThat(actualDataSources[0].config.heapprofdConfig.continuousDumpConfig.dumpIntervalMs).isEqualTo(0)
  }
}