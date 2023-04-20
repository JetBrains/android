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
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A collection of options governing Android run configuration launch behavior.
 */
public final class LaunchOptions {

  public static final class Builder {
    private boolean myDeploy = true;
    private boolean myOpenLogcatAutomatically = false;
    private final Map<String, Object> myExtraOptions = new HashMap<>();
    private boolean myDeployAsInstant = false;
    private boolean myClearAppStorage = false;

    private Builder() {
    }

    @NotNull
    public LaunchOptions build() {
      return new LaunchOptions(myDeploy,
                               myOpenLogcatAutomatically,
                               myExtraOptions,
                               myDeployAsInstant,
                               myClearAppStorage);
    }

    @NotNull
    public Builder setDeploy(boolean deploy) {
      myDeploy = deploy;
      return this;
    }

    @NotNull
    public Builder setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
      myOpenLogcatAutomatically = openLogcatAutomatically;
      return this;
    }


    @NotNull
    public Builder addExtraOptions(@NotNull Map<String, Object> extraOptions) {
      myExtraOptions.putAll(extraOptions);
      return this;
    }


    @NotNull
    public Builder setDeployAsInstant(boolean deployAsInstant) {
      myDeployAsInstant = deployAsInstant;
      return this;
    }

    @NotNull
    public Builder setClearAppStorage(boolean clearAppStorage) {
      myClearAppStorage = clearAppStorage;
      return this;
    }
  }

  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  private final boolean myDeploy;
  private final boolean myOpenLogcatAutomatically;
  private final Map<String, Object> myExtraOptions;
  private final boolean myDeployAsInstant;
  private final boolean myClearAppStorage;

  private LaunchOptions(boolean deploy,
                        boolean openLogcatAutomatically,
                        @NotNull Map<String, Object> extraOptions,
                        boolean deployAsInstant,
                        boolean clearAppStorage) {
    myDeploy = deploy;
    myOpenLogcatAutomatically = openLogcatAutomatically;
    myExtraOptions = ImmutableMap.copyOf(extraOptions);
    myDeployAsInstant = deployAsInstant;
    myClearAppStorage = clearAppStorage;
  }

  public boolean isDeploy() {
    return myDeploy;
  }

  public boolean isClearAppStorage() { return myClearAppStorage; }


  public boolean isOpenLogcatAutomatically() {
    return myOpenLogcatAutomatically;
  }

  @Nullable
  public Object getExtraOption(@NotNull String key) {
    return myExtraOptions.get(key);
  }

  public boolean isDeployAsInstant() { return myDeployAsInstant; }
}
