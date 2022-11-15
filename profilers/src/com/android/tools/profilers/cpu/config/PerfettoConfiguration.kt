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
package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.adtui.model.options.Slider
import com.android.tools.profiler.proto.Trace
import perfetto.protos.PerfettoConfig.AndroidPowerConfig
import perfetto.protos.PerfettoConfig.DataSourceConfig
import perfetto.protos.PerfettoConfig.ProcessStatsConfig
import perfetto.protos.PerfettoConfig.TraceConfig

/**
 * Configuration for Perfetto traces.
 */
class PerfettoConfiguration(name: String) : ProfilingConfiguration(name) {
  @Slider(min = 1, max = 32, step = 1)
  @OptionsProperty(group = TRACE_CONFIG_GROUP, order = 100, name = "Buffer size limit:",
                   description = "In memory buffer size for capturing trace events.",
                   unit = "Mb")
  var profilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB
  var appPkgName: String = ""

  private val counterFtraceEvents = listOf("thermal/thermal_temperature", "perf_trace_counters/perf_trace_user")
  private val fenceFtraceEvents = listOf("fence/signaled", "fence/fence_wait_start")
  private val cpuFreqFtraceEvents = listOf("power/cpu_frequency", "power/cpu_idle")
  private val taskTrackingFtraceEvents = listOf("task/task_rename", "task/task_newtask")

  private val standardAtraceCategories = listOf("gfx", "input", "view", "wm", "am", "sm", "camera", "hal", "res", "pm", "ss", "power",
                                                "database",
                                                "binder_driver", "binder_lock")
  private val verboseAtraceCategories = listOf("sched", "freq")

  private val batteryCounters = listOf(AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CAPACITY_PERCENT,
                                       AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CHARGE,
                                       AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CURRENT)

  override fun buildUserOptions(): Trace.UserOptions.Builder {
    return Trace.UserOptions.newBuilder()
      .setBufferSizeInMb(profilingBufferSizeInMb)
  }

  override fun getOptions(): TraceConfig {

    var config = buildCommonTraceConfig()
    val configBuilder = config.toBuilder()

    // add buffers
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(profilingBufferSizeInMb * 1024))
    configBuilder.addBuffers(TraceConfig.BufferConfig.newBuilder().setSizeKb(256))

    // add ftrace data source
    val ftraceDataSource = TraceConfig.DataSource.newBuilder().setConfig(getFtraceDataConfig())

    // Add config to get process and thread names.
    // This is required to properly parse perfetto captures with trebuchet.
    val processAndThreadNamesDataSource = TraceConfig.DataSource.newBuilder().setConfig(getProcessAndThreadNamesDataConfig())

    // Split process/thread scan and /proc/stat so that they don't share the same buffer.
    val procStatDataSource = TraceConfig.DataSource.newBuilder().setConfig(getProcStatDataConfig())

    // Add config to get CPU information from procfs and sysfs.
    val cpuInfoDataConfig = DataSourceConfig.newBuilder().setName("linux.system_info")
    val cpuInfoDataSource = TraceConfig.DataSource.newBuilder().setConfig(cpuInfoDataConfig)

    // These data sources will be ignored on unsupported Android version.
    // Add data source to get Android frame lifecycle data (R+).
    val lifecycleDataConfig = DataSourceConfig.newBuilder().setName("android.surfaceflinger.frame")
    val lifeCycleDataSource = TraceConfig.DataSource.newBuilder().setConfig(lifecycleDataConfig)

    // Add data source to get Android frame timeline data (S+).
    val frameTimelineDataConfig = DataSourceConfig.newBuilder().setName("android.surfaceflinger.frametimeline")
    val frameTimelineDataSource = TraceConfig.DataSource.newBuilder().setConfig(frameTimelineDataConfig)

    // Perfetto SDK TrackEvent API
    val trackEventDataConfig = DataSourceConfig.newBuilder().setName("track_event")
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

  private fun getFtraceDataConfig(): DataSourceConfig {
    val ftraceDataConfig = DataSourceConfig.newBuilder().setName("linux.ftrace").setTargetBuffer(0)

    val ftraceConfig = ftraceDataConfig.ftraceConfig.toBuilder()
    // Drain ftrace every 10frames @ 60fps
    ftraceConfig.drainPeriodMs = 170
    // Enable "compact sched", which significantly reduces the bandwidth taken by
    // sched events by proto encoding in a more efficient way. Supported on R+.
    // No effect on Q-.
    ftraceConfig.compactSchedBuilder.enabled = true
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
    perfEventConfig.allCpus = true
    perfEventConfig.addTargetCmdline(appPkgName)
    ftraceDataConfig.perfEventConfig = perfEventConfig.build()

    // In P and above "*" is supported, if we move to support O we will want to
    // pass in the |app_pkg_name|
    ftraceConfig.addAtraceApps("*");

    ftraceDataConfig.ftraceConfig = ftraceConfig.build()

    return ftraceDataConfig.build()
  }

  private fun getProcessAndThreadNamesDataConfig(): DataSourceConfig {
    val processAndThreadNamesDataConfig = DataSourceConfig.newBuilder().setName("linux.process_stats").setTargetBuffer(1)
    val procStatsConfig = processAndThreadNamesDataConfig.processStatsConfig.toBuilder()
    procStatsConfig.scanAllProcessesOnStart = true
    procStatsConfig.recordThreadNames = true
    processAndThreadNamesDataConfig.processStatsConfig = procStatsConfig.build()

    return processAndThreadNamesDataConfig.build()
  }

  private fun getProcStatDataConfig(): DataSourceConfig {
    val procStatDataConfig = DataSourceConfig.newBuilder().setName("linux.process_stats").setTargetBuffer(0)
    val procStatProcessStatsConfig = procStatDataConfig.processStatsConfig.toBuilder()
    procStatProcessStatsConfig.procStatsPollMs = 1000
    procStatProcessStatsConfig.addQuirks(ProcessStatsConfig.Quirks.DISABLE_ON_DEMAND)
    procStatDataConfig.processStatsConfig = procStatProcessStatsConfig.build()

    return procStatDataConfig.build()
  }

  private fun getPowerDataConfig(): DataSourceConfig {
    val powerDataConfig = DataSourceConfig.newBuilder().setName("android.power").setTargetBuffer(0)
    val androidPowerConfig = powerDataConfig.androidPowerConfig.toBuilder()
    androidPowerConfig.collectPowerRails = true
    androidPowerConfig.batteryPollMs = 1000
    androidPowerConfig.addAllBatteryCounters(batteryCounters)
    powerDataConfig.androidPowerConfig = androidPowerConfig.build()

    return powerDataConfig.build()
  }

  private fun buildCommonTraceConfig(): TraceConfig {
    return TraceConfig.newBuilder()
      .setWriteIntoFile(true)
      .setFlushPeriodMs(1000)
      .setFileWritePeriodMs(250)
      .build()
  }

  override fun addOptions(configBuilder: Trace.TraceConfiguration.Builder) {
    configBuilder.perfettoOptions = options
  }

  override fun getTraceType(): Trace.UserOptions.TraceType {
    return Trace.UserOptions.TraceType.PERFETTO
  }

  override fun getRequiredDeviceLevel(): Int {
    return AndroidVersion.VersionCodes.P;
  }
}