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
package com.android.tools.idea.profilers;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.eventpreprocessor.EnergyUsagePreprocessor;
import com.android.tools.idea.profilers.eventpreprocessor.SimpleperfPipelinePreprocessor;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxyManager;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.transport.TransportDeviceManager;
import com.android.tools.idea.transport.TransportProxy;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfSampleReporter;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An application-level service that manages application level configurations for the profilers. When constructed this class subscribes
 * itself to the application MessageBus and listens for {@link TransportDeviceManager#TOPIC} events.
 */
public class AndroidProfilerService implements TransportDeviceManager.TransportDeviceManagerListener {
  private final int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);

  public static AndroidProfilerService getInstance() {
    return ApplicationManager.getApplication().getService(AndroidProfilerService.class);
  }

  /**
   * Default constructor required for reflection by intellij.
   */
  AndroidProfilerService() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(TransportDeviceManager.TOPIC, this);
  }

  @Override
  public void onPreTransportDaemonStart(@NotNull Common.Device device) {
  }

  @Override
  public void onStartTransportDaemonFail(@NotNull Common.Device device, @NotNull Exception exception) {
  }

  @Override
  public void onTransportProxyCreationFail(@NotNull Common.Device device, @NotNull Exception exception) {
  }

  @Override
  public void customizeProxyService(@NotNull TransportProxy proxy) {
    // Register profiler-specific command handlers (memory GC, pre-O CPU recording, etc.).
    ProfilerServiceProxyManager.registerCommandHandlers(proxy);

    // Instantiate and register energy usage preprocessor, which preprocesses unified events and periodically insert energy usage events
    // to the datastore.
    if (StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get()) {
      proxy.registerEventPreprocessor(new EnergyUsagePreprocessor(TransportService.getInstance().getLogService()));
    }
    SimpleperfPipelinePreprocessor traceProcessor = new SimpleperfPipelinePreprocessor(new SimpleperfSampleReporter());
    proxy.registerEventPreprocessor(traceProcessor);
    proxy.registerDataPreprocessor(traceProcessor);
  }

  @Override
  public void customizeDaemonConfig(@NotNull Transport.DaemonConfig.Builder configBuilder) {
    configBuilder
      .setCommon(
        configBuilder.getCommonBuilder()
          .setEnergyProfilerEnabled(StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get())
          .setProfilerUnifiedPipeline(StudioFlags.PROFILER_UNIFIED_PIPELINE.get())
          .setProfilerCustomEventVisualization(StudioFlags.PROFILER_CUSTOM_EVENT_VISUALIZATION.get()))
      .setCpu(
        Transport.DaemonConfig.CpuConfig.newBuilder()
          .setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC)
          .setUsePerfetto(true));
  }

  @Override
  public void customizeAgentConfig(@NotNull Agent.AgentConfig.Builder configBuilder,
                                   @Nullable AndroidRunConfigurationBase runConfig) {
    int liveAllocationSamplingRate;
    if (StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS.get()) {
      // If memory live allocation is enabled, read sampling rate from preferences. Otherwise suspend live allocation.
      if (shouldEnableMemoryLiveAllocation(runConfig)) {
        liveAllocationSamplingRate = PropertiesComponent.getInstance().getInt(
          IntellijProfilerPreferences.getProfilerPropertyName(MemoryProfilerStage.LIVE_ALLOCATION_SAMPLING_PREF),
          MemoryProfilerStage.DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE.getValue());
      }
      else {
        liveAllocationSamplingRate = MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue();
      }
    }
    else {
      // Sampling feature is disabled, use full mode.
      liveAllocationSamplingRate = MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue();
    }
    configBuilder
      .setCommon(
        configBuilder.getCommonBuilder()
          .setEnergyProfilerEnabled(StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get())
          .setProfilerUnifiedPipeline(StudioFlags.PROFILER_UNIFIED_PIPELINE.get())
          .setProfilerCustomEventVisualization(StudioFlags.PROFILER_CUSTOM_EVENT_VISUALIZATION.get()))
      .setMem(
        Agent.AgentConfig.MemoryConfig.newBuilder()
          .setUseLiveAlloc(StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get())
          .setMaxStackDepth(LIVE_ALLOCATION_STACK_DEPTH)
          .setTrackGlobalJniRefs(StudioFlags.PROFILER_TRACK_JNI_REFS.get())
          .setSamplingRate(
            Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(liveAllocationSamplingRate).build())
          .build())
      .setCpuApiTracingEnabled(true);
    if (runConfig != null && runConfig.getProfilerState().isNativeMemoryStartupProfilingEnabled()) {
      // Delay JVMTI instrumentation until the user stops the native heap sample recording.
      // This prevents a bug in heapprofd from terminating early.
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
      configBuilder.setAttachCommand(Commands.Command.CommandType.STOP_NATIVE_HEAP_SAMPLE);
    } else if (runConfig != null && runConfig.getProfilerState().isCpuStartupProfilingEnabled()) {
      // Delay JVMTI instrumentation when a user is doing a startup cpu capture.
      // This is for consistency with native memory recording.
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
      configBuilder.setAttachCommand(Commands.Command.CommandType.STOP_CPU_TRACE);
    } else {
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.INSTANT);
    }
  }

  private boolean shouldEnableMemoryLiveAllocation(@Nullable AndroidRunConfigurationBase runConfig) {
    if (runConfig == null) {
      return true;
    }
    ProfilerState state = runConfig.getProfilerState();
    if (state.isCpuStartupProfilingEnabled()) {
      String configName = runConfig.getProfilerState().STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
      CpuProfilerConfig startupConfig = CpuProfilerConfigsState.getInstance(runConfig.getProject()).getConfigByName(configName);
      return startupConfig == null || !startupConfig.isDisableLiveAllocation();
    }
    return !state.isNativeMemoryStartupProfilingEnabled();
  }
}
