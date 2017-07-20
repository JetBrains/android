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

import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
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
  private final CodeNavigator myFakeNavigationService = new CodeNavigator(myFakeFeatureTracker) {
    @Override
    protected void handleNavigate(@NotNull CodeLocation location) {
    }
  };

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
   * Toggle for faking jvmti agent support in tests.
   */
  private boolean isJvmtiAgentEnabled = false;

  /**
   * Can toggle for tests via {@link #enableSimplePerf(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean isSimplePerfEnabled = false;

  /**
   * Toggle for faking live allocation tracking support in tests.
   */
  private boolean isLiveTrackingEnabled = false;

  /**
   * Whether long trace files should be parsed.
   */
  private boolean myShouldParseLongTraces = false;

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
      public boolean isJvmtiAgentEnabled() {
        return isJvmtiAgentEnabled;
      }

      @Override
      public boolean isNetworkThreadViewEnabled() {
        return true;
      }

      @Override
      public boolean isSimplePerfEnabled() {
        return isSimplePerfEnabled;
      }

      @Override
      public boolean isLiveAllocationsEnabled() {
        return isLiveTrackingEnabled;
      }
    };
  }

  @Override
  public void openCpuProfilingConfigurationsDialog(ProfilingConfiguration configuration, boolean isDeviceAtLeastO,
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

  @Override
  public List<ProfilingConfiguration> getCpuProfilingConfigurations() {
    return new ArrayList<>();
  }

  public void setOnExecute(@Nullable Runnable onExecute) {
    myOnExecute = onExecute;
  }

  public void setPrePoolExecutor(@Nullable Runnable prePoolExecute) {
    myPrePoolExecute = prePoolExecute;
  }

  public void enableJvmtiAgent(boolean enabled) {
    isJvmtiAgentEnabled = enabled;
  }

  public void enableSimplePerf(boolean enabled) {
    isSimplePerfEnabled = enabled;
  }

  public void enableLiveAllocationTracking(boolean enabled) {
    isLiveTrackingEnabled = enabled;
  }
}
