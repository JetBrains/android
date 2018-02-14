/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A collection of options governing Android run configuration launch behavior.
 */
public final class LaunchOptions {

  public static final class Builder {
    private boolean myDeploy = true;
    private String myPmInstallOptions = null;
    private boolean myDebug = false;
    private boolean myOpenLogcatAutomatically = false;
    private boolean myClearLogcatBeforeStart = false;
    private boolean mySkipNoopApkInstallations = true;
    private boolean myForceStopRunningApp = true;
    private final Map<String, Object> myExtraOptions = new HashMap<>();

    private Builder() {
    }

    @NotNull
    public LaunchOptions build() {
      return new LaunchOptions(myDeploy,
                               myPmInstallOptions,
                               myDebug,
                               myOpenLogcatAutomatically,
                               myClearLogcatBeforeStart,
                               mySkipNoopApkInstallations,
                               myForceStopRunningApp,
                               myExtraOptions);
    }

    @NotNull
    public Builder setDeploy(boolean deploy) {
      myDeploy = deploy;
      return this;
    }

    @NotNull
    public Builder setPmInstallOptions(@Nullable String options) {
      myPmInstallOptions = options;
      return this;
    }

    @NotNull
    public Builder setDebug(boolean debug) {
      myDebug = debug;
      return this;
    }

    @NotNull
    public Builder setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
      myOpenLogcatAutomatically = openLogcatAutomatically;
      return this;
    }

    @NotNull
    public Builder setClearLogcatBeforeStart(boolean clearLogcatBeforeStart) {
      myClearLogcatBeforeStart = clearLogcatBeforeStart;
      return this;
    }

    @NotNull
    public Builder setSkipNoopApkInstallations(boolean skipNoopApkInstallations) {
      mySkipNoopApkInstallations = skipNoopApkInstallations;
      return this;
    }

    @NotNull
    public Builder setForceStopRunningApp(boolean forceStopRunningApp) {
      myForceStopRunningApp = forceStopRunningApp;
      return this;
    }

    @NotNull
    public Builder addExtraOptions(@NotNull Map<String, Object> extraOptions) {
      myExtraOptions.putAll(extraOptions);
      return this;
    }
  }

  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  private final boolean myDeploy;
  private final String myPmInstallOptions;
  private final boolean myDebug;
  private final boolean myOpenLogcatAutomatically;
  private final boolean myClearLogcatBeforeStart;
  private final boolean mySkipNoopApkInstallations;
  private final boolean myForceStopRunningApp;
  private final Map<String, Object> myExtraOptions;

  private LaunchOptions(boolean deploy,
                        @Nullable String pmInstallOptions,
                        boolean debug,
                        boolean openLogcatAutomatically,
                        boolean clearLogcatBeforeStart,
                        boolean skipNoopApkInstallations,
                        boolean forceStopRunningApp,
                        @NotNull Map<String, Object> extraOptions) {
    myDeploy = deploy;
    myPmInstallOptions = pmInstallOptions;
    myDebug = debug;
    myOpenLogcatAutomatically = openLogcatAutomatically;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
    mySkipNoopApkInstallations = skipNoopApkInstallations;
    myForceStopRunningApp = forceStopRunningApp;
    myExtraOptions = ImmutableMap.copyOf(extraOptions);
  }

  public boolean isDeploy() {
    return myDeploy;
  }

  @Nullable
  public String getPmInstallOptions() {
    return myPmInstallOptions;
  }

  public boolean isDebug() {
    return myDebug;
  }

  public boolean isOpenLogcatAutomatically() {
    return myOpenLogcatAutomatically;
  }

  public boolean isClearLogcatBeforeStart() {
    return myClearLogcatBeforeStart;
  }

  public boolean isSkipNoopApkInstallations() {
    return mySkipNoopApkInstallations;
  }

  public boolean isForceStopRunningApp() {
    return myForceStopRunningApp;
  }

  @Nullable
  public Object getExtraOption(@NotNull String key) {
    return myExtraOptions.get(key);
  }
}
