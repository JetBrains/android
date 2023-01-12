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
import com.android.tools.idea.codenavigation.CodeNavigator;
import com.android.tools.idea.codenavigation.FakeNavSource;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Trace;
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
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;

public class FakeIdeProfilerServices implements IdeProfilerServices {

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
  private final CodeNavigator myFakeNavigationService = new CodeNavigator(
      new FakeNavSource(), CodeNavigator.Companion.getTestExecutor());
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
   * Whether jank detection UI is enabled
   */
  private boolean myIsJankDetectionUiEnabled = true;

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   */
  private boolean myNativeProfilingConfigurationPreferred = false;

  /**
   * Whether long trace files should be parsed.
   */
  private boolean myShouldProceedYesNoDialog = false;

  /**
   * Whether the new pipeline is used or the old one for devices / processes / sessions.
   */
  private boolean myEventsPipelineEnabled = false;

  /**
   * Whether custom event visualization should be visible
   */
  private boolean myCustomEventVisualizationEnabled = false;

  /**
   * Whether we support profileable builds.
   */
  private boolean myProfileablsBuildsEnabled = true;

  /**
   * Whether power and battery data tracks should be visible in system trace and if shown,
   * which graph display style will be used for the power and battery tracks.
   * Value of HIDE -> Hide both power + battery tracks.
   * Value of MINMAX -> Show power rails is min-max view and battery counters in zero-based view.
   * Value of DELTA -> Show power rails in delta view and battery counters in zero-based view.
   */
  private PowerProfilerDisplayMode mySystemTracePowerProfilerDisplayMode = PowerProfilerDisplayMode.HIDE;

  /**
   * Whether we support navigate-to-source action for Compose Tracing
   */
  private boolean myComposeTracingNavigateToSourceEnabled = true;

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

    myFakeNavigationService.addListener((location -> myFakeFeatureTracker.trackNavigateToCode()));
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
  public <R> void runAsync(@NotNull Supplier<R> supplier, @NotNull Consumer<R> consumer) {
    consumer.accept(supplier.get());
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
    Collections.addAll(myProjectClasses, classNames);
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
      public boolean isEnergyProfilerEnabled() {
        return myEnergyProfilerEnabled;
      }

      @Override
      public boolean isJankDetectionUiEnabled() {
        return myIsJankDetectionUiEnabled;
      }

      @Override
      public boolean isMemoryCSVExportEnabled() {
        return false;
      }

      @Override
      public boolean isPerformanceMonitoringEnabled() {
        return false;
      }

      @Override
      public boolean isProfileableBuildsEnabled() {
        return myProfileablsBuildsEnabled;
      }

      @Override
      public PowerProfilerDisplayMode getSystemTracePowerProfilerDisplayMode() {
        return mySystemTracePowerProfilerDisplayMode;
      }

      @Override
      public boolean isCustomEventVisualizationEnabled() {
        return myCustomEventVisualizationEnabled;
      }

      @Override
      public boolean isUnifiedPipelineEnabled() {
        return myEventsPipelineEnabled;
      }

      @Override
      public boolean isComposeTracingNavigateToSourceEnabled() {
        return myComposeTracingNavigateToSourceEnabled;
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

  public void addCustomProfilingConfiguration(String name, TraceType type) {
    ProfilingConfiguration config;
    if (type == TraceType.ART) {
      config = new ArtSampledConfiguration(name);
    }
    else if (type == TraceType.SIMPLEPERF) {
      config = new SimpleperfConfiguration(name);
    }
    else if (type == TraceType.PERFETTO) {
      config = new PerfettoConfiguration(name);
    }
    else if (type == TraceType.ATRACE) {
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

  public void enableJankDetectionUi(boolean enabled) {
    myIsJankDetectionUiEnabled = enabled;
  }

  public void enableEventsPipeline(boolean enabled) {
    myEventsPipelineEnabled = enabled;
  }

  public void enableCustomEventVisualization(boolean enabled) { myCustomEventVisualizationEnabled = enabled; }

  public void enableProfileableBuilds(boolean enabled) {
    myProfileablsBuildsEnabled = enabled;
  }

  public void setSystemTracePowerProfilerDisplayMode(PowerProfilerDisplayMode mode) {
    mySystemTracePowerProfilerDisplayMode = mode;
  }

  public void enableComposeTracingNavigateToSource(boolean enabled) {
    myComposeTracingNavigateToSourceEnabled = enabled;
  }
}
