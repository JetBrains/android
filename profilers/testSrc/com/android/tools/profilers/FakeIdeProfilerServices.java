/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.sdklib.AndroidVersion;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.FakeTracePreProcessor;
import com.android.tools.profilers.cpu.TracePreProcessor;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.AtraceConfiguration;
import com.android.tools.profilers.cpu.config.PerfettoConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService;
import com.android.tools.profilers.stacktrace.FakeCodeNavigator;
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeIdeProfilerServices implements IdeProfilerServices {

  public static final String FAKE_ART_SAMPLED_NAME = "Sampled";

  public static final String FAKE_ART_INSTRUMENTED_NAME = "Instrumented";

  public static final String FAKE_SIMPLEPERF_NAME = "Simpleperf";

  public static final String FAKE_ATRACE_NAME = "Atrace";

  public static final String FAKE_PERFETTO_NAME = "Perfetto";

  public static final String FAKE_SYMBOL_DIR = "/fake/sym/dir/";

  public static final ProfilingConfiguration ART_SAMPLED_CONFIG = new ArtSampledConfiguration(FAKE_ART_SAMPLED_NAME);
  public static final ProfilingConfiguration ART_INSTRUMENTED_CONFIG = new ArtInstrumentedConfiguration(FAKE_ART_INSTRUMENTED_NAME);
  public static final ProfilingConfiguration SIMPLEPERF_CONFIG = new SimpleperfConfiguration(FAKE_SIMPLEPERF_NAME);
  public static final ProfilingConfiguration ATRACE_CONFIG = new AtraceConfiguration(FAKE_ATRACE_NAME);
  public static final ProfilingConfiguration PERFETTO_CONFIG = new PerfettoConfiguration(FAKE_PERFETTO_NAME);

  private final FeatureTracker myFakeFeatureTracker = new FakeFeatureTracker();
  private final CodeNavigator myFakeNavigationService = new FakeCodeNavigator(myFakeFeatureTracker);
  private final TracePreProcessor myFakeTracePreProcessor = new FakeTracePreProcessor();
  private TraceProcessorService myTraceProcessorService = new FakeTraceProcessorService();
  private NativeFrameSymbolizer myFakeSymbolizer = new NativeFrameSymbolizer() {
    @NotNull
    @Override
    public Memory.NativeCallStack.NativeFrame symbolize(String abi,
                                                        Memory.NativeCallStack.NativeFrame unsymbolizedFrame) {
      return unsymbolizedFrame;
    }

    @Override
    public void stop() {

    }
  };

  /**
   * Toggle for including an energy profiler in our profiler view.
   */
  private boolean myEnergyProfilerEnabled = false;

  /**
   * JNI references alloc/dealloc events are tracked and shown.
   */
  private boolean myIsJniReferenceTrackingEnabled = false;

  /**
   * Toggle for faking live allocation tracking support in tests.
   */
  private boolean myLiveTrackingEnabled = false;

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   */
  private boolean myNativeProfilingConfigurationPreferred = false;

  /**
   * Whether long trace files should be parsed.
   */
  private boolean myShouldProceedYesNoDialog = false;

  /**
   * Can toggle for tests via {@link #enableStartupCpuProfiling(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myStartupCpuProfilingEnabled = false;

  /**
   * Whether the new pipeline is used or the old one for devices / processes / sessions.
   */
  private boolean myEventsPipelineEnabled = false;

  /**
   * Toggle for faking {@link FeatureConfig#isCpuNewRecordingWorkflowEnabled()} in tests.
   */
  private boolean myCpuNewRecordingWorkflowEnabled = false;

  /**
   * Toggle for live allocation sampling mode.
   */
  private boolean myLiveAllocationsSamplingEnabled = true;

  /**
   * Toggle for cpu capture stage switching vs cpu profiler stage when handling captures.
   */
  private boolean myIsCaptureStageEnabled = false;

  /**
   * Whether custom event visualization should be visible
   */
  private boolean myCustomEventVisualizationEnabled = false;

  /**
   * Whether native memory sampling via heapprofd is enabled.
   */
  private boolean myNativeMemorySampleEnabled = false;

  /**
   * Whether we use TraceProcessor to parse Perfetto traces.
   */
  private boolean myUseTraceProcessor = true;

  /**
   * Whether we support profileable processes
   */
  private boolean myProfileableEnabled = false;

  /**
   * Whether we support profileable processes in Q & R
   */
  private boolean myProfileableInQrEnabled = false;

  /**
   * List of custom CPU profiling configurations.
   */
  private final List<ProfilingConfiguration> myCustomProfilingConfigurations = new ArrayList<>();

  @NotNull private final ProfilerPreferences myPersistentPreferences;
  @NotNull private final ProfilerPreferences myTemporaryPreferences;

  /**
   * When {@link #openListBoxChooserDialog} is called this will be used to try to match one of the options and return the first match.
   * If no option is matched, it will use the {@code myListBoxOptionsIndex}.
   */
  @Nullable private Predicate<String> myListBoxOptionMatcher;

  /**
   * When {@link #openListBoxChooserDialog} is called this index is used to return a specific element in the set of options.
   * If this index is out of bounds (e.g. -1 or more than the available options), null is returned just as like the user has cancelled
   * the selection.
   */
  private int myListBoxOptionsIndex;

  @Nullable private Notification myNotification;

  @NotNull private final Set<String> myProjectClasses = new HashSet<>();

  public FakeIdeProfilerServices() {
    myPersistentPreferences = new FakeProfilerPreferences();
    myTemporaryPreferences = new FakeProfilerPreferences();
  }

  @NotNull
  @Override
  public Executor getMainExecutor() {
    return (runnable) -> runnable.run();
  }

  @NotNull
  @Override
  public Executor getPoolExecutor() {
    return (runnable) -> runnable.run();
  }

  @Override
  public void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable) {
  }

  @NotNull
  @Override
  public NativeFrameSymbolizer getNativeFrameSymbolizer() {
    return myFakeSymbolizer;
  }

  public void setNativeFrameSymbolizer(@NotNull NativeFrameSymbolizer symbolizer) {
    myFakeSymbolizer = symbolizer;
  }

  @Override
  public Set<String> getAllProjectClasses() {
    return myProjectClasses;
  }

  public void addProjectClasses(String... classNames) {
    for (int i = 0; i < classNames.length; i++) {
      myProjectClasses.add(classNames[i]);
    }
  }

  @NotNull
  @Override
  public CodeNavigator getCodeNavigator() {
    return myFakeNavigationService;
  }

  @NotNull
  @Override
  public FeatureTracker getFeatureTracker() {
    return myFakeFeatureTracker;
  }

  @Override
  public void enableAdvancedProfiling() {
    // No-op.
  }

  @NotNull
  @Override
  public FeatureConfig getFeatureConfig() {
    return new FeatureConfig() {
      @Override
      public boolean isCpuCaptureStageEnabled() { return myIsCaptureStageEnabled; }

      @Override
      public boolean isCpuNewRecordingWorkflowEnabled() {
        return myCpuNewRecordingWorkflowEnabled;
      }

      @Override
      public boolean isEnergyProfilerEnabled() {
        return myEnergyProfilerEnabled;
      }

      @Override
      public boolean isJniReferenceTrackingEnabled() { return myIsJniReferenceTrackingEnabled; }

      @Override
      public boolean isLiveAllocationsEnabled() {
        return myLiveTrackingEnabled;
      }

      @Override
      public boolean isLiveAllocationsSamplingEnabled() {
        return myLiveAllocationsSamplingEnabled;
      }

      @Override
      public boolean isMemoryCSVExportEnabled() {
        return false;
      }

      @Override
      public boolean isNativeMemorySampleEnabled() { return myNativeMemorySampleEnabled; }

      @Override
      public boolean isPerformanceMonitoringEnabled() {
        return false;
      }

      @Override
      public boolean isProfileableEnabled() {
        return myProfileableEnabled;
      }

      @Override
      public boolean isProfileableInQrEnabled() {
        return myProfileableInQrEnabled;
      }

      @Override
      public boolean isCustomEventVisualizationEnabled() {
        return myCustomEventVisualizationEnabled;
      }

      @Override
      public boolean isStartupCpuProfilingEnabled() {
        return myStartupCpuProfilingEnabled;
      }

      @Override
      public boolean isUnifiedPipelineEnabled() {
        return myEventsPipelineEnabled;
      }

      @Override
      public boolean isUseTraceProcessor() {
        return myUseTraceProcessor;
      }
    };
  }

  @NotNull
  @Override
  public ProfilerPreferences getTemporaryProfilerPreferences() {
    return myTemporaryPreferences;
  }

  @NotNull
  @Override
  public ProfilerPreferences getPersistentProfilerPreferences() {
    return myPersistentPreferences;
  }

  @Override
  public void openYesNoDialog(String message, String title, Runnable yesCallback, Runnable noCallback) {
    (myShouldProceedYesNoDialog ? yesCallback : noCallback).run();
  }

  @Override
  @Nullable
  public <T> T openListBoxChooserDialog(@NotNull String title,
                                        @Nullable String message,
                                        @NotNull List<T> options,
                                        @NotNull Function<T, String> listBoxPresentationAdapter) {
    if (myListBoxOptionMatcher != null) {
      Optional<T> optionMatch = options.stream().filter(o -> myListBoxOptionMatcher.test(listBoxPresentationAdapter.apply(o))).findFirst();
      if (optionMatch.isPresent()) {
        return optionMatch.get();
      }
    }

    if (myListBoxOptionsIndex >= 0 && myListBoxOptionsIndex < options.size()) {
      return options.get(myListBoxOptionsIndex);
    }
    return null;
  }

  @NotNull
  public TracePreProcessor getTracePreProcessor() {
    return myFakeTracePreProcessor;
  }

  /**
   * Sets the listbox options matcher to search for one of options.
   * If set to null (default) or match is not successful, then fallback to {@code setListBoxOptionsIndex(int)}.
   */
  public void setListBoxOptionsMatcher(@Nullable Predicate<String> optionMatcher) {
    myListBoxOptionMatcher = optionMatcher;
  }

  /**
   * Sets the listbox options return element index. If this is set to an index out of bounds null is returned.
   */
  public void setListBoxOptionsIndex(int optionIndex) {
    myListBoxOptionsIndex = optionIndex;
  }

  public void setShouldProceedYesNoDialog(boolean shouldProceedYesNoDialog) {
    myShouldProceedYesNoDialog = shouldProceedYesNoDialog;
  }

  public void addCustomProfilingConfiguration(String name, Cpu.CpuTraceType type) {
    ProfilingConfiguration config;
    if (type == Cpu.CpuTraceType.ART) {
      config = new ArtSampledConfiguration(name);
    }
    else if (type == Cpu.CpuTraceType.SIMPLEPERF) {
      config = new SimpleperfConfiguration(name);
    }
    else if (type == Cpu.CpuTraceType.PERFETTO) {
      config = new PerfettoConfiguration(name);
    }
    else if (type == Cpu.CpuTraceType.ATRACE) {
      config = new AtraceConfiguration(name);
    }
    else {
      config = new UnspecifiedConfiguration(name);
    }
    myCustomProfilingConfigurations.add(config);
  }

  @Override
  public List<ProfilingConfiguration> getUserCpuProfilerConfigs(int apiLevel) {
    return myCustomProfilingConfigurations;
  }

  @Override
  public List<ProfilingConfiguration> getDefaultCpuProfilerConfigs(int apiLevel) {
    if (apiLevel >= AndroidVersion.VersionCodes.P) {
      return ImmutableList.of(ART_SAMPLED_CONFIG, ART_INSTRUMENTED_CONFIG, SIMPLEPERF_CONFIG, PERFETTO_CONFIG);
    }
    else {
      return ImmutableList.of(ART_SAMPLED_CONFIG, ART_INSTRUMENTED_CONFIG, SIMPLEPERF_CONFIG, ATRACE_CONFIG);
    }
  }

  @Override
  public boolean isNativeProfilingConfigurationPreferred() {
    return myNativeProfilingConfigurationPreferred;
  }

  @Override
  public int getNativeMemorySamplingRateForCurrentConfig() {
    return 0;
  }

  @Override
  public void showNotification(@NotNull Notification notification) {
    myNotification = notification;
  }

  @NotNull
  @Override
  public List<String> getNativeSymbolsDirectories() {
    return Collections.singletonList(FAKE_SYMBOL_DIR);
  }

  @NotNull
  @Override
  public TraceProcessorService getTraceProcessorService() {
    return myTraceProcessorService;
  }

  public void setTraceProcessorService(@NotNull TraceProcessorService service) {
    myTraceProcessorService = service;
  }

  @Nullable
  public Notification getNotification() {
    return myNotification;
  }

  public void setNativeProfilingConfigurationPreferred(boolean nativeProfilingConfigurationPreferred) {
    myNativeProfilingConfigurationPreferred = nativeProfilingConfigurationPreferred;
  }

  public void enableEnergyProfiler(boolean enabled) {
    myEnergyProfilerEnabled = enabled;
  }

  public void enableJniReferenceTracking(boolean enabled) { myIsJniReferenceTrackingEnabled = enabled; }

  public void enableLiveAllocationTracking(boolean enabled) {
    myLiveTrackingEnabled = enabled;
  }

  public void enableStartupCpuProfiling(boolean enabled) {
    myStartupCpuProfilingEnabled = enabled;
  }

  public void enableEventsPipeline(boolean enabled) {
    myEventsPipelineEnabled = enabled;
  }

  public void enableCpuNewRecordingWorkflow(boolean enabled) {
    myCpuNewRecordingWorkflowEnabled = enabled;
  }

  public void enableLiveAllocationsSampling(boolean enabled) {
    myLiveAllocationsSamplingEnabled = enabled;
  }

  public void enableNativeMemorySampling(boolean enabled) {
    myNativeMemorySampleEnabled = enabled;
  }

  public void enableCpuCaptureStage(boolean enabled) { myIsCaptureStageEnabled = enabled; }

  public void enableCustomEventVisualization(boolean enabled) { myCustomEventVisualizationEnabled = enabled; }

  public void enableUseTraceProcessor(boolean enabled) {
    myUseTraceProcessor = enabled;
  }

  public void enableProfileable(boolean enabled) {
    myProfileableEnabled = enabled;
  }

  public void enableProfileableInQr(boolean enabled) {
    myProfileableInQrEnabled = enabled;
  }
}
