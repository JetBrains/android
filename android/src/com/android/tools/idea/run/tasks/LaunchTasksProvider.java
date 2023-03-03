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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.stats.RunStats;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LaunchTasksProvider {

  /**
   * An extension point to introduce build system dependent [AndroidRunConfigurationBase] based [LaunchTasksProvider] implementations like
   * support for running instrumented tests via Gradle.
   */
  interface Provider {
    ExtensionPointName<Provider> EP_NAME = ExtensionPointName.create("com.android.run.createLaunchTasksProvider");

    @Nullable LaunchTasksProvider createLaunchTasksProvider(@NotNull AndroidRunConfigurationBase runConfiguration,
                                                            @NotNull ExecutionEnvironment env,
                                                            @NotNull AndroidFacet facet,
                                                            @NotNull ApplicationIdProvider applicationIdProvider,
                                                            @NotNull ApkProvider apkProvider,
                                                            @NotNull LaunchOptions launchOptions);
  }

  @NotNull
  List<LaunchTask> getTasks(@NotNull IDevice device)
    throws ExecutionException;

  @Nullable
  ConnectDebuggerTask getConnectDebuggerTask() throws ExecutionException;

  default void fillStats(RunStats stats) { }
}
