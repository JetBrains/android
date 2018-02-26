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

import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.FakeCodeNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class FakeIdeProfilerServices implements IdeProfilerServices {
  private final FeatureTracker myFakeFeatureTracker = new FakeFeatureTracker();
  private final CodeNavigator myFakeNavigationService = new FakeCodeNavigator(myFakeFeatureTracker);

  /**
   * Callback to be run after the executor calls its execute() method.
   */
  @Nullable
  Runnable myOnExecute;

  /**
   * The pool executor runs code in a separate thread. Sometimes is useful to check the state of the profilers
   * just before calling pool executor's execute method (e.g. verifying Stage's transient status before making a gRPC call).
   */
  @Nullable
  Runnable myPrePoolExecute;

  /**
   * Can toggle for tests via {@link #enableAtrace(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myAtraceEnabled = false;

  /**
   * Toggle for including an energy profiler in our profiler view.
   */
  private boolean myEnergyProfilerEnabled = false;

  /**
   * Can toggle for tests via {@link #enableExportTrace(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myExportCpuTraceEnabled = false;

  /**
   * Toggle for faking jvmti agent support in tests.
   */
  private boolean myJvmtiAgentEnabled = false;

  /**
   * JNI references alloc/dealloc events are tracked and shown.
   */
  private boolean myIsJniReferenceTrackingEnabled = false;

  /**
   * Toggle for faking live allocation tracking support in tests.
   */
  private boolean myLiveTrackingEnabled = false;

  /**
   * Toggle for faking memory snapshot support in tests.
   */
  private boolean myMemorySnapshotEnabled = true;

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   */
  private boolean myNativeProfilingConfigurationPreferred = false;

  /**
   * Whether network request payload is tracked and shown.
   */
  private boolean myRequestPayloadEnabled = false;

  /**
   * Whether long trace files should be parsed.
   */
  private boolean myShouldParseLongTraces = false;

  /**
   * Toggle for faking sessions UI support in tests.
   */
  private boolean mySessionsViewEnabled = true;

  /**
   * Can toggle for tests via {@link #enableSimpleperf(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean mySimpleperfEnabled = false;


  /**
   * List of custom CPU profiling configurations.
   */
  private final List<ProfilingConfiguration> myCustomProfilingConfigurations = new ArrayList<>();

  @NotNull private final ProfilerPreferences myTemporaryPreferences;

  /**
   * Title of the error balloon displayed when {@link #showErrorBalloon(String, String, String, String)} is called.
   */
  private String myErrorBalloonTitle;
  /**
   * Body of the error balloon displayed when {@link #showErrorBalloon(String, String, String, String)} is called.
   */
  private String myErrorBalloonBody;
  /**
   * Url of the error balloon displayed when {@link #showErrorBalloon(String, String, String, String)} is called.
   */
  private String myErrorBalloonUrl;
  /**
   * Linked text of the error balloon displayed when {@link #showErrorBalloon(String, String, String, String)} is called.
   */
  private String myErrorBalloonUrlText;

  public FakeIdeProfilerServices() {
    myTemporaryPreferences = new FakeProfilerPreferences();
  }

  @NotNull
  @Override
  public Executor getMainExecutor() {
    return (runnable) -> {
      runnable.run();
      if (myOnExecute != null) {
        myOnExecute.run();
      }
    };
  }

  @NotNull
  @Override
  public Executor getPoolExecutor() {
    return (runnable) -> {
      if (myPrePoolExecute != null) {
        myPrePoolExecute.run();
      }
      runnable.run();
    };
  }

  @Override
  public void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable) {
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
      public boolean isAtraceEnabled() {
        return myAtraceEnabled;
      }

      @Override
      public boolean isCpuCaptureFilterEnabled() {
        return false;
      }

      @Override
      public boolean isEnergyProfilerEnabled() {
        return myEnergyProfilerEnabled;
      }

      @Override
      public boolean isExportCpuTraceEnabled() {
        return myExportCpuTraceEnabled;
      }

      @Override
      public boolean isJniReferenceTrackingEnabled() { return myIsJniReferenceTrackingEnabled; }

      @Override
      public boolean isJvmtiAgentEnabled() {
        return myJvmtiAgentEnabled;
      }

      @Override
      public boolean isLiveAllocationsEnabled() {
        return myLiveTrackingEnabled;
      }

      @Override
      public boolean isMemoryCaptureFilterEnabled() {
        return false;
      }

      @Override
      public boolean isMemorySnapshotEnabled() {
        return myMemorySnapshotEnabled;
      }

      @Override
      public boolean isNetworkRequestPayloadEnabled() {
        return myRequestPayloadEnabled;
      }

      @Override
      public boolean isNetworkThreadViewEnabled() {
        return true;
      }

      @Override
      public boolean isSessionsEnabled() {
        return mySessionsViewEnabled;
      }

      @Override
      public boolean isSimpleperfEnabled() {
        return mySimpleperfEnabled;
      }
    };
  }

  @NotNull
  @Override
  public ProfilerPreferences getTemporaryProfilerPreferences() {
    return myTemporaryPreferences;
  }

  @Override
  public void openCpuProfilingConfigurationsDialog(CpuProfilerConfigModel model, int deviceLevel,
                                                   Consumer<ProfilingConfiguration> callbackDialog) {
    // No-op.
  }

  @Override
  public void openParseLargeTracesDialog(Runnable yesCallback, Runnable noCallback) {
    if (myShouldParseLongTraces) {
      yesCallback.run();
    }
    else {
      noCallback.run();
    }
  }

  public void setShouldParseLongTraces(boolean shouldParseLongTraces) {
    myShouldParseLongTraces = shouldParseLongTraces;
  }

  public void addCustomProfilingConfiguration(String name, CpuProfiler.CpuProfilerType type) {
    ProfilingConfiguration config =
      new ProfilingConfiguration(name, type, CpuProfiler.CpuProfilerConfiguration.Mode.UNSTATED);
    myCustomProfilingConfigurations.add(config);
  }

  @Override
  public List<ProfilingConfiguration> getCpuProfilingConfigurations() {
    return myCustomProfilingConfigurations;
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return "some.id";
  }

  @Override
  public boolean isNativeProfilingConfigurationPreferred() {
    return myNativeProfilingConfigurationPreferred;
  }

  @Override
  public void showErrorBalloon(@NotNull String title, @NotNull String body, String url, String urlText) {
    myErrorBalloonTitle = title;
    myErrorBalloonBody = body;
    myErrorBalloonUrl = url;
    myErrorBalloonUrlText = urlText;
  }

  @Override
  public void reportNoPiiException(@NotNull Throwable t) {
    t.printStackTrace();
  }

  public String getErrorBalloonTitle() {
    return myErrorBalloonTitle;
  }

  public String getErrorBalloonBody() {
    return myErrorBalloonBody;
  }

  public String getErrorBalloonUrl() {
    return myErrorBalloonUrl;
  }

  public String getErrorBalloonUrlText() {
    return myErrorBalloonUrlText;
  }

  public void setNativeProfilingConfigurationPreferred(boolean nativeProfilingConfigurationPreferred) {
    myNativeProfilingConfigurationPreferred = nativeProfilingConfigurationPreferred;
  }

  public void setOnExecute(@Nullable Runnable onExecute) {
    myOnExecute = onExecute;
  }

  public void setPrePoolExecutor(@Nullable Runnable prePoolExecute) {
    myPrePoolExecute = prePoolExecute;
  }

  public void enableAtrace(boolean enabled) {
    myAtraceEnabled = enabled;
  }

  public void enableEnergyProfiler(boolean enabled) {
    myEnergyProfilerEnabled = enabled;
  }

  public void enableJvmtiAgent(boolean enabled) {
    myJvmtiAgentEnabled = enabled;
  }

  public void enableJniReferenceTracking(boolean enabled) { myIsJniReferenceTrackingEnabled = enabled; }

  public void enableLiveAllocationTracking(boolean enabled) {
    myLiveTrackingEnabled = enabled;
  }

  public void enableMemorySnapshot(boolean enabled) {
    myMemorySnapshotEnabled = enabled;
  }

  public void enableRequestPayload(boolean enabled) {
    myRequestPayloadEnabled = enabled;
  }

  public void enableSessionsView(boolean enabled) {
    mySessionsViewEnabled = enabled;
  }

  public void enableSimpleperf(boolean enabled) {
    mySimpleperfEnabled = enabled;
  }

  public void enableExportTrace(boolean enabled) {
    myExportCpuTraceEnabled = enabled;
  }
}
