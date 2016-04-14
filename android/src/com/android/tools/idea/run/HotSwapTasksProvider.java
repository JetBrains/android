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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.Lists;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HotSwapTasksProvider implements LaunchTasksProvider {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;

  public HotSwapTasksProvider(@NotNull AndroidRunConfigurationBase runConfig,
                              @NotNull ExecutionEnvironment env,
                              @NotNull AndroidFacet facet,
                              @NotNull ApkProvider apkProvider,
                              @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myFacet = facet;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    List<LaunchTask> tasks = Lists.newArrayListWithCapacity(2);

    final HotSwapTask hotSwapTask = new HotSwapTask(myEnv, myFacet);
    tasks.add(hotSwapTask);

    LaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myApkProvider, myFacet, myLaunchOptions.isDebug(), launchStatus);
    if (appLaunchTask != null) {
      tasks.add(new PredicateLaunchTask(appLaunchTask, new PredicateLaunchTask.Predicate() {
        @Override
        public boolean isSuccess() {
          return hotSwapTask.needsActivityLaunch();
        }
      }));
    }

    return tasks;
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus) {
    return null;
  }

  @Override
  public boolean createsNewProcess() {
    return false;
  }
}
