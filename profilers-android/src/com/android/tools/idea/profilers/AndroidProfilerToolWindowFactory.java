/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.transport.TransportDeviceManager;
import com.android.tools.idea.transport.TransportProxy;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfSampleReporter;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import icons.StudioIcons;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidProfilerToolWindowFactory implements DumbAware, ToolWindowFactory, Condition<Project> {
  public static final String ID = "Android Profiler";
  private static final String PROFILER_TOOL_WINDOW_TITLE = "Profiler";
  private static final Map<Content, AndroidProfilerToolWindow> PROJECT_PROFILER_MAP = new HashMap<>();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window == null) {
          return;
        }

        AndroidProfilerToolWindow profilerToolWindow = getProfilerToolWindow(project);
        if (window.isVisible() && profilerToolWindow == null) {
          createContent(project, window);
        }
      }
    });
  }

  @Override
  public void init(ToolWindow toolWindow) {
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.hide(null);
    toolWindow.setShowStripeButton(false);
    toolWindow.setStripeTitle(PROFILER_TOOL_WINDOW_TITLE);

    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(TransportDeviceManager.TOPIC, new ProfilerDeviceManagerListener());
  }

  private static void createContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    AndroidProfilerToolWindow view = new AndroidProfilerToolWindow(toolWindow, project);
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(view.getComponent(), "", false);
    Disposer.register(project, view);
    toolWindow.getContentManager().addContent(content);
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER);

    PROJECT_PROFILER_MAP.put(content, view);
    Disposer.register(content, () -> PROJECT_PROFILER_MAP.remove(content));

    // Forcibly synchronize the Tool Window to a visible state. Otherwise, the Tool Window may not auto-hide correctly.
    toolWindow.show(null);
  }

  /**
   * Gets the {@link AndroidProfilerToolWindow} corresponding to a given {@link Project} if it was already created by
   * {@link #createContent(Project, ToolWindow)}. Otherwise, returns null.
   */
  @Nullable
  public static AndroidProfilerToolWindow getProfilerToolWindow(@NotNull Project project) {
    ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(ID);
    if (window == null) {
      return null;
    }

    ContentManager contentManager = window.getContentManager();
    if (contentManager == null || contentManager.getContentCount() == 0) {
      return null;
    }

    return PROJECT_PROFILER_MAP.get(contentManager.getContent(0));
  }

  public static void removeContent(@NotNull ToolWindow toolWindow) {
    if (toolWindow.getContentManager().getContentCount() > 0) {
      Content content = toolWindow.getContentManager().getContent(0);
      PROJECT_PROFILER_MAP.remove(content);
      toolWindow.getContentManager().removeAllContents(true);
    }
  }

  @Override
  public boolean value(Project project) {
    return true;
  }

  private static class ProfilerDeviceManagerListener implements TransportDeviceManager.TransportDeviceManagerListener {
    private final int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);

    public ProfilerDeviceManagerListener() {
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
      ProfilerServiceProxyManager.registerProxies(proxy);
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
            .setSimpleperfHost(StudioFlags.PROFILER_SIMPLEPERF_HOST.get())
            .setUsePerfetto(StudioFlags.PROFILER_USE_PERFETTO.get()));
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
        .setCpuApiTracingEnabled(StudioFlags.PROFILER_CPU_API_TRACING.get())
        .setStartupProfilingEnabled(runConfig != null);
    }

    private boolean shouldEnableMemoryLiveAllocation(@Nullable AndroidRunConfigurationBase runConfig) {
      if (runConfig != null && runConfig.getProfilerState().STARTUP_CPU_PROFILING_ENABLED) {
        String configName = runConfig.getProfilerState().STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
        CpuProfilerConfig startupConfig = CpuProfilerConfigsState.getInstance(runConfig.getProject()).getConfigByName(configName);
        return startupConfig == null || !startupConfig.isDisableLiveAllocation();
      }
      return true;
    }
  }
}

