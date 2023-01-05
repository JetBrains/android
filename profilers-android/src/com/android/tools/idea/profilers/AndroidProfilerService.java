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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.commands.CpuTraceInterceptCommandHandler;
import com.android.tools.idea.profilers.commands.GcCommandHandler;
import com.android.tools.idea.profilers.commands.LegacyAllocationCommandHandler;
import com.android.tools.idea.profilers.commands.LegacyCpuTraceCommandHandler;
import com.android.tools.idea.profilers.eventpreprocessor.EnergyUsagePreprocessor;
import com.android.tools.idea.profilers.eventpreprocessor.SimpleperfPipelinePreprocessor;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.transport.FailedToStartServerException;
import com.android.tools.idea.transport.TransportDeviceManager;
import com.android.tools.idea.transport.TransportProxy;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfSampleReporter;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.openapi.application.ApplicationManager;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An application-level service that manages application level configurations for the profilers. When constructed this class subscribes
 * itself to the application MessageBus and listens for {@link TransportDeviceManager#TOPIC} events.
 */
public class AndroidProfilerService implements TransportDeviceManager.TransportDeviceManagerListener {
  private final int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);
  @NotNull private static final String MEMORY_PROXY_EXECUTOR_NAME = "MemoryAllocationDataFetchExecutor";

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
  public void onTransportDaemonException(@NotNull Common.Device device, @NotNull Exception exception) {
  }

  @Override
  public void onTransportProxyCreationFail(@NotNull Common.Device device, @NotNull Exception exception) {
  }

  @Override
  public void onStartTransportDaemonServerFail(@NotNull Common.Device device, @NotNull FailedToStartServerException exception) {
  }

  @Override
  public void customizeProxyService(@NotNull TransportProxy proxy) {
    // Register profiler-specific command handlers (memory GC, pre-O CPU recording, etc.).
    IDevice device = proxy.getDevice();
    proxy.registerProxyCommandHandler(Commands.Command.CommandType.GC, new GcCommandHandler(device));
    if (device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      LegacyAllocationCommandHandler trackAllocationHandler =
        new LegacyAllocationCommandHandler(device,
                                           proxy.getEventQueue(),
                                           proxy.getBytesCache(),
                                           Executors.newSingleThreadExecutor(
                                             new ThreadFactoryBuilder().setNameFormat(MEMORY_PROXY_EXECUTOR_NAME).build()),
                                           StudioLegacyAllocationTracker::new);
      proxy.registerProxyCommandHandler(Commands.Command.CommandType.START_ALLOC_TRACKING, trackAllocationHandler);
      proxy.registerProxyCommandHandler(Commands.Command.CommandType.STOP_ALLOC_TRACKING, trackAllocationHandler);
    }
    if (device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      LegacyCpuTraceCommandHandler cpuTraceHandler =
        new LegacyCpuTraceCommandHandler(device,
                                         TransportServiceGrpc.newBlockingStub(proxy.getTransportChannel()),
                                         proxy.getEventQueue(),
                                         proxy.getBytesCache());
      proxy.registerProxyCommandHandler(Commands.Command.CommandType.START_TRACE, cpuTraceHandler);
      proxy.registerProxyCommandHandler(Commands.Command.CommandType.STOP_TRACE, cpuTraceHandler);
    } else if (StudioFlags.PERFETTO_SDK_TRACING.get() &&
               device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.R) {
      CpuTraceInterceptCommandHandler cpuTraceHandler =
        new CpuTraceInterceptCommandHandler(device,
                                         TransportServiceGrpc.newBlockingStub(proxy.getTransportChannel()));
      proxy.registerProxyCommandHandler(Commands.Command.CommandType.START_TRACE, cpuTraceHandler);
    }

    // Instantiate and register energy usage preprocessor, which preprocesses unified events and periodically insert energy usage events
    // to the datastore.
    if (StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get()) {
      proxy.registerEventPreprocessor(new EnergyUsagePreprocessor(TransportService.getInstance().getLogService()));
    }
    SimpleperfPipelinePreprocessor traceProcessor =
      new SimpleperfPipelinePreprocessor(new SimpleperfSampleReporter(AndroidStudioUsageTracker.deviceToDeviceInfo(device)));
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
    // The first live allocation tracking during the lifetime of the JVMTI agent starts with full
    // tracking mode by default.
    final int liveAllocationSamplingRate = MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue();
    configBuilder
      .setCommon(
        configBuilder.getCommonBuilder()
          .setEnergyProfilerEnabled(StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get())
          .setProfilerUnifiedPipeline(StudioFlags.PROFILER_UNIFIED_PIPELINE.get())
          .setProfilerCustomEventVisualization(StudioFlags.PROFILER_CUSTOM_EVENT_VISUALIZATION.get())
          .setProfilerKeyboardEvent(StudioFlags.PROFILER_KEYBOARD_EVENT.get()))
      .setMem(
        Agent.AgentConfig.MemoryConfig.newBuilder()
          .setMaxStackDepth(LIVE_ALLOCATION_STACK_DEPTH)
          .setTrackGlobalJniRefs(true)
          .setSamplingRate(
            Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(liveAllocationSamplingRate).build())
          .build())
      .setCpuApiTracingEnabled(true);
    if (runConfig != null && runConfig.getProfilerState().isNativeMemoryStartupProfilingEnabled()) {
      // Delay JVMTI instrumentation until the user stops the native heap sample recording.
      // This prevents a bug in heapprofd from terminating early.
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
      configBuilder.setAttachCommand(Commands.Command.CommandType.STOP_TRACE);
    }
    else if (runConfig != null && runConfig.getProfilerState().isCpuStartupProfilingEnabled()) {
      // Delay JVMTI instrumentation when a user is doing a startup cpu capture.
      // This is for consistency with native memory recording.
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
      configBuilder.setAttachCommand(Commands.Command.CommandType.STOP_TRACE);
    }
    else {
      configBuilder.setAttachMethod(Agent.AgentConfig.AttachAgentMethod.INSTANT);
    }
  }
}
