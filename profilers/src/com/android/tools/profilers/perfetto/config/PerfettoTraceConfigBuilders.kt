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

import perfetto.protos.PerfettoConfig
import perfetto.protos.PerfettoConfig.TraceConfig

object PerfettoTraceConfigBuilders {
  private val counterFtraceEvents = listOf("thermal/thermal_temperature", "perf_trace_counters/perf_trace_user")
  private val fenceFtraceEvents = listOf("fence/signaled", "fence/fence_wait_start")
  private val cpuFreqFtraceEvents = listOf("power/cpu_frequency", "power/cpu_idle")
  private val taskTrackingFtraceEvents = listOf("task/task_rename", "task/task_newtask")

  private val standardAtraceCategories = listOf("gfx", "input", "view", "wm", "am", "sm", "camera", "hal", "res", "pm", "ss", "power",
                                                "database", "dalvik", "audio",
                                                "binder_driver", "binder_lock")
  private val verboseAtraceCategories = listOf("sched", "freq")
  private val batteryCounters = listOf(PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CAPACITY_PERCENT,
                                       PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CHARGE,
                                       PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CURRENT)

  // Ftrace config values.
  private const val FTRACE_DRAIN_PERIOD_MS = 170
  private const val FTRACE_COMPACT_SCHED_BUILDER_ENABLED = true
  private const val PERF_EVENT_ALL_CPUS_ENABLED = true
  private const val SHARED_MEM_BUFFER_BYTES = 64 * 1024 * 1024L

  // Process and thread names config values.
  private const val PROC_STATS_SCAN_ALL_PROCESS_ON_START_ENABLED = true
  private const val PROC_STATS_RECORD_THREAD_NAMES_ENABLED = true

  // Process stats data config values.
  private const val PROCESS_STATS_POLL_INTERVAL_MS = 1000

  // Power and battery data config values.
  private const val BATTERY_POLL_INTERVAL_MS = 1000

  // Buffer size values.
  private const val CPU_TRACE_CONFIG_BUFFER_1_SIZE_KB = 256
  private const val CPU_TRACE_CONFIG_BUFFER_2_SIZE_KB = 4096
  private const val MEMORY_TRACE_CONFIG_BUFFER_0_SIZE_KB = 1024 * 128

  // Heap profd config values.
  private const val CONTINUOUS_DUMP_INTERVAL_MS = 0
  private const val HEAP_PROFD_ALL_HEAPS_ENABLED = true
  private const val HEAP_PROFD_BLOCK_CLIENT_ENABLED = true

  // Common trace config values.
  private const val FLUSH_PERIOD_MS = 1000
  private const val FILE_WRITE_PERIOD_MS = 250
  private const val WRITE_INTO_FILE_ENABLED = true

  // Small helper method to do conversion from mb to kb.
  private fun Int.mbToKb(): Int = this * 1024

  /**
   * Constructs a Perfetto TraceConfig utilized for cpu tracing (System Trace).
   */
  fun getCpuTraceConfig(bufferSizeMb: Int): TraceConfig {
    var config = buildCommonTraceConfig()
    val configBuilder = config.toBuilder()

    // add buffers
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(bufferSizeMb.mbToKb()))
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(CPU_TRACE_CONFIG_BUFFER_1_SIZE_KB))
    // This buffer is exclusively for the 'android.power' data source.
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(CPU_TRACE_CONFIG_BUFFER_2_SIZE_KB))

    // add ftrace data source
    val ftraceDataSource = TraceConfig.DataSource.newBuilder().setConfig(getFtraceDataConfig())

    // Add config to get process and thread names.
    // This is required to properly parse perfetto captures with trebuchet.
    val processAndThreadNamesDataSource = TraceConfig.DataSource.newBuilder().setConfig(getProcessAndThreadNamesDataConfig())

    // Split process/thread scan and /proc/stat so that they don't share the same buffer.
    val procStatDataSource = TraceConfig.DataSource.newBuilder().setConfig(getProcStatDataConfig())

    // Add config to get CPU information from procfs and sysfs.
    val cpuInfoDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("linux.system_info")
    val cpuInfoDataSource = TraceConfig.DataSource.newBuilder().setConfig(cpuInfoDataConfig)

    // These data sources will be ignored on unsupported Android version.
    // Add data source to get Android frame lifecycle data (R+).
    val lifecycleDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("android.surfaceflinger.frame")
    val lifeCycleDataSource = TraceConfig.DataSource.newBuilder().setConfig(lifecycleDataConfig)

    // Add data source to get Android frame timeline data (S+).
    val frameTimelineDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("android.surfaceflinger.frametimeline")
    val frameTimelineDataSource = TraceConfig.DataSource.newBuilder().setConfig(frameTimelineDataConfig)

    // Perfetto SDK TrackEvent API
    val trackEventDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("track_event")
    val trackEventDataSource = TraceConfig.DataSource.newBuilder().setConfig(trackEventDataConfig)

    // Add power and battery drain data.
    val powerDataSource = TraceConfig.DataSource.newBuilder().setConfig(getPowerDataConfig())

    configBuilder.addDataSources(ftraceDataSource)
    configBuilder.addDataSources(processAndThreadNamesDataSource)
    configBuilder.addDataSources(procStatDataSource)
    configBuilder.addDataSources(cpuInfoDataSource)
    configBuilder.addDataSources(lifeCycleDataSource)
    configBuilder.addDataSources(frameTimelineDataSource)
    configBuilder.addDataSources(trackEventDataSource)
    configBuilder.addDataSources(powerDataSource)

    return configBuilder.build()
  }

  private fun getFtraceDataConfig(): PerfettoConfig.DataSourceConfig {
    val ftraceDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("linux.ftrace").setTargetBuffer(0)

    val ftraceConfig = ftraceDataConfig.ftraceConfig.toBuilder()
    // Drain ftrace every 10frames @ 60fps
    ftraceConfig.drainPeriodMs = FTRACE_DRAIN_PERIOD_MS

    // Enable "compact sched", which significantly reduces the bandwidth taken by
    // sched events by proto encoding in a more efficient way. Supported on R+.
    // No effect on Q-.
    ftraceConfig.compactSchedBuilder.enabled = FTRACE_COMPACT_SCHED_BUILDER_ENABLED

    // Enable more counters
    ftraceConfig.addAllFtraceEvents(counterFtraceEvents)

    // If this event is reported by the OS. Fenced events will help users track
    // synchronization issues. Most commonly fences are used to guard buffers
    // used by kernel level drivers (eg. GPU). They are captured when the driver
    // needs to do work and they are signaled when the driver is done.
    ftraceConfig.addAllFtraceEvents(fenceFtraceEvents)

    // Enable CPU frequency events
    ftraceConfig.addAllFtraceEvents(cpuFreqFtraceEvents)

    // Enable task tracking
    // This enables us to capture events/metadata that helps to track
    // processes/threads as they get renamed/spawned. Reduces the number of
    // processes/threads with only PIDs and no name attached to them in the
    // capture.
    ftraceConfig.addAllFtraceEvents(taskTrackingFtraceEvents)

    // Standard set of atrace categories
    ftraceConfig.addAllAtraceCategories(standardAtraceCategories)

    // Very verbose atrace categories
    ftraceConfig.addAllAtraceCategories(verboseAtraceCategories)

    // Enable perf counters (mem / oom score / HW VSYNC)
    val perfEventConfig = ftraceDataConfig.perfEventConfig.toBuilder()
    perfEventConfig.allCpus = PERF_EVENT_ALL_CPUS_ENABLED
    ftraceDataConfig.perfEventConfig = perfEventConfig.build()

    // In P and above "*" is supported, if we move to support O we will want to
    // pass in the |app_pkg_name|
    ftraceConfig.addAtraceApps("*");

    ftraceDataConfig.ftraceConfig = ftraceConfig.build()

    return ftraceDataConfig.build()
  }

  private fun getProcessAndThreadNamesDataConfig(): PerfettoConfig.DataSourceConfig {
    val processAndThreadNamesDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("linux.process_stats").setTargetBuffer(1)
    val procStatsConfig = processAndThreadNamesDataConfig.processStatsConfig.toBuilder()
    procStatsConfig.scanAllProcessesOnStart = PROC_STATS_SCAN_ALL_PROCESS_ON_START_ENABLED
    procStatsConfig.recordThreadNames = PROC_STATS_RECORD_THREAD_NAMES_ENABLED

    processAndThreadNamesDataConfig.processStatsConfig = procStatsConfig.build()

    return processAndThreadNamesDataConfig.build()
  }

  private fun getProcStatDataConfig(): PerfettoConfig.DataSourceConfig {
    val procStatDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("linux.process_stats").setTargetBuffer(0)
    val procStatProcessStatsConfig = procStatDataConfig.processStatsConfig.toBuilder()
    procStatProcessStatsConfig.procStatsPollMs = PROCESS_STATS_POLL_INTERVAL_MS
    procStatProcessStatsConfig.addQuirks(PerfettoConfig.ProcessStatsConfig.Quirks.DISABLE_ON_DEMAND)

    procStatDataConfig.processStatsConfig = procStatProcessStatsConfig.build()

    return procStatDataConfig.build()
  }

  private fun getPowerDataConfig(): PerfettoConfig.DataSourceConfig {
    val powerDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("android.power").setTargetBuffer(2)
    val androidPowerConfig = powerDataConfig.androidPowerConfig.toBuilder()
    androidPowerConfig.collectPowerRails = true
    androidPowerConfig.batteryPollMs = BATTERY_POLL_INTERVAL_MS
    androidPowerConfig.addAllBatteryCounters(batteryCounters)

    powerDataConfig.androidPowerConfig = androidPowerConfig.build()

    return powerDataConfig.build()
  }

  /**
   * Constructs a Perfetto TraceConfig utilized for memory tracing (Native Allocation Tracking).
   */
  fun getMemoryTraceConfig(appPkgNameOrPid: String, samplingIntervalBytes: Long): TraceConfig {
    var config = buildCommonTraceConfig()
    val configBuilder = config.toBuilder()

    // Set an arbitrary buffer size that is not unreasonable to request, and
    // allows us a reasonable size  to not overflow.
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(MEMORY_TRACE_CONFIG_BUFFER_0_SIZE_KB))

    val heapProfdDataSource = TraceConfig.DataSource.newBuilder().setConfig(
      getHeapProfdDataConfig(appPkgNameOrPid, samplingIntervalBytes))
    configBuilder.addDataSources(heapProfdDataSource)

    return configBuilder.build()
  }

  private fun getHeapProfdDataConfig(appPkgNameOrPid: String,
                                     samplingIntervalBytes: Long): PerfettoConfig.DataSourceConfig {
    val heapProfdDataConfig = PerfettoConfig.DataSourceConfig.newBuilder().setName("android.heapprofd")
    val heapProfdConfig = heapProfdDataConfig.heapprofdConfig.toBuilder()
    heapProfdConfig.samplingIntervalBytes = samplingIntervalBytes
    heapProfdConfig.addProcessCmdline(appPkgNameOrPid)
    heapProfdConfig.shmemSizeBytes = SHARED_MEM_BUFFER_BYTES

    val continuousDumpConfig = heapProfdConfig.continuousDumpConfig.toBuilder()
    continuousDumpConfig.dumpIntervalMs = CONTINUOUS_DUMP_INTERVAL_MS
    // Record allocations from all heaps (including custom).
    heapProfdConfig.allHeaps = HEAP_PROFD_ALL_HEAPS_ENABLED

    // If heapprofd cannot keep up with the rate of samples, the target process
    // will stall the malloc until heapprofd has caught up. Without this flag, it
    // will end the profile early.
    heapProfdConfig.blockClient = HEAP_PROFD_BLOCK_CLIENT_ENABLED

    heapProfdConfig.continuousDumpConfig = continuousDumpConfig.build()

    heapProfdDataConfig.heapprofdConfig = heapProfdConfig.build()

    return heapProfdDataConfig.build()
  }

  private fun buildCommonTraceConfig(): TraceConfig {
    return TraceConfig.newBuilder()
      .setWriteIntoFile(WRITE_INTO_FILE_ENABLED)
      .setFlushPeriodMs(FLUSH_PERIOD_MS)
      .setFileWritePeriodMs(FILE_WRITE_PERIOD_MS)
      .build()
  }
}